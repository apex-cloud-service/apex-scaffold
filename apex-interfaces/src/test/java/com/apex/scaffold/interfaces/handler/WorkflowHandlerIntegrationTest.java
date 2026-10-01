package com.apex.scaffold.interfaces.handler;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.WorkflowInstanceDetailDTO;
import com.apex.scaffold.application.dto.WorkflowTaskDTO;
import com.apex.scaffold.application.service.WorkflowService;
import com.apex.scaffold.application.workflow.WorkflowQueryService;
import com.apex.scaffold.application.workflow.WorkflowStatisticsService;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.domain.model.workflow.ProcessInstance;
import com.apex.scaffold.interfaces.dto.workflow.*;
import com.apex.scaffold.interfaces.router.WorkflowRouter;
import com.apex.scaffold.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.interfaces.util.MessageUtils;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest(
        excludeAutoConfiguration = {
            org.springframework.boot.autoconfigure.security.oauth2.client.reactive.ReactiveOAuth2ClientAutoConfiguration
                    .class
        })
@ContextConfiguration(
        classes = {
            WorkflowHandler.class,
            WorkflowRouter.class,
            com.apex.scaffold.interfaces.exception.GlobalExceptionHandler.class
        })
@DisplayName("工作流 Handler 集成测试")
class WorkflowHandlerIntegrationTest {

    private WebTestClient webTestClient;

    /**
     * 构造器注入 WebTestClient（Spring Boot 测试框架自动解析构造器参数）。
     *
     * @param webTestClient 响应式 Web 测试客户端
     */
    public WorkflowHandlerIntegrationTest(WebTestClient webTestClient) {
        this.webTestClient = webTestClient;
    }

