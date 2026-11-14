package com.apex.scaffold.infrastructure.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 领域事件发布器实现类（基于事务 Outbox）
 *
 * <p>默认实现先将领域事件持久化到 Outbox，由后台投递器进行有界重试和死信收口。
 * 标注为 {@code @Primary}，当应用中同时存在多个 {@link DomainEventPublisher} 实现时，
 * Spring 将优先注入本实例。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>先持久化</strong>：发布完成表示 Outbox 记录已经写入数据库</li>
 *   <li><strong>可靠投递</strong>：监听器失败会重试，超过阈值进入 DEAD</li>
 *   <li><strong>响应式适配</strong>：阻塞 JDBC 被调度到独立数据库 Scheduler</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see DomainEventPublisher
 * @see EnhancedDomainEventPublisherImpl
 */
@Primary
@Component
@RequiredArgsConstructor
@Slf4j
public class DomainEventPublisherImpl implements DomainEventPublisher {

    /** 领域事件 Outbox 存储。 */
    private final DomainEventOutboxStore outboxStore;

    /**
     * 发布领域事件。
     *
     * <p>写入成功后由 {@link DomainEventOutboxDispatcher} 异步投递。写入失败会通过
     * Mono 错误信号返回，调用方不得忽略。</p>
     *
     * @param event 领域事件对象，不能为 null
     */
    @Override
    public Mono<Void> publish(AbstractDomainEvent event) {
        return Mono.fromRunnable(() -> outboxStore.append(event))
                .subscribeOn(BlockingIoSchedulers.event())
                .doOnSuccess(ignored ->
                        log.debug("领域事件已写入Outbox: {}, ID: {}", event.getClass().getSimpleName(), event.getEventId()))
                .then();
    }
}
