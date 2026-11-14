package com.apex.scaffold.workflowservice.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.contract.event.TaskAssignedEvent;
import com.apex.scaffold.contract.event.TaskCompletedEvent;
import com.apex.scaffold.workflow.application.dto.WorkflowInstanceDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowInstanceDetailDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowStatisticsDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowTaskDTO;
import com.apex.scaffold.workflow.application.dto.WorkflowTrendDTO;
import com.apex.scaffold.workflow.application.query.WorkflowQueryService;
import com.apex.scaffold.workflow.application.query.WorkflowStatisticsService;
import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflow.domain.model.ProcessInstance;
import com.apex.scaffold.workflowservice.WorkflowServiceSliceTestConfiguration;
import com.apex.scaffold.workflowservice.interfaces.dto.workflow.CompleteTaskRequest;
import com.apex.scaffold.workflowservice.interfaces.dto.workflow.DelegateTaskRequest;
import com.apex.scaffold.workflowservice.interfaces.dto.workflow.RollbackTaskRequest;
import com.apex.scaffold.workflowservice.interfaces.dto.workflow.TransferTaskRequest;
import com.apex.scaffold.workflowservice.interfaces.router.WorkflowRouter;
import com.apex.scaffold.workflowservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.workflowservice.interfaces.util.RequestHandlerUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 工作流处理器审批流转集成测试。
 *
 * <p>在 {@link WebFluxTest} 切片上下文中加载 {@link WorkflowHandler} 与
 * {@link WorkflowRouter}，通过 {@code WebTestClient} 以真实 HTTP 语义驱动
 * 全部 12 个函数式端点，覆盖迁移方案 §3.6「审批流转集成测试」要求，
 * 验证「发起 → 待办 → 审批 → 流转 → 挂起/恢复」的完整审批链路，
 * 以及查询、统计与异常分支的行为。
 *
 * <h2>覆盖范围</h2>
 * <ul>
 *   <li>发起流程实例：POST /api/v1/workflows/{'{'}workflowCode{'}'}/instances</li>
 *   <li>待办任务查询：GET /api/v1/workflows/tasks/pending</li>
 *   <li>完成任务审批：POST /api/v1/workflows/tasks/{'{'}taskId{'}'}/complete</li>
 *   <li>实例列表/详情：GET /admin-api/v1/workflows/instances[/{'{'}instanceId{'}'}]</li>
 *   <li>统计与趋势：GET /admin-api/v1/workflows/statistics[/trend]</li>
 *   <li>流程回退/转办/委派：POST /api/workflow/tasks/{'{'}taskId{'}'}/rollback|transfer|delegate</li>
 *   <li>流程挂起/恢复：POST /api/workflow/instances/{'{'}instanceId{'}'}/suspend|resume</li>
 * </ul>
 *
 * <p>说明：工作流服务未注册全局异常处理器，业务异常在函数式端点中会以
 * HTTP 5xx 形式透出（而非认证中心「HTTP 200 + code=ERROR」的包装风格）；
 * 统计与趋势接口自带 {@code onErrorResume} 降级，故其异常分支仍返回
 * HTTP 200 且携带空数据兜底。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@WebFluxTest
@Import({WorkflowHandler.class, WorkflowRouter.class, WorkflowServiceSliceTestConfiguration.class})
@DisplayName("工作流Handler审批流转集成测试")
class WorkflowHandlerIntegrationTest {

    /** 认证用户 ID，与测试桩中的发起人一致。 */
    private static final long USER_ID = 100L;

    /**
     * {@code WebTestClient} 在 {@code @WebFluxTest} 中无法通过构造器解析，
     * 故采用 JSR-250 {@code @Resource} 字段注入。
     */
    @Resource
    private WebTestClient webTestClient;

    /** 已注入认证上下文与 CSRF 令牌的客户端，模拟网关透传的可信用户。 */
    private WebTestClient client;

    @MockBean
    private WorkflowService workflowService;

    @MockBean
    private WorkflowQueryService workflowQueryService;

    @MockBean
    private WorkflowStatisticsService workflowStatisticsService;

    @MockBean
    private RequestHandlerUtil requestHandlerUtil;

    private ProcessInstance testInstance;
    private WorkflowTaskDTO testTaskDto;
    private WorkflowInstanceDTO testInstanceDto;
    private WorkflowInstanceDetailDTO testDetailDto;

