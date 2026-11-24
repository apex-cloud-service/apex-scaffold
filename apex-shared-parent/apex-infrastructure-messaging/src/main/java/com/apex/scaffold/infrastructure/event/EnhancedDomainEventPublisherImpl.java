package com.apex.scaffold.infrastructure.event;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.domain.event.DomainEventBus;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

/**
 * 增强版领域事件发布器实现
 *
 * <p>基于 Spring ApplicationEventPublisher 和 RocketMQ 实现的领域事件发布器，
 * 支持同步事件发布、异步事件发布和分布式事件发布三种模式。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>同步模式</strong>：通过 Spring ApplicationEventPublisher 同步发布事件，
 *       适用于需要立即处理且对事务一致性有要求的场景</li>
 *   <li><strong>异步模式</strong>：通过线程池异步执行事件处理，适用于对响应时间敏感的场景</li>
 *   <li><strong>分布式模式</strong>：通过 RocketMQ 发布事件到消息队列，适用于跨服务通信场景</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>可靠性保障</strong>：分布式事件支持持久化和重试机制</li>
 *   <li><strong>性能优化</strong>：异步事件不会阻塞主业务流程</li>
 *   <li><strong>事务一致性</strong>：同步事件参与当前事务</li>
 *   <li><strong>可扩展性</strong>：支持多种事件发布策略</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * // 发布同步事件（参与事务）
 * eventPublisher.publishSync(event);
 *
 * // 发布异步事件（不阻塞）
 * eventPublisher.publishAsync(event);
 *
 * // 发布分布式事件（跨服务）
 * eventPublisher.publishDistributed(event);
 * }</pre>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see DomainEventPublisher
 * @see DomainEventBus
 * @see RocketMQTemplate
 */
@Component
@ConditionalOnBean(RocketMQTemplate.class)
@Slf4j
public class EnhancedDomainEventPublisherImpl implements DomainEventPublisher {
    /** 领域事件总线 */
    private final DomainEventBus domainEventBus;

    /** Spring应用事件发布器 */
    private final ApplicationEventPublisher applicationEventPublisher;

    /** RocketMQ模板（用于分布式事件发布） */
    private final RocketMQTemplate rocketMQTemplate;

    /** 领域事件主题名称 */
    private final String domainEventTopic;

    /** 是否启用分布式事件发布 */
    private final boolean domainEventEnabled;

    /**
     * 构造增强版领域事件发布器。
     *
     * @param domainEventBus 领域事件总线
     * @param applicationEventPublisher Spring 应用事件发布器
     * @param rocketMqTemplate RocketMQ 模板，用于分布式事件发布
     * @param domainEventTopic 领域事件主题名称，默认 {@code apex-domain-event}
     * @param domainEventEnabled 是否启用分布式事件发布，默认 true
     */
    public EnhancedDomainEventPublisherImpl(
            DomainEventBus domainEventBus,
            ApplicationEventPublisher applicationEventPublisher,
            RocketMQTemplate rocketMqTemplate,
            @Value("${apex.mq.domain-event-topic:apex-domain-event}") String domainEventTopic,
            @Value("${apex.mq.domain-event-enabled:true}") boolean domainEventEnabled) {
        this.domainEventBus = domainEventBus;
        this.applicationEventPublisher = applicationEventPublisher;
        this.rocketMQTemplate = rocketMqTemplate;
        this.domainEventTopic = domainEventTopic;
        this.domainEventEnabled = domainEventEnabled;
    }

    /**
     * 默认发布方法（异步模式）
     *
     * <p>采用异步方式发布领域事件，不会阻塞主业务流程。
     * 同时通过 Spring 事件机制和领域事件总线发布事件。
     *
     * @param event 领域事件对象
     */
    @Override
    public Mono<Void> publish(AbstractDomainEvent event) {
        return publishAsync(event);
    }

    /**
     * 同步发布领域事件
     *
     * <p>同步发布事件，事件处理会参与当前事务。
     * 适用于需要事务一致性保证的场景。
     *
     * @param event 领域事件对象
     */
    public Mono<Void> publishSync(AbstractDomainEvent event) {
        log.debug("同步发布领域事件: {}, ID: {}", event.getClass().getSimpleName(), event.getEventId());

        // 通过 Spring ApplicationEventPublisher 同步发布
        return Mono.deferContextual((ContextView ctx) -> {
                    // 发布前将当前租户 ID 写进事件 DTO，监听器侧跨线程可直接读，
                    // 不再依赖 TenantContext ThreadLocal（避免 publishAsync 切线程后上下文丢失）。
                    String tenantId = ctx.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
                    if (tenantId == null) {
                        tenantId = TenantContext.currentTenantId().orElse(null);
                    }
                    event.attachTenantContext(tenantId);
                    applicationEventPublisher.publishEvent(event);
                    return Mono.empty();
                })
                .then(domainEventBus.publishSync(event))
                .doOnSuccess(v -> log.info("同步事件发布完成: {} (eventId={})", event.getEventName(), event.getEventId()))
                .doOnError(e -> log.error(
                        "同步事件发布失败: {} (eventId={}), error: {}",
                        event.getEventName(),
                        event.getEventId(),
                        e.getMessage(),
                        e));
    }

