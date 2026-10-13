package com.apex.scaffold.workflowservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.apex.scaffold.workflowservice.infrastructure.event.LocalWorkflowEventPublisher;
import com.apex.scaffold.workflowservice.infrastructure.event.MqWorkflowEventPublisher;
import com.apex.scaffold.workflowservice.infrastructure.event.WorkflowEventPublisher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link WorkflowEventConfig} 装配测试.
 *
 * <p>验证不同环境组合下容器内始终恰好存在一个 {@link WorkflowEventPublisher} Bean：
 * 生产（启用 outbox + 存在 RocketMQTemplate）装配 MQ 发布器，
 * basic/H2（关闭 outbox）或缺少 RocketMQ 时回退本地发布器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@DisplayName("WorkflowEventConfig 装配测试")
class WorkflowEventConfigTest {

    /** 仅装配事件配置（模拟无 RocketMQ 的 basic/H2 环境）。 */
    private final ApplicationContextRunner localRunner =
            new ApplicationContextRunner().withUserConfiguration(WorkflowEventConfig.class);

    /** 先注册 RocketMQTemplate 替身，再装配事件配置（模拟生产环境）。 */
    private final ApplicationContextRunner mqRunner = new ApplicationContextRunner()
            .withUserConfiguration(RocketMqTemplateConfig.class, WorkflowEventConfig.class);

    @Test
    @DisplayName("无 RocketMQTemplate 时装配本地兜底发布器")
    void shouldFallbackToLocalWhenNoRocketMq() {
        localRunner.run(context -> assertThat(context.getBean(WorkflowEventPublisher.class))
                .isInstanceOf(LocalWorkflowEventPublisher.class));
    }

    @Test
    @DisplayName("启用 outbox 且存在 RocketMQTemplate 时装配 MQ 发布器")
    void shouldUseMqPublisherWhenRocketMqPresent() {
        mqRunner.run(context ->
                assertThat(context.getBean(WorkflowEventPublisher.class)).isInstanceOf(MqWorkflowEventPublisher.class));
    }

    @Test
    @DisplayName("显式关闭 outbox 时回退本地发布器")
    void shouldFallbackToLocalWhenOutboxDisabled() {
        mqRunner.withPropertyValues("apex.events.outbox.enabled=false")
                .run(context -> assertThat(context.getBean(WorkflowEventPublisher.class))
                        .isInstanceOf(LocalWorkflowEventPublisher.class));
    }

    /**
     * 测试用 RocketMQ 模板替身配置。
     *
     * @author Apex Team
     * @version 1.0.0
     * @since 1.3.1
     */
    @Configuration(proxyBeanMethods = false)
    static class RocketMqTemplateConfig {

        /**
         * 提供 RocketMQ 模板替身，触发条件装配中的 {@code @ConditionalOnBean}。
         *
         * @return RocketMQTemplate 替身
         */
        @Bean
        RocketMQTemplate rocketMQTemplate() {
            return mock(RocketMQTemplate.class);
        }
    }
}
