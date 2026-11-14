package com.apex.scaffold.workflowservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflow.domain.model.ProcessInstance;
import com.apex.scaffold.workflow.domain.repository.ProcessInstanceRepository;
import com.apex.scaffold.workflow.domain.repository.TaskRepository;
import com.apex.scaffold.workflow.domain.repository.WorkflowDefinitionRepository;
import com.apex.scaffold.workflow.infrastructure.persistence.repository.JdbcWorkflowTaskTransaction;
import com.apex.scaffold.workflow.infrastructure.persistence.repository.WorkflowDefinitionRepositoryImpl;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.util.AopTestUtils;
import reactor.core.publisher.Mono;

/**
 * 通过真实数据库后续写故障验证多步工作流写入必须整体回滚。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "security.config.encryption.key=01234567890123456789012345678901",
            "spring.datasource.url=jdbc:h2:mem:workflow-atomicity-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "spring.cloud.nacos.discovery.register-enabled=false"
        })
@ActiveProfiles("basic")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkflowAtomicityIntegrationTest {
    private final JdbcTemplate jdbc;
    private final WorkflowService service;
    private final WorkflowDefinitionRepository definitions;
    private final ProcessInstanceRepository instances;
    private final TaskRepository tasks;

    WorkflowAtomicityIntegrationTest(
            JdbcTemplate jdbc,
            WorkflowService service,
            WorkflowDefinitionRepository definitions,
            ProcessInstanceRepository instances,
            TaskRepository tasks) {
        this.jdbc = jdbc;
        this.service = service;
        this.definitions = definitions;
        this.instances = instances;
        this.tasks = tasks;
    }

    /** 各用例独立种子，不使用测试事务包裹业务调用。 */
    @BeforeEach
    void prepareData() {
        removeFaults();
        jdbc.update("DELETE FROM wf_approval_record");
        jdbc.update("DELETE FROM wf_task");
        jdbc.update("DELETE FROM wf_process_instance");
        jdbc.update("DELETE FROM wf_workflow_definition");
        jdbc.update("INSERT INTO wf_workflow_definition "
                + "(id,tenant_id,workflow_code,workflow_name,node_definition) "
                + "VALUES (1001,'1','leave_approval','请假审批','[]')");
        jdbc.update("INSERT INTO wf_process_instance "
                + "(id,tenant_id,workflow_definition_id,business_key,initiator_id) "
                + "VALUES (3001,'1',1001,'atomicity-leave',100)");
        jdbc.update("INSERT INTO wf_task "
                + "(id,tenant_id,process_instance_id,node_code,task_name,assignee_id,status,create_time,update_time) "
                + "VALUES (5001,'1',3001,'approve','经理审批',100,'PENDING',"
                + "TIMESTAMP '2026-01-01 00:00:00',TIMESTAMP '2026-01-01 00:00:00')");
    }

    /** 即使断言失败也移除故障约束，避免污染其他用例。 */
    @AfterEach
    void removeFaults() {
        jdbc.execute("ALTER TABLE wf_task DROP CONSTRAINT IF EXISTS atomicity_fault_task");
        jdbc.execute("ALTER TABLE wf_process_instance DROP CONSTRAINT IF EXISTS atomicity_fault_instance");
    }

    @Test
    void rejectRollsBackTaskWhenInstanceUpdateFails() {
        jdbc.execute("ALTER TABLE wf_process_instance ADD CONSTRAINT atomicity_fault_instance "
                + "CHECK (status <> 'REJECTED')");
        assertAtomicFailure("reject-constraint", service.rejectTask(5001L, 100L, "fault"), true);
    }

    @Test
    void rollbackRollsBackParentWhenChildInsertFails() {
        jdbc.execute("ALTER TABLE wf_task ADD CONSTRAINT atomicity_fault_task CHECK (node_code <> 'previous')");
        assertAtomicFailure("rollback-constraint", service.rollbackToNode(5001L, "previous", 100L, "fault"), true);
    }

    @Test
    void delegateRollsBackParentVersionWhenDelegatedInsertFails() {
        jdbc.execute("ALTER TABLE wf_task ADD CONSTRAINT atomicity_fault_task CHECK (status <> 'DELEGATED')");
        assertAtomicFailure("delegate-constraint", service.delegateTask(5001L, 200L, 100L, "fault"), true);
    }

    @Test
    void rejectErrorsAndRollsBackWhenFollowUpInstanceIsMissing() {
        jdbc.update("UPDATE wf_task SET process_instance_id=9999 WHERE id=5001");
        assertAtomicFailure("reject-missing-instance", service.rejectTask(5001L, 100L, "fault"), false);
    }

    @Test
    void rollbackErrorsAndRollsBackWhenFollowUpInstanceIsMissing() {
        jdbc.update("UPDATE wf_task SET process_instance_id=9999 WHERE id=5001");
        assertAtomicFailure(
                "rollback-missing-instance", service.rollbackToNode(5001L, "previous", 100L, "fault"), false);
    }

    @Test
    void rejectErrorsAndRollsBackWhenRealInstanceUpdateAffectsZeroRows() {
        var realInstances = AopTestUtils
                .<com.apex.scaffold.workflow.infrastructure.persistence.repository.ProcessInstanceRepositoryImpl>
                        getUltimateTargetObject(instances);
        var followUp = spy(realInstances);
        AtomicInteger affectedRows = new AtomicInteger(-1);
        // 仅改变后续更新的目标ID；先前任务更新及后续0行结果均由真实Mapper执行。
        doAnswer(invocation -> {
                    ProcessInstance instance = invocation.getArgument(1);
                    instance.setId(9999L);
                    int count = realInstances.updateByIdSync(invocation.getArgument(0), instance);
                    affectedRows.set(count);
                    return count;
                })
                .when(followUp)
                .updateByIdSync(org.mockito.ArgumentMatchers.eq("1"), any(ProcessInstance.class));
        var transactions = new JdbcWorkflowTaskTransaction(
                jdbc.getDataSource(),
                AopTestUtils.getUltimateTargetObject(tasks),
                followUp,
                AopTestUtils.<WorkflowDefinitionRepositoryImpl>getUltimateTargetObject(definitions),
                new com.apex.scaffold.workflow.infrastructure.persistence.repository.JdbcWorkflowEventOutbox(),
                false);
        WorkflowService controlled = new WorkflowService(definitions, instances, tasks, transactions);
        try {
            assertAtomicFailure("reject-zero-row", controlled.rejectTask(5001L, 100L, "fault"), false);
        } finally {
            assertThat(affectedRows.get()).as("真实Mapper确实返回0行").isZero();
        }
    }

    @Test
    void legalRejectCommitsTaskAndInstance() {
        service.rejectTask(5001L, 100L, "legal")
                .contextWrite(TenantContext.withTenant("1"))
                .block(Duration.ofSeconds(10));
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5001", String.class))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject("SELECT version FROM wf_task WHERE id=5001", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM wf_process_instance WHERE id=3001", String.class))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForObject(
                        "SELECT end_time FROM wf_process_instance WHERE id=3001", java.time.LocalDateTime.class))
                .isNotNull();
    }

    @Test
    void legalRollbackCommitsParentAndChild() {
        service.rollbackToNode(5001L, "previous", 100L, "legal")
                .contextWrite(TenantContext.withTenant("1"))
                .block(Duration.ofSeconds(10));
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5001", String.class))
                .isEqualTo("ROLLED_BACK");
        assertThat(jdbc.queryForObject("SELECT version FROM wf_task WHERE id=5001", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wf_task WHERE node_code='previous' AND status='PENDING' AND tenant_id='1'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void legalDelegateCommitsVersionAndChild() {
        service.delegateTask(5001L, 200L, 100L, "legal")
                .contextWrite(TenantContext.withTenant("1"))
                .block(Duration.ofSeconds(10));
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5001", String.class))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT version FROM wf_task WHERE id=5001", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wf_task WHERE status='DELEGATED' AND assignee_id=200 AND transferred_from=100 AND tenant_id='1'",
                        Integer.class))
                .isEqualTo(1);
    }

    /** 保存所有任务、实例及审批记录字段，覆盖状态、版本、时间及数量的回滚。 */
    private Map<String, List<Map<String, Object>>> snapshot() {
        return Map.of(
                "tasks", jdbc.queryForList("SELECT * FROM wf_task ORDER BY id"),
                "instances", jdbc.queryForList("SELECT * FROM wf_process_instance ORDER BY id"),
                "records", jdbc.queryForList("SELECT * FROM wf_approval_record ORDER BY id"));
    }

    /** 同时报告错误信号与所有数据库差异，不因首个失败遗漏残留证据。 */
    private void assertAtomicFailure(String scenario, Mono<Void> operation, boolean constraintFailure) {
        var before = snapshot();
        var signal = operation
                .contextWrite(TenantContext.withTenant("1"))
                .materialize()
                .block(Duration.ofSeconds(10));
        var after = snapshot();
        System.out.printf("ATOMICITY %s signal=%s before=%s after=%s%n", scenario, signal, before, after);
        assertAll(
                scenario,
                () -> {
                    assertThat(signal).isNotNull();
                    assertThat(signal.isOnError()).as("业务Mono必须报错").isTrue();
                    if (constraintFailure) {
                        assertThat(signal.getThrowable()).hasStackTraceContaining("ATOMICITY_FAULT");
                    }
                },
                () -> assertThat(after.get("tasks")).as("所有任务字段必须回滚").isEqualTo(before.get("tasks")),
                () -> assertThat(after.get("instances")).as("所有实例字段必须回滚").isEqualTo(before.get("instances")),
                () -> assertThat(after.get("records")).as("审批记录及数量必须不变").isEqualTo(before.get("records")));
    }
}
