package com.apex.scaffold.workflowservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.workflow.application.scheduler.WorkflowTimeoutHandler;
import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.domain.repository.ProcessInstanceRepository;
import com.apex.scaffold.workflow.domain.repository.TaskRepository;
import com.apex.scaffold.workflow.domain.repository.TimeoutTaskScan;
import com.apex.scaffold.workflow.domain.repository.WorkflowDefinitionRepository;
import com.apex.scaffold.workflow.infrastructure.persistence.repository.JdbcWorkflowTaskTransaction;
import com.apex.scaffold.workflow.infrastructure.persistence.repository.ProcessInstanceRepositoryImpl;
import com.apex.scaffold.workflow.infrastructure.persistence.repository.TaskRepositoryImpl;
import com.apex.scaffold.workflow.infrastructure.persistence.repository.WorkflowDefinitionRepositoryImpl;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.util.AopTestUtils;
import reactor.core.publisher.Flux;

/** 真实H2数据库上的独立扫描器竞争；不使用测试事务或时间等待制造竞争。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "security.config.encryption.key=01234567890123456789012345678901",
            "spring.datasource.url=jdbc:h2:mem:workflow-timeout-race-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "spring.cloud.nacos.discovery.register-enabled=false"
        })
@ActiveProfiles("basic")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkflowTimeoutRaceIntegrationTest {
    private final JdbcTemplate jdbc;
    private final WorkflowService service;
    private final TimeoutTaskScan scan;
    private final TaskRepository tasks;
    private final ProcessInstanceRepository instances;
    private final WorkflowDefinitionRepository definitions;

    WorkflowTimeoutRaceIntegrationTest(
            JdbcTemplate jdbc,
            WorkflowService service,
            TimeoutTaskScan scan,
            TaskRepository tasks,
            ProcessInstanceRepository instances,
            WorkflowDefinitionRepository definitions) {
        this.jdbc = jdbc;
        this.service = service;
        this.scan = scan;
        this.tasks = tasks;
        this.instances = instances;
        this.definitions = definitions;
    }

    @BeforeEach
    void seed() {
        removeFault();
        jdbc.update("DELETE FROM wf_task");
        jdbc.update("DELETE FROM wf_process_instance");
        jdbc.update("DELETE FROM wf_workflow_definition");
        jdbc.update(
                "INSERT INTO wf_workflow_definition "
                        + "(id,tenant_id,workflow_code,workflow_name,node_definition,version,enabled,status) "
                        + "VALUES (4001,'race-A','race_flow_a','竞争流程A',?,1,1,'PUBLISHED'),"
                        + "(4002,'race-B','race_flow_b','竞争流程B',?,1,1,'PUBLISHED')",
                singleChainDag(),
                singleChainDag());
        jdbc.update("INSERT INTO wf_process_instance "
                + "(id,tenant_id,workflow_definition_id,business_key,initiator_id,status,start_time) "
                + "VALUES (3001,'race-A',4001,'race-instance-a',100,'RUNNING',TIMESTAMP '2020-01-01 00:00:00'),"
                + "(3002,'race-B',4002,'race-instance-b',200,'RUNNING',TIMESTAMP '2020-01-01 00:00:00')");
        jdbc.update("INSERT INTO wf_task (id,tenant_id,process_instance_id,node_code,task_name,assignee_id,"
                + "timeout_at,timeout_strategy) VALUES (5001,'race-A',3001,'approve','审批',100,"
                + "TIMESTAMP '2020-01-01 00:00:00','AUTO_COMPLETE'),"
                + "(5002,'race-B',3002,'approve','审批',200,TIMESTAMP '2020-01-01 00:00:00','AUTO_COMPLETE')");
    }

    /** 首期可执行单链：开始 → 审批（发起人） → 结束。 */
    private static String singleChainDag() {
        return "{\"nodes\":["
                + "{\"nodeId\":\"start\",\"nodeType\":\"START\"},"
                + "{\"nodeId\":\"approve\",\"nodeType\":\"APPROVAL\",\"nodeName\":\"审批\","
                + "\"approverType\":\"INITIATOR\"},"
                + "{\"nodeId\":\"end\",\"nodeType\":\"END\"}],"
                + "\"edges\":["
                + "{\"edgeId\":\"e1\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"approve\"},"
                + "{\"edgeId\":\"e2\",\"sourceNodeId\":\"approve\",\"targetNodeId\":\"end\"}]}";
    }

    @AfterEach
    void removeFault() {
        jdbc.execute("ALTER TABLE wf_task DROP CONSTRAINT IF EXISTS timeout_race_fault");
    }

    @Test
    void twoIndependentHandlersCompleteEachTaskAtMostOnce() throws Exception {
        race("AUTO_COMPLETE");
    }

    @Test
    void twoIndependentHandlersEscalateEachSnapshotAtMostOnce() throws Exception {
        race("ESCALATE");
    }

    private void race(String strategy) throws Exception {
        jdbc.update("UPDATE wf_task SET timeout_strategy=?", strategy);
        // 两个扫描器各自从真实适配器读取；屏障确保它们的事务均读到相同版本再争抢写入。
        List<Task> first = snapshot();
        List<Task> second = snapshot();
        TaskRepositoryImpl real = AopTestUtils.getUltimateTargetObject(tasks);
        TaskRepositoryImpl controlled = spy(real);
        CyclicBarrier readBarrier = new CyclicBarrier(2);
        AtomicInteger winners = new AtomicInteger();
        doAnswer(invocation -> {
                    Task task = real.findByIdSync(invocation.getArgument(0), invocation.getArgument(1));
                    readBarrier.await(10, TimeUnit.SECONDS);
                    return task;
                })
                .when(controlled)
                .findByIdSync(anyString(), anyLong());
        // 统计的结果来自真实SQL，而不是模拟受影响行数。
        doAnswer(invocation -> {
                    int count = (int) invocation.callRealMethod();
                    winners.addAndGet(count);
                    return count;
                })
                .when(controlled)
                .updateTimeoutByIdSync(
                        anyString(),
                        org.mockito.ArgumentMatchers.any(Task.class),
                        org.mockito.ArgumentMatchers.any(Task.class));
        var transactions = new JdbcWorkflowTaskTransaction(
                jdbc.getDataSource(),
                controlled,
                AopTestUtils.<ProcessInstanceRepositoryImpl>getUltimateTargetObject(instances),
                AopTestUtils.<WorkflowDefinitionRepositoryImpl>getUltimateTargetObject(definitions),
                new com.apex.scaffold.workflow.infrastructure.persistence.repository.JdbcWorkflowEventOutbox(),
                false);
        WorkflowService competing = new WorkflowService(definitions, instances, controlled, transactions);
        var left = handler(first, competing).scanTimeoutTasks().materialize().toFuture();
        var right = handler(second, competing).scanTimeoutTasks().materialize().toFuture();
        var leftSignal = left.get(20, TimeUnit.SECONDS);
        var rightSignal = right.get(20, TimeUnit.SECONDS);
        assertThat(winners.get()).isEqualTo(2);
        assertThat(leftSignal.isOnComplete()).isTrue();
        assertThat(rightSignal.isOnComplete()).isTrue();
        assertThat(jdbc.queryForList("SELECT version FROM wf_task ORDER BY id", Integer.class))
                .containsExactly(1, 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_task", Integer.class))
                .isEqualTo(2);
        // 数据库版本增量也是提交成功次数的持久化证据，不把扫描器本身称作互斥。
        assertThat(jdbc.queryForList("SELECT status FROM wf_task ORDER BY id", String.class))
                .containsOnly("ESCALATE".equals(strategy) ? "PENDING" : "COMPLETED");
    }

    @Test
    void completedCandidateIsSkippedWithoutStarvingOtherTenant() {
        List<Task> old = snapshot();
        service.completeTask(5001L, 100L, "人工先办")
                .contextWrite(TenantContext.withTenant("race-A"))
                .block(Duration.ofSeconds(10));
        assertStaleSkipped(old);
    }

    @Test
    void transferredCandidateCannotUseNewAssignee() {
        List<Task> old = snapshot();
        service.transferTask(5001L, 300L, 100L, "人工转办")
                .contextWrite(TenantContext.withTenant("race-A"))
                .block(Duration.ofSeconds(10));
        assertStaleSkipped(old);
        assertThat(jdbc.queryForObject("SELECT assignee_id FROM wf_task WHERE id=5001", Long.class))
                .isEqualTo(300L);
    }

    @Test
    void changedDeadlineInvalidatesSnapshotEvenWithoutVersionIncrement() {
        List<Task> old = snapshot();
        jdbc.update("UPDATE wf_task SET timeout_at=TIMESTAMP '2090-01-01 00:00:00' WHERE id=5001");
        assertStaleSkipped(old);
    }

    @Test
    void changedStrategyInvalidatesSnapshotEvenWithoutVersionIncrement() {
        List<Task> old = snapshot();
        jdbc.update("UPDATE wf_task SET timeout_strategy='ESCALATE' WHERE id=5001");
        assertStaleSkipped(old);
    }

    @Test
    void legalDueTasksUsePersistedTenantDespiteUnrelatedCallerContext() {
        handler(snapshot(), service)
                .scanTimeoutTasks()
                .contextWrite(TenantContext.withTenant("unrelated"))
                .block(Duration.ofSeconds(10));
        assertThat(jdbc.queryForList("SELECT status FROM wf_task ORDER BY id", String.class))
                .containsOnly("COMPLETED");
        assertThat(jdbc.queryForList("SELECT version FROM wf_task ORDER BY id", Integer.class))
                .containsExactly(1, 1);
    }

    @Test
    void realDatabaseFailureRemainsObservableAndOtherTenantStillProgresses() {
        List<Task> old = snapshot();
        var before = jdbc.queryForMap("SELECT * FROM wf_task WHERE id=5001");
        jdbc.execute("ALTER TABLE wf_task ADD CONSTRAINT timeout_race_fault CHECK (id<>5001 OR status<>'COMPLETED')");
        var result = handler(old, service).scanTimeoutTasks().materialize().block(Duration.ofSeconds(10));
        assertThat(result).isNotNull();
        assertThat(result.isOnError()).isTrue();
        assertThat(result.getThrowable()).hasStackTraceContaining("TIMEOUT_RACE_FAULT");
        assertThat(jdbc.queryForMap("SELECT * FROM wf_task WHERE id=5001")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5002", String.class))
                .isEqualTo("COMPLETED");
    }

    private void assertStaleSkipped(List<Task> old) {
        var before = jdbc.queryForMap("SELECT * FROM wf_task WHERE id=5001");
        var result = handler(old, service).scanTimeoutTasks().materialize().block(Duration.ofSeconds(10));
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5002", String.class))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForMap("SELECT * FROM wf_task WHERE id=5001")).isEqualTo(before);
        assertThat(result).isNotNull();
        assertThat(result.isOnComplete()).isTrue();
    }

    private List<Task> snapshot() {
        return scan.findPendingTasks()
                .sort(java.util.Comparator.comparing(Task::getId))
                .collectList()
                .block(Duration.ofSeconds(10));
    }

    private WorkflowTimeoutHandler handler(List<Task> snapshot, WorkflowService workflow) {
        return new WorkflowTimeoutHandler(() -> Flux.fromIterable(snapshot), workflow, null, false, false, 300);
    }
}