    @BeforeEach
    void setUp() {
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(USER_ID, "openid-1", "default");
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(authenticatedUser, "", List.of());
        client = webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(authentication));

        when(requestHandlerUtil.validateAndThrow(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(workflowQueryService.getTaskById(anyLong())).thenReturn(Mono.empty());

        testInstance = new ProcessInstance();
        testInstance.setId(3001L);
        testInstance.setWorkflowDefinitionId(1001L);
        testInstance.setBusinessKey("LEAVE-001");
        testInstance.setCurrentNodeKey("node_1");
        testInstance.setStatus("RUNNING");
        testInstance.setInitiatorId(USER_ID);
        testInstance.setStartTime(LocalDateTime.now());

        testTaskDto = WorkflowTaskDTO.builder()
                .id(5001L)
                .instanceId(3001L)
                .taskName("部门经理审批")
                .taskType("USER_TASK")
                .status("PENDING")
                .assigneeId(USER_ID)
                .assigneeName("张三")
                .nodeId("node_1")
                .createTime(LocalDateTime.now())
                .build();

        testInstanceDto = WorkflowInstanceDTO.builder()
                .id(3001L)
                .definitionId(1001L)
                .processCode("leave_approval")
                .title("张三的请假申请")
                .status("RUNNING")
                .suspended(false)
                .initiatorId(USER_ID)
                .initiatorName("张三")
                .businessKey("LEAVE-001")
                .currentNodeId("node_1")
                .startTime(LocalDateTime.now())
                .build();

        testDetailDto = WorkflowInstanceDetailDTO.builder()
                .instance(testInstanceDto)
                .tasks(List.of(testTaskDto))
                .currentNodeId("node_1")
                .build();
    }

    @Test
    @DisplayName("发起流程实例 - 成功并透传参数")
    void startProcessInstance_Success() {
        when(workflowService.startProcessInstance(anyString(), any(), anyLong(), any(), any()))
                .thenReturn(Mono.just(testInstance));

        client.post()
                .uri("/api/v1/workflows/leave_approval/instances")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("businessKey", "LEAVE-001", "contextJson", "{\"days\":3}"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.id")
                .isEqualTo(3001)
                .jsonPath("$.data.businessKey")
                .isEqualTo("LEAVE-001")
                .jsonPath("$.data.status")
                .isEqualTo("RUNNING");

        verify(workflowService)
                .startProcessInstance(eq("leave_approval"), eq("LEAVE-001"), eq(USER_ID), eq("{\"days\":3}"), any());
    }

    @Test
    @DisplayName("查询当前用户待办任务 - 成功")
    void listPendingTasks_Success() {
        when(workflowQueryService.listMyTasks(USER_ID, 1, 10))
                .thenReturn(Mono.just(PageResponse.of(List.of(testTaskDto), 1L, 1, 10)));

        client.get()
                .uri("/api/v1/workflows/tasks/pending")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.total")
                .isEqualTo(1)
                .jsonPath("$.data.list[0].id")
                .isEqualTo(5001)
                .jsonPath("$.data.list[0].status")
                .isEqualTo("PENDING");

        verify(workflowQueryService).listMyTasks(USER_ID, 1, 10);
    }

    @Test
    @DisplayName("查询当前用户待办任务 - 自定义分页参数")
    void listPendingTasks_CustomPaging() {
        when(workflowQueryService.listMyTasks(USER_ID, 2, 5))
                .thenReturn(Mono.just(PageResponse.of(List.of(), 0L, 2, 5)));

        client.get()
                .uri("/api/v1/workflows/tasks/pending?pageNum=2&pageSize=5")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.list")
                .isArray();

        verify(workflowQueryService).listMyTasks(USER_ID, 2, 5);
    }

    @Test
    @DisplayName("完成任务审批 - 成功并透传审批人")
    void completeTask_Success() {
        when(workflowService.completeTask(anyLong(), anyLong(), any(), any())).thenReturn(Mono.empty());

        client.post()
                .uri("/api/v1/workflows/tasks/5001/complete")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("comment", "同意"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);

        verify(workflowService).completeTask(eq(5001L), eq(USER_ID), eq("同意"), any());
    }

    @Test
    @DisplayName("查询流程实例详情 - 成功")
    void getInstanceDetail_Success() {
        when(workflowQueryService.getInstanceDetail(3001L)).thenReturn(Mono.just(testDetailDto));

        client.get()
                .uri("/admin-api/v1/workflows/instances/3001")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.currentNodeId")
                .isEqualTo("node_1")
                .jsonPath("$.data.instance.id")
                .isEqualTo(3001);
    }

    @Test
    @DisplayName("查询工作流实例列表 - 成功")
    void listInstances_Success() {
        when(workflowQueryService.listInstances(1, 10, null, null))
                .thenReturn(Mono.just(PageResponse.of(List.of(testInstanceDto), 1L, 1, 10)));

        client.get()
                .uri("/admin-api/v1/workflows/instances")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.total")
                .isEqualTo(1)
                .jsonPath("$.data.list[0].id")
                .isEqualTo(3001)
                .jsonPath("$.data.list[0].processCode")
                .isEqualTo("leave_approval");
    }

    @Test
    @DisplayName("查询工作流实例列表 - 按状态过滤")
    void listInstances_WithStatusFilter() {
        when(workflowQueryService.listInstances(1, 10, "RUNNING", null))
                .thenReturn(Mono.just(PageResponse.of(List.of(testInstanceDto), 1L, 1, 10)));

        client.get()
                .uri("/admin-api/v1/workflows/instances?status=RUNNING")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.list[0].status")
                .isEqualTo("RUNNING");

        verify(workflowQueryService).listInstances(1, 10, "RUNNING", null);
    }

    @Test
    @DisplayName("获取工作流统计数据 - 成功")
    void getStatistics_Success() {
        WorkflowStatisticsDTO statistics = WorkflowStatisticsDTO.builder()
                .todayStarted(25)
                .runningTotal(120)
                .pendingTaskTotal(86)
                .suspendedCount(3)
                .timeoutCount(5)
                .transferredCount(12)
                .avgCompletionSeconds(86400L)
                .statusDistribution(Map.of("RUNNING", 120))
                .build();
        when(workflowStatisticsService.getStatistics()).thenReturn(Mono.just(statistics));

        client.get()
                .uri("/admin-api/v1/workflows/statistics")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.todayStarted")
                .isEqualTo(25)
                .jsonPath("$.data.runningTotal")
                .isEqualTo(120)
                .jsonPath("$.data.pendingTaskTotal")
                .isEqualTo(86);
    }

    @Test
    @DisplayName("获取工作流趋势数据 - 成功")
    void getTrend_Success() {
        WorkflowTrendDTO trend = WorkflowTrendDTO.builder()
                .dates(List.of("2026-09-01", "2026-09-02"))
                .startedCounts(List.of(120, 135))
                .completedCounts(List.of(115, 140))
                .build();
        when(workflowStatisticsService.getTrend(7)).thenReturn(Mono.just(trend));

        client.get()
                .uri("/admin-api/v1/workflows/statistics/trend")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.dates[0]")
                .isEqualTo("2026-09-01")
                .jsonPath("$.data.startedCounts[1]")
                .isEqualTo(135);
    }

    @Test
    @DisplayName("流程回退 - 成功")
    void rollbackTask_Success() {
        when(workflowService.rollbackToNode(anyLong(), anyString(), anyLong(), any(), any()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/api/workflow/tasks/5001/rollback")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("targetNodeCode", "dept_leader_approve", "reason", "材料不完整"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);

        verify(workflowService).rollbackToNode(eq(5001L), eq("dept_leader_approve"), eq(USER_ID), eq("材料不完整"), any());
    }

    @Test
    @DisplayName("任务转办 - 成功")
    void transferTask_Success() {
        when(workflowService.transferTask(anyLong(), anyLong(), anyLong(), any(), any()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/api/workflow/tasks/5001/transfer")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("newAssigneeId", 200, "reason", "原审批人调岗"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);

        verify(workflowService).transferTask(eq(5001L), eq(200L), eq(USER_ID), eq("原审批人调岗"), any());
    }

    @Test
    @DisplayName("任务委派 - 成功")
    void delegateTask_Success() {
        when(workflowService.delegateTask(anyLong(), anyLong(), anyLong(), any(), any()))
                .thenReturn(Mono.empty());

        client.post()
                .uri("/api/workflow/tasks/5001/delegate")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("delegateToId", 300, "reason", "本人出差"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);

        verify(workflowService).delegateTask(eq(5001L), eq(300L), eq(USER_ID), eq("本人出差"), any());
    }

    @Test
    @DisplayName("挂起流程实例 - 成功")
    void suspendInstance_Success() {
        when(workflowService.suspendInstance(anyLong(), anyLong(), any())).thenReturn(Mono.empty());

        client.post()
                .uri("/api/workflow/instances/3001/suspend")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("reason", "等待资料补充"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);

        verify(workflowService).suspendInstance(3001L, USER_ID, "等待资料补充");
    }

    @Test
    @DisplayName("恢复流程实例 - 成功")
    void resumeInstance_Success() {
        when(workflowService.resumeInstance(anyLong(), anyLong(), any())).thenReturn(Mono.empty());

        client.post()
                .uri("/api/workflow/instances/3001/resume")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("reason", "资料已补齐"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);

        verify(workflowService).resumeInstance(3001L, USER_ID, "资料已补齐");
    }

    @Test
    @DisplayName("未认证访问受保护接口 - 返回 401")
    void protectedEndpoint_Unauthenticated() {
        webTestClient
                .get()
                .uri("/api/v1/workflows/tasks/pending")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isUnauthorized();
    }

    @Test
    @DisplayName("认证主体非网关用户 - 解析用户失败返回 5xx")
    void getCurrentUserId_InvalidPrincipal() {
        UsernamePasswordAuthenticationToken plainAuthentication =
                new UsernamePasswordAuthenticationToken("anonymous", "", List.of());

        webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(plainAuthentication))
                .get()
                .uri("/api/v1/workflows/tasks/pending")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    @DisplayName("发起流程实例 - 服务异常返回 5xx")
    void startProcessInstance_ServiceError() {
        when(workflowService.startProcessInstance(anyString(), any(), anyLong(), any()))
                .thenReturn(Mono.error(new IllegalStateException("service unavailable")));

        client.post()
                .uri("/api/v1/workflows/leave_approval/instances")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("businessKey", "LEAVE-001"))
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    @DisplayName("查询待办任务 - 服务异常返回 5xx")
    void listPendingTasks_ServiceError() {
        when(workflowQueryService.listMyTasks(anyLong(), anyInt(), anyInt()))
                .thenReturn(Mono.error(new IllegalStateException("service unavailable")));

        client.get()
                .uri("/api/v1/workflows/tasks/pending")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    @DisplayName("查询实例详情 - 服务异常返回 5xx")
    void getInstanceDetail_ServiceError() {
        when(workflowQueryService.getInstanceDetail(anyLong()))
                .thenReturn(Mono.error(new IllegalStateException("service unavailable")));

        client.get()
                .uri("/admin-api/v1/workflows/instances/3001")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    @DisplayName("完成任务审批 - 服务异常返回 5xx")
    void completeTask_ServiceError() {
        when(workflowService.completeTask(anyLong(), anyLong(), any()))
                .thenReturn(Mono.error(new IllegalStateException("service unavailable")));

        client.post()
                .uri("/api/v1/workflows/tasks/5001/complete")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("comment", "同意"))
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    @DisplayName("流程回退 - 服务异常返回 5xx")
    void rollbackTask_ServiceError() {
        when(workflowService.rollbackToNode(anyLong(), anyString(), anyLong(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("service unavailable")));

        client.post()
                .uri("/api/workflow/tasks/5001/rollback")
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("targetNodeCode", "dept_leader_approve", "reason", "材料不完整"))
                .exchange()
                .expectStatus()
                .is5xxServerError();
    }

    @Test
    @DisplayName("获取统计数据 - 服务异常降级为空统计")
    void getStatistics_ServiceError_ReturnsFallback() {
        when(workflowStatisticsService.getStatistics())
                .thenReturn(Mono.error(new IllegalStateException("statistics down")));

        client.get()
                .uri("/admin-api/v1/workflows/statistics")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.totalInstances")
                .isEqualTo(0)
                .jsonPath("$.data.runningInstances")
                .isEqualTo(0)
                .jsonPath("$.data.pendingTasks")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("获取趋势数据 - 服务异常降级为空趋势")
    void getTrend_ServiceError_ReturnsFallback() {
        when(workflowStatisticsService.getTrend(anyInt()))
                .thenReturn(Mono.error(new IllegalStateException("trend down")));

        client.get()
                .uri("/admin-api/v1/workflows/statistics/trend")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.dateList")
                .isArray()
                .jsonPath("$.data.dateList.length()")
                .isEqualTo(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"complete", "rollback", "transfer", "delegate"})
    @DisplayName("任务操作把事件工厂交给事务内落箱，且继承租户上下文")
    void taskEvent_BuiltInsideTransactionWithTenant(String operation) {
        java.util.concurrent.atomic.AtomicReference<Object> built = new java.util.concurrent.atomic.AtomicReference<>();
        stubTaskOperationFactory(operation, built);
        stubTenantTaskQuery();

        StepVerifier.create(invokeTaskOperation(operation))
                .assertNext(
                        response -> assertThat(response.statusCode().value()).isEqualTo(200))
                .verifyComplete();

        assertThat(built.get()).isNotNull();
        if (operation.equals("complete")) {
            assertThat(built.get()).isInstanceOfSatisfying(TaskCompletedEvent.class, completed -> {
                assertThat(completed.getTaskId()).isEqualTo(5001L);
                assertThat(completed.getProcessInstanceId()).isEqualTo(3001L);
                assertThat(completed.getApproverId()).isEqualTo(USER_ID);
                assertThat(completed.getAction()).isEqualTo(TaskCompletedEvent.ACTION_APPROVE);
                assertThat(completed.getComment()).isEqualTo("同意");
            });
        } else {
            assertThat(built.get()).isInstanceOfSatisfying(TaskAssignedEvent.class, assigned -> {
                assertThat(assigned.getTaskId()).isEqualTo(5001L);
                assertThat(assigned.getProcessInstanceId()).isEqualTo(3001L);
                assertThat(assigned.getNodeCode()).isEqualTo("node_1");
                assertThat(assigned.getAssigneeId()).isEqualTo(operation.equals("rollback") ? USER_ID : 200L);
                assertThat(assigned.getAssignedBy()).isEqualTo(USER_ID);
            });
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"complete", "rollback", "transfer", "delegate"})
    @DisplayName("主操作错误原样传播且不触发事件旁路")
    void taskEvent_BusinessFailurePropagates(String operation) {
        IllegalStateException failure = new IllegalStateException("business failed");
        stubTaskOperation(operation, Mono.error(failure));

        StepVerifier.create(invokeTaskOperation(operation))
                .expectErrorMatches(error -> error == failure)
                .verify();
        verifyNoInteractions(workflowQueryService);
    }

    private void stubTenantTaskQuery() {
        when(workflowQueryService.getTaskById(5001L)).thenReturn(Mono.deferContextual(context -> {
            if (!"tenant-events".equals(context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null))) {
                return Mono.error(new IllegalStateException("tenant context missing"));
            }
            return Mono.just(testTaskDto);
        }));
    }

    /**
     * 按操作类型桩出“事务内事件工厂”，并记录 Handler 传给服务的事件实例。
     *
     * <p>新语义下事件不再经发布器直发，而是由服务在事务中调用工厂并落箱；
     * 此处只验证 Handler 正确组装了事件，持久化由工作流集成测试覆盖。
     *
     * @param operation 操作类型
     * @param built 记录 Handler 构造的事件
     */
    private void stubTaskOperationFactory(String operation, java.util.concurrent.atomic.AtomicReference<Object> built) {
        com.apex.scaffold.workflow.domain.model.Task task = new com.apex.scaffold.workflow.domain.model.Task();
        task.setId(5001L);
        task.setProcessInstanceId(3001L);
        task.setNodeCode("node_1");
        task.setAssigneeId(200L);
        switch (operation) {
            case "complete" -> when(workflowService.completeTask(eq(5001L), eq(USER_ID), eq("同意"), any()))
                    .thenAnswer(call -> {
                        @SuppressWarnings("unchecked")
                        java.util.function.BiFunction<
                                        com.apex.scaffold.workflow.domain.model.Task,
                                        ProcessInstance,
                                        com.apex.scaffold.common.event.AbstractDomainEvent>
                                factory = call.getArgument(3);
                        built.set(factory.apply(task, testInstance));
                        return Mono.empty();
                    });
            case "rollback" -> when(workflowService.rollbackToNode(
                            eq(5001L), eq("node_1"), eq(USER_ID), eq("原因"), any()))
                    .thenAnswer(call -> {
                        built.set(applyTaskFactory(call, task));
                        return Mono.empty();
                    });
            case "transfer" -> when(workflowService.transferTask(eq(5001L), eq(200L), eq(USER_ID), eq("原因"), any()))
                    .thenAnswer(call -> {
                        built.set(applyTaskFactory(call, task));
                        return Mono.empty();
                    });
            case "delegate" -> when(workflowService.delegateTask(eq(5001L), eq(200L), eq(USER_ID), eq("原因"), any()))
                    .thenAnswer(call -> {
                        built.set(applyTaskFactory(call, task));
                        return Mono.empty();
                    });
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private Object applyTaskFactory(
            org.mockito.invocation.InvocationOnMock call, com.apex.scaffold.workflow.domain.model.Task task) {
        @SuppressWarnings("unchecked")
        java.util.function.Function<
                        com.apex.scaffold.workflow.domain.model.Task,
                        com.apex.scaffold.common.event.AbstractDomainEvent>
                factory = call.getArgument(4);
        return factory.apply(task);
    }

    private void stubTaskOperation(String operation, Mono<Void> result) {
        switch (operation) {
            case "complete" -> when(workflowService.completeTask(eq(5001L), eq(USER_ID), eq("同意"), any()))
                    .thenReturn(result);
            case "rollback" -> when(workflowService.rollbackToNode(
                            eq(5001L), eq("node_1"), eq(USER_ID), eq("原因"), any()))
                    .thenReturn(result);
            case "transfer" -> when(workflowService.transferTask(eq(5001L), eq(200L), eq(USER_ID), eq("原因"), any()))
                    .thenReturn(result);
            case "delegate" -> when(workflowService.delegateTask(eq(5001L), eq(200L), eq(USER_ID), eq("原因"), any()))
                    .thenReturn(result);
            default -> throw new IllegalArgumentException(operation);
        }
    }

    private Mono<ServerResponse> invokeTaskOperation(String operation) {
        WorkflowHandler handler = new WorkflowHandler(
                workflowService, workflowQueryService, workflowStatisticsService, requestHandlerUtil);
        ObjectMapper mapper = new ObjectMapper();
        Mono<ServerResponse> response =
                switch (operation) {
                    case "complete" -> handler.completeTask(MockServerRequest.builder()
                            .pathVariable("taskId", "5001")
                            .body(Mono.just(mapper.convertValue(Map.of("comment", "同意"), CompleteTaskRequest.class))));
                    case "rollback" -> handler.rollbackTask(MockServerRequest.builder()
                            .pathVariable("taskId", "5001")
                            .body(Mono.just(mapper.convertValue(
                                    Map.of("targetNodeCode", "node_1", "reason", "原因"), RollbackTaskRequest.class))));
                    case "transfer" -> handler.transferTask(MockServerRequest.builder()
                            .pathVariable("taskId", "5001")
                            .body(Mono.just(mapper.convertValue(
                                    Map.of("newAssigneeId", 200L, "reason", "原因"), TransferTaskRequest.class))));
                    case "delegate" -> handler.delegateTask(MockServerRequest.builder()
                            .pathVariable("taskId", "5001")
                            .body(Mono.just(mapper.convertValue(
                                    Map.of("delegateToId", 200L, "reason", "原因"), DelegateTaskRequest.class))));
                    default -> throw new IllegalArgumentException(operation);
                };
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(USER_ID, "openid-1", "tenant-events"), "", List.of());
        return response.contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
                .contextWrite(context -> context.put(TenantContext.REACTOR_CONTEXT_KEY, "tenant-events"));
    }

    @Test
    @DisplayName("查询已不存在的实例详情 - 空结果返回空响应体")
    void getInstanceDetail_NotFound() {
        when(workflowQueryService.getInstanceDetail(9999L)).thenReturn(Mono.empty());

        client.get()
                .uri("/admin-api/v1/workflows/instances/9999")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .isEmpty();
    }
}
