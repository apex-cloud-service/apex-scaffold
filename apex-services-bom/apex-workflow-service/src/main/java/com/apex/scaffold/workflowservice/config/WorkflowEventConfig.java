package com.apex.scaffold.workflowservice.config;

import com.apex.scaffold.common.event.EventChannelMode;
import com.apex.scaffold.common.event.EventChannelProperties;
import com.apex.scaffold.workflowservice.infrastructure.event.LocalWorkflowEventPublisher;
import com.apex.scaffold.workflowservice.infrastructure.event.MqWorkflowEventPublisher;
import com.apex.scaffold.workflowservice.infrastructure.event.WorkflowEventPublisher;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 工作流事件发布器装配配置（方案 M3 / 批次 C8 事件通道自适应）.
 *
 * <p>本配置消费共享的事件通道抽象 {@link EventChannelMode}（{@code apex.events.channel}），
 * 由它统一决定"用哪条路"：
 *
 * <ul>
 *   <li>{@code REMOTE}：需要跨服务投递 → {@link MqWorkflowEventPublisher}；</li>
 *   <li>{@code LOCAL}：进程内派发 → {@link LocalWorkflowEventPublisher}；</li>
 *   <li>{@code AUTO}（默认）：按环境能力解析——存在 {@link RocketMQTemplate} 且
 *       Outbox 启用则等同 {@code REMOTE}，否则 {@code LOCAL}。组合部署（apex-boot-all）
 *       无 MQ 时自动退化为进程内派发，拆分部署自动走 MQ，业务代码零感知。</li>
 * </ul>
 *
 * <p>与既有 {@code apex.events.outbox.enabled} 的关系：通道模式决定"用哪条路"，
 * Outbox 开关决定"是否持久化投递"。两者正交，AUTO 模式下同时作为远程能力判据之一。
 *
 * <p>装配保证：容器内始终<b>恰好一个</b> {@link WorkflowEventPublisher}，供
 * {@code WorkflowHandler} 注入使用。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 * @see EventChannelMode
 * @see MqWorkflowEventPublisher
 * @see LocalWorkflowEventPublisher
 */
@Configuration
@EnableConfigurationProperties(EventChannelProperties.class)
public class WorkflowEventConfig {

    /** Outbox 开关配置前缀（basic profile 置为 false）。 */
    private static final String OUTBOX_ENABLED_PREFIX = "apex.events.outbox";

    /**
     * 注册工作流事件发布器（按通道模式解析结果二选一）。
     *
     * <p>解析顺序：{@link EventChannelMode#resolve(boolean)} 依据
     * "Outbox 启用 且 存在 RocketMQTemplate"判定远程能力；结果为 {@code REMOTE}
     * 且 MQ 模板可用时装配 MQ 实现，否则装配本地实现。
     *
     * @param properties    事件通道配置（{@code apex.events.channel}）
     * @param mqTemplate    RocketMQ 模板（可能不存在）
     * @param outboxEnabled Outbox 开关（远程能力判据之一）
     * @param workflowEventTopic 工作流事件主题名称
     * @return 唯一的事件发布器
     */
    @Bean
    @ConditionalOnMissingBean(WorkflowEventPublisher.class)
    public WorkflowEventPublisher workflowEventPublisher(
            EventChannelProperties properties,
            ObjectProvider<RocketMQTemplate> mqTemplate,
            org.springframework.context.ApplicationEventPublisher applicationEventPublisher,
            @Value("${" + OUTBOX_ENABLED_PREFIX + ".enabled:true}") boolean outboxEnabled,
            @Value("${apex.mq.workflow-event-topic:apex-workflow-events}") String workflowEventTopic) {
        RocketMQTemplate template = mqTemplate.getIfAvailable();
        boolean remoteCapable = outboxEnabled && template != null;
        EventChannelMode effective = properties.channelMode().resolve(remoteCapable);
        if (effective == EventChannelMode.REMOTE && template != null) {
            return new MqWorkflowEventPublisher(template, workflowEventTopic);
        }
        return new LocalWorkflowEventPublisher(applicationEventPublisher);
    }

    /**
     * 强制 REMOTE 却缺少 MQ 能力时的快速失败守卫。
     *
     * <p>当 {@code apex.events.channel=remote} 但容器内没有 {@link RocketMQTemplate}
     * 或 Outbox 未启用时，静默退化为本地派发会让运维误以为消息已跨服务投递——
     * 这是危险的空洞配置。本 Bean 在该组合下启动即失败。
     *
     * @param mqTemplate    RocketMQ 模板（可能不存在）
     * @param outboxEnabled Outbox 开关
     * @return 校验哨兵（无实际用途）
     */
    @Bean
    @ConditionalOnProperty(prefix = "apex.events", name = "channel", havingValue = "remote")
    public Object workflowRemoteChannelGuard(
            ObjectProvider<RocketMQTemplate> mqTemplate,
            @Value("${" + OUTBOX_ENABLED_PREFIX + ".enabled:true}") boolean outboxEnabled) {
        if (mqTemplate.getIfAvailable() == null) {
            throw new IllegalStateException(
                    "apex.events.channel=remote 需要 RocketMQTemplate，但容器中不存在；" + "请配置 RocketMQ 或改用 channel=auto/local");
        }
        if (!outboxEnabled) {
            throw new IllegalStateException(
                    "apex.events.channel=remote 需要 apex.events.outbox.enabled=true（可靠投递依赖 Outbox），" + "当前为 false");
        }
        return new Object();
    }
}
