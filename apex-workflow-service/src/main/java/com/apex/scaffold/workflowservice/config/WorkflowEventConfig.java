package com.apex.scaffold.workflowservice.config;

import com.apex.scaffold.workflowservice.infrastructure.event.LocalWorkflowEventPublisher;
import com.apex.scaffold.workflowservice.infrastructure.event.MqWorkflowEventPublisher;
import com.apex.scaffold.workflowservice.infrastructure.event.WorkflowEventPublisher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工作流事件发布器装配配置（迁移方案 Phase 3.4 事件改造）.
 *
 * <p>按运行环境选择 {@link WorkflowEventPublisher} 的实现，保证容器内始终恰好存在一个
 * 发布器 Bean，供 {@code WorkflowHandler} 注入使用。</p>
 *
 * <h2>按环境分条件装配</h2>
 * <ul>
 *   <li><strong>生产/开发（{@code apex.events.outbox.enabled=true}，默认）且存在
 *       {@link RocketMQTemplate} Bean</strong>：注册 {@link MqWorkflowEventPublisher}，
 *       将审批事件直发到主题 {@code apex-workflow-events}。</li>
 *   <li><strong>basic/H2（{@code apex.events.outbox.enabled=false}）或无 RocketMQ</strong>：
 *       注册轻量级 {@link LocalWorkflowEventPublisher}，仅记录日志、不投递，
 *       保证服务在无消息中间件的环境下可正常启动与完成审批流程。</li>
 * </ul>
 *
 * <p>沿用认证中心 {@code AuthCenterEventConfig} 的装配惯例：以既有的
 * {@code apex.events.outbox.enabled} 开关作为主判据（basic profile 已置为 {@code false}），
 * 并以 {@link ConditionalOnMissingBean} 兜底覆盖"开关开启但缺 RocketMQ"的边界场景。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see MqWorkflowEventPublisher
 * @see LocalWorkflowEventPublisher
 */
@Configuration
public class WorkflowEventConfig {

    /** Outbox 开关配置前缀（与认证中心保持一致，basic profile 置为 false）。 */
    private static final String OUTBOX_ENABLED_PREFIX = "apex.events.outbox";

    /**
     * 基于 RocketMQ 的工作流事件发布器 Bean（生产环境）。
     *
     * <p>仅当启用 Outbox（默认启用）且容器内存在 {@link RocketMQTemplate} 时注册，
     * 将审批事件异步直发到 {@code apex-workflow-events} 主题。</p>
     *
     * @param mqTemplate RocketMQ 模板
     * @return WorkflowEventPublisher 实例
     */
    @Bean
    @ConditionalOnProperty(
            prefix = OUTBOX_ENABLED_PREFIX,
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    @ConditionalOnBean(RocketMQTemplate.class)
    public WorkflowEventPublisher mqWorkflowEventPublisher(RocketMQTemplate mqTemplate) {
        return new MqWorkflowEventPublisher(mqTemplate);
    }

    /**
     * 轻量级工作流事件发布器 Bean（basic/H2 或无 RocketMQ 时兜底）。
     *
     * <p>当上述 RocketMQ 实现未注册时启用，仅记录日志不投递，保证服务可正常启动。</p>
     *
     * @return WorkflowEventPublisher 实例
     */
    @Bean
    @ConditionalOnMissingBean(WorkflowEventPublisher.class)
    public WorkflowEventPublisher localWorkflowEventPublisher() {
        return new LocalWorkflowEventPublisher();
    }
}
