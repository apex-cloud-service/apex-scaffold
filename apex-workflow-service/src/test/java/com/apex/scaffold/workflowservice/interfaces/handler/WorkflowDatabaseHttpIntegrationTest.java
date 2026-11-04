package com.apex.scaffold.workflowservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.workflow.application.query.WorkflowQueryService;
import com.apex.scaffold.workflow.infrastructure.persistence.repository.JdbcWorkflowTaskTransaction;
import com.apex.scaffold.workflow.infrastructure.persistence.repository.ProcessInstanceRepositoryImpl;
import com.apex.scaffold.workflow.infrastructure.persistence.repository.WorkflowDefinitionRepositoryImpl;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.test.StepVerifier;

/**
 * 工作流服务真实数据库 HTTP 集成测试。
 *
 * <p>承接原 example 工作流用例的业务意图，使用服务自身路由、身份中继过滤器、应用服务、MyBatis 仓储与 H2 数据库。
 * 使用真实 RSA 签名 ACCESS JWT 验证身份边界。当前服务未装配 License 门控，因此不沿用旧定义路由的 Basic License 403 假设。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "security.config.encryption.key=01234567890123456789012345678901",
            "spring.datasource.url=jdbc:h2:mem:workflow-http-it;MODE=MySQL;DB_CLOSE_DELAY=-1",
            "spring.cloud.nacos.config.enabled=false",
            "spring.cloud.nacos.discovery.enabled=false",
            "spring.cloud.nacos.discovery.register-enabled=false"
        })
@ActiveProfiles("basic")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class WorkflowDatabaseHttpIntegrationTest {

    private static final KeyPair RSA = Jwts.SIG.RS256.keyPair().build();

    private final WebTestClient client;
    private final JdbcTemplate jdbc;
    private final WorkflowQueryService queries;

    /** 为真实验签过滤器配置本测试生成的 RSA 公钥。 */
    @DynamicPropertySource
    static void configureJwtPublicKey(DynamicPropertyRegistry registry) {
        registry.add("workflow.security.jwt-public-key-base64", () -> Base64.getEncoder()
                .encodeToString(RSA.getPublic().getEncoded()));
    }

    /** 为指定测试用户签发短期有效的 ACCESS 凭证。 */
    private String accessToken(long userId) {
        return accessToken(userId, "1");
    }

    private String accessToken(long userId, String tenantId) {
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("userId", userId)
                .claim("tenantId", tenantId)
                .claim("tokenType", "ACCESS")
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(RSA.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 注入真实 HTTP 客户端与当前测试数据库访问器。
     *
     * @param client 连接随机端口的测试客户端
     * @param jdbc 工作流测试数据库访问器
     */
    WorkflowDatabaseHttpIntegrationTest(
            WebTestClient client,
            JdbcTemplate jdbc,
            WorkflowQueryService queries,
            com.apex.scaffold.workflow.domain.repository.TaskRepository tasks,
            com.apex.scaffold.workflow.domain.repository.WorkflowDefinitionRepository definitions,
            com.apex.scaffold.workflow.domain.repository.ProcessInstanceRepository instances,
            com.apex.scaffold.workflow.domain.repository.ApprovalRecordRepository records,
            com.apex.scaffold.workflow.domain.repository.TimeoutTaskScan timeoutScan,
            com.apex.scaffold.workflow.application.scheduler.WorkflowTimeoutHandler timeoutHandler,
            com.apex.scaffold.workflow.application.service.WorkflowService service) {
        this.client = client;
        this.jdbc = jdbc;
        this.queries = queries;
        this.tasks = tasks;
        this.definitions = definitions;
        this.instances = instances;
        this.records = records;
        this.timeoutScan = timeoutScan;
        this.timeoutHandler = timeoutHandler;
        this.service = service;
    }

    /** 清理独立测试库，并写入真实流程定义与不同办理人的待办种子。 */
    @BeforeEach
    void prepareWorkflowData() {
        jdbc.update("DELETE FROM wf_approval_record");
        jdbc.update("DELETE FROM wf_task");
        jdbc.update("DELETE FROM wf_process_instance");
        jdbc.update("DELETE FROM wf_workflow_definition");
        jdbc.update(
                "INSERT INTO wf_workflow_definition "
                        + "(id, tenant_id, workflow_code, workflow_name, node_definition) "
                        + "VALUES (1001, '1', 'leave_approval', '请假审批', ?)",
                validSingleChainDag());
        jdbc.update("INSERT INTO wf_process_instance "
                + "(id, tenant_id, workflow_definition_id, business_key, initiator_id) "
                + "VALUES (3001, '1', 1001, 'seed-leave', 100)");
        jdbc.update("INSERT INTO wf_task "
                + "(id, tenant_id, process_instance_id, node_code, task_name, assignee_id, status) "
                + "VALUES (5001, '1', 3001, 'approve', '经理审批', 100, 'PENDING'), "
                + "(5002, '1', 3001, 'approve', '其他人审批', 200, 'PENDING'), "
                + "(5003, '1', 3001, 'approve', '已完成审批', 100, 'COMPLETED'), "
                + "(5004, '2', 3001, 'approve', '其他租户审批', 100, 'PENDING')");
    }

    /** 首期可执行单链：开始 → 审批（发起人） → 结束。 */
    private static String validSingleChainDag() {
        return "{\"nodes\":["
                + "{\"nodeId\":\"start\",\"nodeType\":\"START\"},"
                + "{\"nodeId\":\"approve\",\"nodeType\":\"APPROVAL\",\"nodeName\":\"经理审批\","
                + "\"approverType\":\"INITIATOR\"},"
                + "{\"nodeId\":\"end\",\"nodeType\":\"END\"}],"
                + "\"edges\":["
                + "{\"edgeId\":\"e1\",\"sourceNodeId\":\"start\",\"targetNodeId\":\"approve\"},"
                + "{\"edgeId\":\"e2\",\"sourceNodeId\":\"approve\",\"targetNodeId\":\"end\"}]}";
    }

    @Test
    void isolatesQueriesAcrossTenants() {
        jdbc.update("INSERT INTO wf_workflow_definition "
                + "(id, tenant_id, workflow_code, workflow_name, node_definition) "
                + "VALUES (1002, '2', 'tenant_b_flow', '租户B流程', '{}')");
        jdbc.update("INSERT INTO wf_process_instance "
                + "(id, tenant_id, workflow_definition_id, business_key, initiator_id) "
                + "VALUES (3002, '2', 1002, 'tenant-b-instance', 100)");
        jdbc.update("INSERT INTO wf_task "
                + "(id, tenant_id, process_instance_id, node_code, task_name, assignee_id, status) "
                + "VALUES (5005, '2', 3002, 'approve', '租户B待办', 100, 'PENDING')");
        for (String tenant : new String[] {"1", "2"}) {
            long instanceId = tenant.equals("1") ? 3001 : 3002;
            long foreignInstanceId = tenant.equals("1") ? 3002 : 3001;
            long definitionId = tenant.equals("1") ? 1001 : 1002;
            long foreignDefinitionId = tenant.equals("1") ? 1002 : 1001;
            long taskId = tenant.equals("1") ? 5001 : 5005;
            long foreignTaskId = tenant.equals("1") ? 5005 : 5001;
            String code = tenant.equals("1") ? "leave_approval" : "tenant_b_flow";
            String foreignCode = tenant.equals("1") ? "tenant_b_flow" : "leave_approval";
            client.get()
                    .uri("/admin-api/v1/workflows/instances?pageNum=1&pageSize=1")
                    .headers(headers -> headers.setBearerAuth(accessToken(100, tenant)))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.data.total")
                    .isEqualTo(1)
                    .jsonPath("$.data.list.length()")
                    .isEqualTo(1)
                    .jsonPath("$.data.list[0].id")
                    .isEqualTo(instanceId);
            client.get()
                    .uri("/admin-api/v1/workflows/instances/" + instanceId)
                    .headers(headers -> headers.setBearerAuth(accessToken(100, tenant)))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.data.instance.id")
                    .isEqualTo(instanceId)
                    .jsonPath("$.data.definition.id")
                    .isEqualTo(definitionId)
                    .jsonPath("$.data.tasks.length()")
                    .isEqualTo(tenant.equals("1") ? 3 : 1);
            client.get()
                    .uri("/admin-api/v1/workflows/instances/" + foreignInstanceId)
                    .headers(headers -> headers.setBearerAuth(accessToken(100, tenant)))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .isEmpty();
            client.get()
                    .uri("/api/v1/workflows/tasks/pending?pageSize=1")
                    .headers(headers -> headers.setBearerAuth(accessToken(100, tenant)))
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.data.total")
                    .isEqualTo(tenant.equals("1") ? 1 : 2)
                    .jsonPath("$.data.list.length()")
                    .isEqualTo(1);
            StepVerifier.create(queries.getTaskById(taskId).contextWrite(TenantContext.withTenant(tenant)))
                    .assertNext(task -> assertThat(task.getId()).isEqualTo(taskId))
                    .verifyComplete();
            StepVerifier.create(queries.getTaskById(foreignTaskId).contextWrite(TenantContext.withTenant(tenant)))
                    .verifyComplete();
            StepVerifier.create(
                            queries.getDefinitionDetail(definitionId).contextWrite(TenantContext.withTenant(tenant)))
                    .assertNext(definition -> assertThat(definition.getCode()).isEqualTo(code))
                    .verifyComplete();
            StepVerifier.create(queries.getDefinitionDetail(foreignDefinitionId)
                            .contextWrite(TenantContext.withTenant(tenant)))
                    .verifyComplete();
            StepVerifier.create(queries.getDefinitionDetail(foreignCode).contextWrite(TenantContext.withTenant(tenant)))
                    .verifyComplete();
            StepVerifier.create(
                            queries.listInstances(1, 1, "RUNNING", 100L).contextWrite(TenantContext.withTenant(tenant)))
                    .assertNext(page -> assertThat(page.getTotal()).isEqualTo(1))
                    .verifyComplete();
        }
        jdbc.update("UPDATE wf_process_instance SET workflow_definition_id = 1002 WHERE id = 3001");
        StepVerifier.create(queries.getInstanceDetail(3001L).contextWrite(TenantContext.withTenant("1")))
                .assertNext(detail -> {
                    assertThat(detail.getDefinition()).isNull();
                    assertThat(detail.getInstance().getProcessCode()).isNull();
                    assertThat(detail.getTasks()).hasSize(3);
                })
                .verifyComplete();
    }

    private final com.apex.scaffold.workflow.domain.repository.TaskRepository tasks;
    private final com.apex.scaffold.workflow.domain.repository.WorkflowDefinitionRepository definitions;
    private final com.apex.scaffold.workflow.domain.repository.ProcessInstanceRepository instances;
    private final com.apex.scaffold.workflow.domain.repository.ApprovalRecordRepository records;

    @Test
    void stalePendingSnapshotsCannotOverwriteWinnerOrForeignTenant() {
        var first = tasks.findById(5001L)
                .contextWrite(TenantContext.withTenant("1"))
                .block();
        var stale = tasks.findById(5001L)
                .contextWrite(TenantContext.withTenant("1"))
                .block();
        first.setAssigneeId(300L);
        StepVerifier.create(tasks.updateById(first).contextWrite(TenantContext.withTenant("1")))
                .expectNext(1)
                .verifyComplete();
        stale.setStatus("COMPLETED");
        StepVerifier.create(tasks.updateById(stale).contextWrite(TenantContext.withTenant("1")))
                .expectNext(0)
                .verifyComplete();
        StepVerifier.create(tasks.updateById(stale).contextWrite(TenantContext.withTenant("2")))
                .expectNext(0)
                .verifyComplete();
        assertThat(jdbc.queryForObject("SELECT assignee_id FROM wf_task WHERE id=5001", Long.class))
                .isEqualTo(300L);
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5001", String.class))
                .isEqualTo("PENDING");
    }

    @Test
    void everyOperationRejectsRaceWithoutFollowUpWrites() {
        for (String operation : java.util.List.of("complete", "reject", "rollback", "transfer", "delegate")) {
            for (String winner : java.util.List.of("status", "assignee")) {
                jdbc.update("UPDATE wf_task SET status='PENDING', assignee_id=100, version=0 WHERE id=5001");
                com.apex.scaffold.workflow.domain.repository.TaskRepository racingTasks = org.mockito.Mockito.spy(org
                        .springframework.test.util.AopTestUtils
                        .<com.apex.scaffold.workflow.domain.repository.TaskRepository>getUltimateTargetObject(tasks));
                org.mockito.Mockito.doAnswer(invocation -> tasks.findById(5001L).map(snapshot -> {
                            if (winner.equals("status")) {
                                jdbc.update("UPDATE wf_task SET status='COMPLETED' WHERE id=5001");
                            } else {
                                jdbc.update("UPDATE wf_task SET assignee_id=300, version=version+1 WHERE id=5001");
                            }
                            return snapshot;
                        }))
                        .when(racingTasks)
                        .findById(5001L);
                var racingService = new com.apex.scaffold.workflow.application.service.WorkflowService(
                        definitions, instances, racingTasks, racingTransaction(winner));
                reactor.core.publisher.Mono<Void> result =
                        switch (operation) {
                            case "complete" -> racingService.completeTask(5001L, 100L, "race");
                            case "reject" -> racingService.rejectTask(5001L, 100L, "race");
                            case "rollback" -> racingService.rollbackToNode(5001L, "previous", 100L, "race");
                            case "transfer" -> racingService.transferTask(5001L, 200L, 100L, "race");
                            default -> racingService.delegateTask(5001L, 200L, 100L, "race");
                        };
                StepVerifier.create(result.contextWrite(TenantContext.withTenant("1")))
                        .expectError(IllegalStateException.class)
                        .verify();
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_task", Integer.class))
                        .isEqualTo(4);
                assertThat(jdbc.queryForObject("SELECT status FROM wf_process_instance WHERE id=3001", String.class))
                        .isEqualTo("RUNNING");
                assertThat(jdbc.queryForObject("SELECT assignee_id FROM wf_task WHERE id=5001", Long.class))
                        .isEqualTo(winner.equals("status") ? 100L : 300L);
                assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5001", String.class))
                        .isEqualTo(winner.equals("status") ? "COMPLETED" : "PENDING");
            }
        }
    }

    private com.apex.scaffold.workflow.domain.repository.WorkflowTaskTransaction racingTransaction(String winner) {
        var realTasks = org.springframework.test.util.AopTestUtils
                .<com.apex.scaffold.workflow.infrastructure.persistence.repository.TaskRepositoryImpl>
                        getUltimateTargetObject(tasks);
        var racing = org.mockito.Mockito.spy(realTasks);
        org.mockito.Mockito.doAnswer(invocation -> {
                    var snapshot = realTasks.findByIdSync("1", 5001L);
                    var independent = new org.springframework.transaction.support.TransactionTemplate(
                            new org.springframework.jdbc.support.JdbcTransactionManager(jdbc.getDataSource()));
                    independent.setPropagationBehavior(
                            org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
                    independent.executeWithoutResult(status -> {
                        if (winner.equals("status")) {
                            jdbc.update("UPDATE wf_task SET status='COMPLETED' WHERE id=5001");
                        } else {
                            jdbc.update("UPDATE wf_task SET assignee_id=300, version=version+1 WHERE id=5001");
                        }
                    });
                    return snapshot;
                })
                .when(racing)
                .findByIdSync("1", 5001L);
        return new JdbcWorkflowTaskTransaction(
                jdbc.getDataSource(),
                racing,
                AopTestUtils.<ProcessInstanceRepositoryImpl>getUltimateTargetObject(instances),
                AopTestUtils.<WorkflowDefinitionRepositoryImpl>getUltimateTargetObject(definitions),
                new com.apex.scaffold.workflow.infrastructure.persistence.repository.JdbcWorkflowEventOutbox(),
                false);
    }

    @Test
    void legalRollbackAndDelegationPreservePendingParentSemantics() {
        StepVerifier.create(service.rollbackToNode(5001L, "previous", 100L, "legal")
                        .contextWrite(TenantContext.withTenant("1")))
                .verifyComplete();
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5001", String.class))
                .isEqualTo("ROLLED_BACK");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wf_task WHERE node_code='previous' AND status='PENDING' AND tenant_id='1'",
                        Integer.class))
                .isEqualTo(1);
        StepVerifier.create(
                        service.delegateTask(5002L, 300L, 200L, "legal").contextWrite(TenantContext.withTenant("1")))
                .verifyComplete();
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5002", String.class))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT version FROM wf_task WHERE id=5002", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM wf_task WHERE status='DELEGATED' AND assignee_id=300 AND tenant_id='1'",
                        Integer.class))
                .isEqualTo(1);
    }

    @Test
    void timeoutCannotOverwriteTaskCompletedAfterScan() {
        jdbc.update(
                "UPDATE wf_task SET timeout_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP), timeout_strategy='AUTO_COMPLETE' WHERE id=5001");
        var scanned = timeoutScan.findPendingTasks().collectList().block();
        service.completeTask(5001L, 100L, "winner")
                .contextWrite(TenantContext.withTenant("1"))
                .block();
        var handler = new com.apex.scaffold.workflow.application.scheduler.WorkflowTimeoutHandler(
                () -> reactor.core.publisher.Flux.fromIterable(scanned), service, null, false, false, 300);
        StepVerifier.create(handler.scanTimeoutTasks()).verifyComplete();
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5001", String.class))
                .as("快照失效属于预期的竞争结果，超时处理应跳过而非覆盖已办结任务")
                .isEqualTo("COMPLETED");
    }

    @Test
    void repositoryReadsRequireReactorTenantAndIgnoreThreadLocal() {
        String previous = TenantContext.setTenantId("1");
        try {
            for (reactor.core.publisher.Mono<?> read : java.util.List.of(
                    tasks.findById(5001L),
                    definitions.findById(1001L),
                    instances.findById(3001L),
                    records.findById(6001L),
                    definitions.findByCode("leave_approval"),
                    tasks.findByAssignee(100L),
                    tasks.findByProcessInstanceId(3001L),
                    instances.findByInitiator(100L),
                    instances.findByBusinessKey("seed-leave"),
                    definitions.findAll(),
                    records.findByTaskId(5001L),
                    records.findByProcessInstanceId(3001L),
                    tasks.findPendingTasks().collectList())) {
                StepVerifier.create(read)
                        .expectError(IllegalStateException.class)
                        .verify();
                StepVerifier.create(read.contextWrite(context -> context.put(TenantContext.REACTOR_CONTEXT_KEY, " ")))
                        .expectError(IllegalStateException.class)
                        .verify();
            }
        } finally {
            TenantContext.restoreTenantId(previous);
        }
    }

    @Test
    void repositoryIdsAndAssociationsCannotReadOrMutateAnotherTenant() {
        jdbc.update(
                "INSERT INTO wf_approval_record (id,tenant_id,process_instance_id,task_id,action) VALUES (6001,'2',3001,5001,'APPROVE')");
        StepVerifier.create(tasks.findById(5004L).contextWrite(TenantContext.withTenant("1")))
                .verifyComplete();
        StepVerifier.create(records.findById(6001L).contextWrite(TenantContext.withTenant("1")))
                .verifyComplete();
        StepVerifier.create(tasks.findByProcessInstanceId(3001L).contextWrite(TenantContext.withTenant("1")))
                .assertNext(list -> assertThat(list)
                        .extracting(com.apex.scaffold.workflow.domain.model.Task::getId)
                        .containsExactlyInAnyOrder(5001L, 5002L, 5003L))
                .verifyComplete();
        StepVerifier.create(records.findByTaskId(5001L).contextWrite(TenantContext.withTenant("1")))
                .assertNext(list -> assertThat(list).isEmpty())
                .verifyComplete();
        var foreign = tasks.findById(5004L)
                .contextWrite(TenantContext.withTenant("2"))
                .block();
        foreign.setStatus("COMPLETED");
        StepVerifier.create(tasks.updateById(foreign).contextWrite(TenantContext.withTenant("1")))
                .expectNext(0)
                .verifyComplete();
        StepVerifier.create(tasks.deleteById(5004L).contextWrite(TenantContext.withTenant("1")))
                .expectNext(0)
                .verifyComplete();
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5004", String.class))
                .isEqualTo("PENDING");
        StepVerifier.create(definitions.findById(1001L).contextWrite(TenantContext.withTenant("2")))
                .verifyComplete();
        StepVerifier.create(instances.findById(3001L).contextWrite(TenantContext.withTenant("2")))
                .verifyComplete();
        StepVerifier.create(definitions.findByCode("leave_approval").contextWrite(TenantContext.withTenant("2")))
                .verifyComplete();
        var definition = definitions
                .findById(1001L)
                .contextWrite(TenantContext.withTenant("1"))
                .block();
        definition.setWorkflowName("forged");
        StepVerifier.create(definitions.updateById(definition).contextWrite(TenantContext.withTenant("2")))
                .expectNext(0)
                .verifyComplete();
        StepVerifier.create(definitions.deleteById(1001L).contextWrite(TenantContext.withTenant("2")))
                .expectNext(0)
                .verifyComplete();
        var instance = instances
                .findById(3001L)
                .contextWrite(TenantContext.withTenant("1"))
                .block();
        instance.setStatus("COMPLETED");
        StepVerifier.create(instances.updateById(instance).contextWrite(TenantContext.withTenant("2")))
                .expectNext(0)
                .verifyComplete();
        StepVerifier.create(instances.deleteById(3001L).contextWrite(TenantContext.withTenant("2")))
                .expectNext(0)
                .verifyComplete();
        var record = records.findById(6001L)
                .contextWrite(TenantContext.withTenant("2"))
                .block();
        record.setAction("REJECT");
        StepVerifier.create(records.updateById(record).contextWrite(TenantContext.withTenant("1")))
                .expectNext(0)
                .verifyComplete();
        StepVerifier.create(records.deleteById(6001L).contextWrite(TenantContext.withTenant("1")))
                .expectNext(0)
                .verifyComplete();
    }

    @Test
    void repositoryInsertsAndReadsSupportTrustedNonNumericTenant() {
        var task = new com.apex.scaffold.workflow.domain.model.Task();
        task.setId(5010L);
        task.setProcessInstanceId(3001L);
        task.setNodeCode("approve");
        task.setNodeName("审批");
        task.setAssigneeId(100L);
        task.setStatus("PENDING");
        task.setTenantId("forged-tenant");
        StepVerifier.create(tasks.insert(task).contextWrite(TenantContext.withTenant("tenant-A")))
                .expectNext(1)
                .verifyComplete();
        assertThat(jdbc.queryForObject("SELECT tenant_id FROM wf_task WHERE id=5010", String.class))
                .isEqualTo("tenant-A");
        StepVerifier.create(tasks.findById(5010L).contextWrite(TenantContext.withTenant("tenant-A")))
                .assertNext(saved -> assertThat(saved.getAssigneeId()).isEqualTo(100L))
                .verifyComplete();
        StepVerifier.create(tasks.findById(5010L).contextWrite(TenantContext.withTenant("tenant-B")))
                .verifyComplete();
        task.setStatus("COMPLETED");
        task.setTenantId("forged-update");
        task.setVersion(0);
        StepVerifier.create(tasks.updateById(task).contextWrite(TenantContext.withTenant("tenant-A")))
                .expectNext(1)
                .verifyComplete();
    }

    private final com.apex.scaffold.workflow.domain.repository.TimeoutTaskScan timeoutScan;
    private final com.apex.scaffold.workflow.application.scheduler.WorkflowTimeoutHandler timeoutHandler;
    private final com.apex.scaffold.workflow.application.service.WorkflowService service;

    @Test
    void timeoutScanOnlyReturnsExpiredPendingTasksAndProcessesPersistedTenants() {
        jdbc.update(
                "UPDATE wf_task SET tenant_id='tenant-A', timeout_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP), timeout_strategy='AUTO_COMPLETE' WHERE id=5001");
        jdbc.update(
                "UPDATE wf_task SET tenant_id='tenant-B', timeout_at=DATEADD('HOUR',-1,CURRENT_TIMESTAMP), timeout_strategy='AUTO_COMPLETE' WHERE id=5004");
        jdbc.update("UPDATE wf_task SET timeout_at=DATEADD('HOUR',1,CURRENT_TIMESTAMP) WHERE id=5002");
        StepVerifier.create(timeoutScan.findPendingTasks().collectList())
                .assertNext(list -> assertThat(list)
                        .extracting(com.apex.scaffold.workflow.domain.model.Task::getId)
                        .containsExactlyInAnyOrder(5001L, 5004L))
                .verifyComplete();
        StepVerifier.create(timeoutHandler.scanTimeoutTasks().contextWrite(TenantContext.withTenant("unrelated")))
                .verifyComplete();
        assertThat(jdbc.queryForList("SELECT status FROM wf_task WHERE id IN (5001,5004)", String.class))
                .containsOnly("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5002", String.class))
                .isEqualTo("PENDING");
    }

    @Test
    void sameAssigneeCannotOperateForeignTenantButCanCompleteOwnTask() {
        StepVerifier.create(service.completeTask(5004L, 100L, "test").contextWrite(TenantContext.withTenant("1")))
                .expectError(IllegalStateException.class)
                .verify();
        StepVerifier.create(service.transferTask(5004L, 200L, 100L, "test").contextWrite(TenantContext.withTenant("1")))
                .expectError(IllegalStateException.class)
                .verify();
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5004", String.class))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT assignee_id FROM wf_task WHERE id=5004", Long.class))
                .isEqualTo(100L);
        StepVerifier.create(service.completeTask(5001L, 100L, "test").contextWrite(TenantContext.withTenant("1")))
                .verifyComplete();
        assertThat(jdbc.queryForObject("SELECT status FROM wf_task WHERE id=5001", String.class))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                        "SELECT complete_time FROM wf_task WHERE id=5001", java.time.LocalDateTime.class))
                .isNotNull();
    }

    @Test
    void missingTenantPreventsEveryRepositoryWriteEvenWithThreadLocal() {
        var task = new com.apex.scaffold.workflow.domain.model.Task();
        task.setId(5001L);
        var definition = new com.apex.scaffold.workflow.domain.model.WorkflowDefinition();
        definition.setId(1001L);
        var instance = new com.apex.scaffold.workflow.domain.model.ProcessInstance();
        instance.setId(3001L);
        var record = new com.apex.scaffold.workflow.domain.model.ApprovalRecord();
        record.setId(6001L);
        String previous = TenantContext.setTenantId("1");
        try {
            for (reactor.core.publisher.Mono<?> write : java.util.List.of(
                    tasks.insert(task),
                    tasks.updateById(task),
                    tasks.deleteById(5001L),
                    definitions.insert(definition),
                    definitions.updateById(definition),
                    definitions.deleteById(1001L),
                    instances.insert(instance),
                    instances.updateById(instance),
                    instances.deleteById(3001L),
                    records.insert(record),
                    records.updateById(record),
                    records.deleteById(6001L))) {
                StepVerifier.create(write)
                        .expectError(IllegalStateException.class)
                        .verify();
                StepVerifier.create(write.contextWrite(context -> context.put(TenantContext.REACTOR_CONTEXT_KEY, " ")))
                        .expectError(IllegalStateException.class)
                        .verify();
            }
        } finally {
            TenantContext.restoreTenantId(previous);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_task WHERE deleted=0", Integer.class))
                .isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_workflow_definition WHERE deleted=0", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_process_instance WHERE deleted=0", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_approval_record", Integer.class))
                .isZero();
    }

    /** 验证发起请求使用可信发起人，并将业务数据真实持久化。 */
    @Test
    void startsProcessWithTrustedIdentity() {
        client.post()
                .uri("/api/v1/workflows/leave_approval/instances?userId=200")
                .headers(headers -> headers.setBearerAuth(accessToken(100)))
                .header("X-User-Id", "200")
                .header("X-Tenant-Id", "2")
                .bodyValue(Map.of("businessKey", "http-leave", "contextJson", "{\"days\":3}", "initiatorId", 200))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.businessKey")
                .isEqualTo("http-leave")
                .jsonPath("$.data.initiatorId")
                .isEqualTo(100)
                .jsonPath("$.data.status")
                .isEqualTo("RUNNING");

        assertThat(jdbc.queryForObject(
                        "SELECT initiator_id FROM wf_process_instance WHERE business_key = 'http-leave'", Long.class))
                .isEqualTo(100L);
        assertThat(jdbc.queryForObject(
                        "SELECT tenant_id FROM wf_process_instance WHERE business_key = 'http-leave'", String.class))
                .isEqualTo("1");
        assertThat(jdbc.queryForObject(
                        "SELECT form_data FROM wf_process_instance WHERE business_key = 'http-leave'", String.class))
                .isEqualTo("{\"days\":3}");
    }

    /** 验证分页查询只返回当前认证用户的待办，不接受查询参数覆盖身份。 */
    @Test
    void pendingTasksUseAuthenticatedAssignee() {
        client.get()
                .uri("/api/v1/workflows/tasks/pending?userId=200&pageNum=1&pageSize=10")
                .headers(headers -> headers.setBearerAuth(accessToken(100)))
                .header("X-User-Id", "200")
                .header("X-Tenant-Id", "2")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.total")
                .isEqualTo(1)
                .jsonPath("$.data.list.length()")
                .isEqualTo(1)
                .jsonPath("$.data.list[0].id")
                .isEqualTo(5001)
                .jsonPath("$.data.list[0].assigneeId")
                .isEqualTo(100)
                .jsonPath("$.data.list[0].status")
                .isEqualTo("PENDING");
        client.get()
                .uri("/api/v1/workflows/tasks/pending")
                .headers(headers -> headers.setBearerAuth(accessToken(300)))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.total")
                .isEqualTo(0)
                .jsonPath("$.data.list")
                .isEmpty();
    }

    /** 验证缺失和非法身份无法访问真实服务路由，也不会产生数据库写入。 */
    @Test
    void rejectsUntrustedIdentityWithoutWriting() {
        client.get()
                .uri("/api/v1/workflows/tasks/pending?userId=100")
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.post()
                .uri("/api/v1/workflows/leave_approval/instances")
                .header("X-User-Id", "100")
                .header("X-Tenant-Id", "1")
                .bodyValue(Map.of("businessKey", "forged-header-leave"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        client.post()
                .uri("/api/v1/workflows/leave_approval/instances")
                .header("X-User-Id", "invalid")
                .bodyValue(Map.of("businessKey", "unauthorized-leave"))
                .exchange()
                .expectStatus()
                .isUnauthorized();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM wf_process_instance", Integer.class))
                .isEqualTo(1);
    }
}
