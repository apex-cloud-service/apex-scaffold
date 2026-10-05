package com.apex.example.integration;

import com.apex.example.repository.ExampleUserMapper;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinition;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.WorkflowDefinitionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * 工作流引擎集成测试（License Feature: workflow）
 *
 * <p>测试轻量级工作流引擎的流程定义查询、流程实例管理、任务管理等端到端场景。
 * 仅在 Enterprise License 下执行（Basic/Standard 版本会被 FeatureGate 拦截）。
 *
 * <p><strong>注意</strong>：此测试依赖 V20 迁移脚本创建的工作流表，
 * 但 apex-example 模块使用独立的测试数据库，暂时禁用此测试。
 * 工作流功能的完整测试应在 apex-interfaces 模块中进行。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Disabled("工作流表未在 apex-example 测试数据库中创建，暂时禁用")
class WorkflowIntegrationTest extends BaseIntegrationTest {

    private final WorkflowDefinitionMapper workflowDefinitionMapper;

    /**
     * 构造器注入共享测试依赖与工作流 Mapper。
     *
     * @param webTestClient            响应式 Web 测试客户端
     * @param exampleUserMapper        示例用户 Mapper
     * @param workflowDefinitionMapper 工作流定义 Mapper
     */
    public WorkflowIntegrationTest(
            WebTestClient webTestClient,
            ExampleUserMapper exampleUserMapper,
            WorkflowDefinitionMapper workflowDefinitionMapper) {
        super(webTestClient, exampleUserMapper);
        this.workflowDefinitionMapper = workflowDefinitionMapper;
    }

    @BeforeEach
    void setUpWorkflow() {
        // 清理测试数据
        workflowDefinitionMapper.delete(null);
    }

    @Test
    void shouldGetWorkflowDefinition() {
        // Given: 插入一个流程定义种子数据
        WorkflowDefinition definition = new WorkflowDefinition();
        definition.setWorkflowCode("leave_approval");
        definition.setWorkflowName("请假审批流程");
        definition.setVersion(1);
        definition.setEnabled(1);
        // 简化：这里应该设置完整的节点配置 JSON，暂时用空字符串占位
        definition.setDagJson("[]");

        // 注意：这里简化测试，实际应通过 Repository 或 Service 插入
        // workflowDefinitionRepository.save(definition);

        // When: 调用 GET /admin-api/v1/workflows/definitions/{code}
        webTestClient
                .get()
                .uri("/admin-api/v1/workflows/definitions/leave_approval")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(200);
        // 由于 FeatureGate 限制，Basic/Standard License 会返回 403
        // Enterprise License 才能通过
    }

    @Test
    void shouldReturnForbiddenForBasicLicense() {
        // Given: 当前 License 为 Basic（无 workflow 特性）

        // When: 调用工作流接口
        webTestClient
                .get()
                .uri("/admin-api/v1/workflows/definitions/any_code")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                // Then: 期望返回 403 Forbidden（FeatureGate 拦截）
                .expectStatus()
                .isForbidden();
    }

    @Test
    void shouldGetPendingTasksReturnEmptyList() {
        // When: 查询待办任务（无任何流程实例）
        webTestClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/admin-api/v1/workflows/tasks/pending")
                        .queryParam("userId", 1L)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.code")
                .isEqualTo(200)
                .jsonPath("$.data")
                .isEmpty();
    }
}