    /**
     * 异步发布领域事件
     *
     * <p>异步发布事件，事件处理在独立线程中执行，不会阻塞主业务流程。
     * 适用于对响应时间敏感的场景。
     *
     * @param event 领域事件对象
     */
    public Mono<Void> publishAsync(AbstractDomainEvent event) {
        log.debug("异步发布领域事件: {}, ID: {}", event.getClass().getSimpleName(), event.getEventId());

        // 通过线程池异步执行
        return Mono.fromRunnable(() -> {
                    // C2 修复：在执行线程（BlockingIoSchedulers.event）内先写租户上下文，
                    // 因为 Spring ApplicationEventMulticaster 将在**同一调用线程**调用同步
                    // @EventListener。监听器里的 Repository 经 Mybatis/TenantLineInnerInterceptor
                    // 需要从 TenantContext.currentTenantId 读到租户。
                    String tenantId = event.getTenantId();
                    String prev = TenantContext.setTenantId(tenantId);
                    try {
                        applicationEventPublisher.publishEvent(event);
                    } finally {
                        TenantContext.restoreTenantId(prev);
                    }
                })
                .then(domainEventBus.publish(event))
                .subscribeOn(BlockingIoSchedulers.event())
                .doOnSuccess(v -> log.debug("异步事件处理完成: {} (eventId={})", event.getEventName(), event.getEventId()))
                .doOnError(e -> log.error(
                        "异步事件处理失败: {} (eventId={}), error: {}",
                        event.getEventName(),
                        event.getEventId(),
                        e.getMessage(),
                        e))
                .doOnSuccess(v -> log.info("异步事件发布完成: {} (eventId={})", event.getEventName(), event.getEventId()))
                .doOnError(e -> log.error(
                        "异步事件发布失败: {} (eventId={}), error: {}",
                        event.getEventName(),
                        event.getEventId(),
                        e.getMessage(),
                        e));
    }

    /**
     * 发布分布式领域事件
     *
     * <p>通过 RocketMQ 将事件发布到消息队列，实现跨服务的事件通信。
     * 消息会被持久化，支持重试机制，保证事件的可靠传递。
     *
     * @param event 领域事件对象
     */
    public Mono<Void> publishDistributed(AbstractDomainEvent event) {
        if (!domainEventEnabled) {
            log.debug("分布式事件发布未启用，跳过: {}", event.getEventName());
            return Mono.empty();
        }

        log.debug("发布分布式领域事件: {}, ID: {}", event.getClass().getSimpleName(), event.getEventId());

        String eventJson = JsonUtil.toJson(event);

        Message<String> message = MessageBuilder.withPayload(eventJson)
                .setHeader(RocketMQHeaders.KEYS, event.getEventId())
                .build();

        return Mono.<Void>create(sink -> {
                    try {
                        rocketMQTemplate.asyncSend(
                                domainEventTopic, message, new org.apache.rocketmq.client.producer.SendCallback() {
                                    /** {@inheritDoc} */
                                    @Override
                                    public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                                        log.info(
                                                "分布式事件发送成功: {} (eventId={}, msgId={})",
                                                event.getEventName(),
                                                event.getEventId(),
                                                sendResult.getMsgId());
                                        sink.success();
                                    }

                                    /** {@inheritDoc} */
                                    @Override
                                    public void onException(Throwable e) {
                                        log.error(
                                                "分布式事件发送失败: {} (eventId={}), error: {}",
                                                event.getEventName(),
                                                event.getEventId(),
                                                e.getMessage(),
                                                e);
                                        sink.error(e);
                                    }
                                });
                    } catch (Throwable e) {
                        sink.error(e);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.event())
                .doOnSuccess(v -> log.info("分布式事件发布完成: {} (eventId={})", event.getEventName(), event.getEventId()))
                .doOnError(e -> log.error(
                        "分布式事件发布失败: {} (eventId={}), error: {}",
                        event.getEventName(),
                        event.getEventId(),
                        e.getMessage(),
                        e));
    }

    /**
     * 发布事件到所有渠道
     *
     * <p>同时通过同步、异步和分布式三种方式发布事件，确保事件被可靠处理。
     *
     * @param event 领域事件对象
     */
    public Mono<Void> publishToAllChannels(AbstractDomainEvent event) {
        log.debug("发布事件到所有渠道: {}, ID: {}", event.getClass().getSimpleName(), event.getEventId());

        return Mono.when(publishSync(event), publishAsync(event), publishDistributed(event))
                .doOnSuccess(v -> log.info("事件已发布到所有渠道: {} (eventId={})", event.getEventName(), event.getEventId()))
                .doOnError(e -> log.error(
                        "事件发布到所有渠道失败: {} (eventId={}), error: {}",
                        event.getEventName(),
                        event.getEventId(),
                        e.getMessage(),
                        e));
    }

    /**
     * 根据事件类型选择发布策略
     *
     * <p>根据事件类型自动选择合适的发布策略：
     * <ul>
     *   <li>需要事务一致性的事件 → 同步发布</li>
     *   <li>普通业务事件 → 异步发布</li>
     *   <li>跨服务事件 → 分布式发布</li>
     * </ul>
     *
     * @param event     领域事件对象
     * @param strategy  发布策略
     */
    public Mono<Void> publishWithStrategy(AbstractDomainEvent event, PublishStrategy strategy) {
        log.debug("使用策略 {} 发布事件: {}, ID: {}", strategy, event.getClass().getSimpleName(), event.getEventId());

        return switch (strategy) {
            case SYNC -> publishSync(event);
            case ASYNC -> publishAsync(event);
            case DISTRIBUTED -> publishDistributed(event);
            case ALL -> publishToAllChannels(event);
        };
    }

    /**
     * 发布策略枚举
     */
    public enum PublishStrategy {
        /** 同步发布：事件处理参与当前事务 */
        SYNC,

        /** 异步发布：事件处理在独立线程中执行 */
        ASYNC,

        /** 分布式发布：通过消息队列发布事件 */
        DISTRIBUTED,

        /** 发布到所有渠道 */
        ALL
    }
}
