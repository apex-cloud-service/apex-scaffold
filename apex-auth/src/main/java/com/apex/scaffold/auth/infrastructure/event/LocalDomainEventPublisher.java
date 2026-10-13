package com.apex.scaffold.auth.infrastructure.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 轻量级领域事件发布器（本地降级实现）。
 *
 * <p>当 {@code apex.events.outbox.enabled=false}（如 basic/H2 环境，H2 无法执行
 * Outbox 的 {@code ON DUPLICATE KEY UPDATE ... IF()} 方言 SQL）时启用。本实现不做
 * 任何持久化，仅在 DEBUG 级别记录事件摘要后立即返回完成信号，保证服务可正常启动与登出流程可用。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>不落库</strong>：避免在无 Outbox 表的运行环境中抛错</li>
 *   <li><strong>发送即忘</strong>：遵循 {@link DomainEventPublisher} 的异步语义，返回 {@code Mono.empty()}</li>
 *   <li><strong>可观测</strong>：DEBUG 日志保留事件类型与 ID，便于本地排查</li>
 * </ul>
 *
 * <p>生产环境（MySQL/PostgreSQL）应保持 {@code apex.events.outbox.enabled=true}，
 * 由基础设施层的 {@code DomainEventPublisherImpl} + Outbox 提供可靠投递。</p>
 *
 * @author Apex Team
 * @since 2026-10-09
 * @see DomainEventPublisher
 */
public class LocalDomainEventPublisher implements DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LocalDomainEventPublisher.class);

    /**
     * 本地发布领域事件。
     *
     * <p>仅记录事件摘要，不进行持久化，直接返回完成信号。</p>
     *
     * @param event 领域事件对象，不能为 null
     * @return 空 Mono，表示发布完成
     */
    @Override
    public Mono<Void> publish(AbstractDomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("领域事件不能为 null");
        }
        log.debug(
                "本地发布领域事件（未启用Outbox）: type={}, eventId={}, tenantId={}",
                event.getClass().getSimpleName(),
                event.getEventId(),
                event.getTenantId());
        return Mono.empty();
    }
}