    @BeforeEach
    void setUpAuthentication() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(900L, "user-900", "tenant-a"), null, List.of());
        webTestClient = webTestClient
                .mutateWith(SecurityMockServerConfigurers.mockAuthentication(authentication))
                .mutateWith(SecurityMockServerConfigurers.csrf());
    }

    @MockBean
    private WorkflowService workflowService;

    @MockBean
    private WorkflowQueryService workflowQueryService;

    @MockBean
    private WorkflowStatisticsService workflowStatisticsService;

    @MockBean
    private MessageUtils messageUtils;

    @MockBean
    private com.apex.scaffold.interfaces.monitor.ExceptionMonitor exceptionMonitor;

    @MockBean
    private jakarta.validation.Validator validator;

    @Test
    @DisplayName("发起流程实例接口测试 - 成功")
    void testStartProcessInstance_Success() {
        ProcessInstance instance = new ProcessInstance();
        instance.setId(100L);
        instance.setStatus("RUNNING");
        when(workflowService.startProcessInstance("leave_approval", "LEAVE-2026-001", 900L, "{\"days\":3}"))
                .thenReturn(Mono.just(instance));

        StartProcessInstanceRequest request = new StartProcessInstanceRequest();
        request.setBusinessKey("LEAVE-2026-001");
        request.setContextJson("{\"days\":3}");

        webTestClient
                .post()
                .uri("/api/v1/workflows/leave_approval/instances")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.data.id")
                .isEqualTo(100L);
    }

    @Test
    @DisplayName("查询当前用户待办接口测试 - 成功")
    void testListPendingTasks_Success() {
        WorkflowTaskDTO task = WorkflowTaskDTO.builder()
                .id(1000L)
                .assigneeId(900L)
                .status("PENDING")
                .build();
        when(workflowQueryService.listMyTasks(900L, 1, 10))
                .thenReturn(Mono.just(PageResponse.of(List.of(task), 1L, 1, 10)));

        webTestClient
                .get()
                .uri("/api/v1/workflows/tasks/pending?pageNum=1&pageSize=10")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.data.list[0].id")
                .isEqualTo(1000L);
    }

    @Test
    @DisplayName("完成任务接口测试 - 使用认证上下文用户")
    void testCompleteTask_UsesAuthenticatedUser() {
        when(workflowService.completeTask(1000L, 900L, "同意")).thenReturn(Mono.empty());
        CompleteTaskRequest request = new CompleteTaskRequest();
        request.setComment("同意");

        webTestClient
                .post()
                .uri("/api/v1/workflows/tasks/1000/complete")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk();

        verify(workflowService).completeTask(1000L, 900L, "同意");
    }

    @Test
    @DisplayName("查询流程实例详情接口测试 - 成功")
    void testGetInstanceDetail_Success() {
        WorkflowInstanceDetailDTO detail = new WorkflowInstanceDetailDTO();
        when(workflowQueryService.getInstanceDetail(100L)).thenReturn(Mono.just(detail));

        webTestClient
                .get()
                .uri("/admin-api/v1/workflows/instances/100")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("流程回退接口测试 - 成功")
    void testRollbackTask_Success() {
        when(workflowService.rollbackToNode(anyLong(), anyString(), anyLong(), anyString()))
                .thenReturn(Mono.empty());

        RollbackTaskRequest request = new RollbackTaskRequest();
        request.setTargetNodeCode("manager_approval");
        request.setReason("材料不完整");

        webTestClient
                .post()
                .uri("/api/workflow/tasks/1000/rollback")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0)
                .jsonPath("$.msg")
                .isEqualTo("成功");
    }

    @Test
    @DisplayName("流程回退接口测试 - 使用认证上下文用户")
    void testRollbackTask_UsesAuthenticatedUser() {
        when(workflowService.rollbackToNode(anyLong(), anyString(), anyLong(), anyString()))
                .thenReturn(Mono.empty());

        RollbackTaskRequest request = new RollbackTaskRequest();
        request.setTargetNodeCode("manager_approval");
        request.setReason("材料不完整");

        webTestClient
                .post()
                .uri("/api/workflow/tasks/1000/rollback")
                .header("X-User-Id", "999")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk();

        verify(workflowService).rollbackToNode(1000L, "manager_approval", 900L, "材料不完整");
    }

    @Test
    @DisplayName("流程回退接口测试 - 参数校验失败")
    void testRollbackTask_ValidationFailed() {
        RollbackTaskRequest request = new RollbackTaskRequest();
        request.setTargetNodeCode("manager_approval");
        request.setReason("");

        jakarta.validation.ConstraintViolation<RollbackTaskRequest> violation =
                org.mockito.Mockito.mock(jakarta.validation.ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("回退原因不能为空");

        when(validator.validate(any(RollbackTaskRequest.class))).thenReturn(java.util.Set.of(violation));

        webTestClient
                .post()
                .uri("/api/workflow/tasks/1000/rollback")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest();
    }

    @Test
    @DisplayName("任务转办接口测试 - 成功")
    void testTransferTask_Success() {
        when(workflowService.transferTask(anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(Mono.empty());

        TransferTaskRequest request = new TransferTaskRequest();
        request.setNewAssigneeId(10003L);
        request.setReason("本周出差");

        webTestClient
                .post()
                .uri("/api/workflow/tasks/1001/transfer")
                .header("X-User-Id", "10002")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("任务委派接口测试 - 成功")
    void testDelegateTask_Success() {
        when(workflowService.delegateTask(anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(Mono.empty());

        DelegateTaskRequest request = new DelegateTaskRequest();
        request.setDelegateToId(10004L);
        request.setReason("请先初审");

        webTestClient
                .post()
                .uri("/api/workflow/tasks/1002/delegate")
                .header("X-User-Id", "10002")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("挂起流程实例接口测试 - 成功")
    void testSuspendInstance_Success() {
        when(workflowService.suspendInstance(anyLong(), anyLong(), anyString())).thenReturn(Mono.empty());

        SuspendInstanceRequest request = new SuspendInstanceRequest();
        request.setReason("数据异常");

        webTestClient
                .post()
                .uri("/api/workflow/instances/100/suspend")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("恢复流程实例接口测试 - 成功")
    void testResumeInstance_Success() {
        when(workflowService.resumeInstance(anyLong(), anyLong(), anyString())).thenReturn(Mono.empty());

        ResumeInstanceRequest request = new ResumeInstanceRequest();
        request.setReason("数据已修复");

        webTestClient
                .post()
                .uri("/api/workflow/instances/100/resume")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("错误处理测试 - IllegalArgumentException")
    void testErrorHandling_IllegalArgumentException() {
        when(workflowService.rollbackToNode(anyLong(), anyString(), anyLong(), anyString()))
                .thenReturn(Mono.error(new IllegalArgumentException("目标节点不存在")));

        RollbackTaskRequest request = new RollbackTaskRequest();
        request.setTargetNodeCode("invalid_node");
        request.setReason("test");

        webTestClient
                .post()
                .uri("/api/workflow/tasks/1000/rollback")
                .header("X-User-Id", "1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400)
                .jsonPath("$.msg")
                .isEqualTo("目标节点不存在");
    }

    @Test
    @DisplayName("错误处理测试 - IllegalStateException")
    void testErrorHandling_IllegalStateException() {
        when(workflowService.transferTask(anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("只能转办待办任务")));

        TransferTaskRequest request = new TransferTaskRequest();
        request.setNewAssigneeId(10003L);
        request.setReason("test");

        webTestClient
                .post()
                .uri("/api/workflow/tasks/1001/transfer")
                .header("X-User-Id", "10002")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(400)
                .jsonPath("$.msg")
                .isEqualTo("只能转办待办任务");
    }
}
