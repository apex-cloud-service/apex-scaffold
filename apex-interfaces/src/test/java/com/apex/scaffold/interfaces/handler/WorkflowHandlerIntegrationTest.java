package com.apex.scaffold.interfaces.handler;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.service.WorkflowService;
import com.apex.scaffold.interfaces.dto.workflow.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@WebFluxTest
@ContextConfiguration(classes = {WorkflowHandler.class})
@DisplayName("工作流 Handler 集成测试")
class WorkflowHandlerIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private WorkflowService workflowService;

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
                .jsonPath("$.message")
                .isEqualTo("操作成功");
    }

    @Test
    @DisplayName("流程回退接口测试 - 参数校验失败")
    void testRollbackTask_ValidationFailed() {
        RollbackTaskRequest request = new RollbackTaskRequest();
        request.setTargetNodeCode("manager_approval");
        request.setReason("");

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
                .jsonPath("$.message")
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
                .jsonPath("$.message")
                .isEqualTo("只能转办待办任务");
    }
}
