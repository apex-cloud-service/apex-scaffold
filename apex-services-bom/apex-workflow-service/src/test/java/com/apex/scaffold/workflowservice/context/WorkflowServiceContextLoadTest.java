package com.apex.scaffold.workflowservice.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ApprovalRecordMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.ProcessInstanceMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.TaskMapper;
import com.apex.scaffold.workflow.infrastructure.persistence.mapper.WorkflowDefinitionMapper;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestConstructor;

/**
 * 工作流服务 basic Profile 轻量上下文加载测试。
 *
 * <p>以 {@code basic} Profile 全量加载 {@link com.apex.scaffold.workflowservice.WorkflowServiceApplication}
 * 应用上下文（H2 内存库、事务 Outbox 关闭、Nacos/RocketMQ 关闭，事件发布器降级为
 * {@code LocalWorkflowEventPublisher}），用于验证运行期装配的完整性——
 * 尤其是位于库模块 {@code apex-workflow} 的 Mapper 是否被 {@code @MapperScan} 正确注册。</p>
 *
 * <p>既有切片测试（{@code @WebFluxTest}）与静态迁移脚本测试均不启动完整容器，
 * 无法覆盖 {@code @MapperScan} 的装配结果；本测试通过断言上下文中的 Mapper Bean
 * 补齐该验证缺口（迁移方案 §3.6）。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
            // apex-common ConfigEncryptionService 要求恰好 32 字节的配置加密密钥
            "security.config.encryption.key=01234567890123456789012345678901"
        })
@ActiveProfiles("basic")
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class WorkflowServiceContextLoadTest {

    /** 工作流服务应用上下文。 */
    private final ApplicationContext context;

    /**
     * 校验 {@code apex-workflow} 库中的工作流 Mapper 均已注册到应用上下文。
     *
     * <p>{@code WorkflowDefinitionMapper / ProcessInstanceMapper / TaskMapper /
     * ApprovalRecordMapper} 均位于库模块
     * {@code com.apex.scaffold.workflow.infrastructure.persistence.mapper}，
     * 依赖启动类的 {@code @MapperScan} 才能被注册；任一缺失即说明装配回归。</p>
     */
    @Test
    void workflowMappersAreWiredIntoContext() {
        assertThat(context.getBeanNamesForType(WorkflowDefinitionMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ProcessInstanceMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(TaskMapper.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(ApprovalRecordMapper.class)).hasSize(1);
    }
}
