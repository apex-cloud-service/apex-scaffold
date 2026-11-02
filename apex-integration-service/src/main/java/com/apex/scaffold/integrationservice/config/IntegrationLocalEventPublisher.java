package com.apex.scaffold.integrationservice.config;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * 集成服务轻量级领域事件发布器（进程内派发，不落库）。
 *
 * <p>实现语义与 auth 库 {@code LocalDomainEventPublisher} 一致：仅记录事件摘要后
 * 立即返回完成信号（发送即忘），避免在无 {@code sys_domain_event_outbox} 表的
 * {@code apex_integration} 库上写事件抛错。选型原因见 {@link IntegrationEventConfig}。</p>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 * @see DomainEventPublisher
 */
@Slf4j
public class IntegrationLocalEventPublisher implements DomainEventPublisher {

    /**
     * 进程内发布领域事件（不持久化）。
     *
     * @param event 领域事件对象，不能为 null
     * @return 完成信号
     */
    @Override
    public Mono<Void> publish(AbstractDomainEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("领域事件不能为 null");
        }
        log.debug(
                "进程内发布领域事件（未启用Outbox）: type={}, eventId={}, tenantId={}",
                event.getClass().getSimpleName(),
                event.getEventId(),
                event.getTenantId());
        return Mono.empty();
    }
}
