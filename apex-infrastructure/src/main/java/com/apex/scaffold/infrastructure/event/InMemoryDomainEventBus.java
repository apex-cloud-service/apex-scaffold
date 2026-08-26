package com.apex.scaffold.infrastructure.event;

import com.apex.scaffold.common.event.BaseDomainEvent;
import com.apex.scaffold.domain.event.DomainEventBus;
import com.apex.scaffold.domain.event.DomainEventHandler;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 领域事件总线实现。
 *
 * <p>基于内存的领域事件总线实现，支持同步和异步事件发布。
 * 事件处理器通过订阅机制注册，事件发布后会通知所有订阅的处理器。</p>
 *
 * <h2>特性</h2>
 * <ul>
 *   <li>线程安全的订阅管理</li>
 *   <li>支持同步和异步事件处理</li>
 *   <li>支持事件处理器的优先级排序</li>
 *   <li>支持事件溯源追踪</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Component
public class InMemoryDomainEventBus implements DomainEventBus {
    private static final Logger log = LoggerFactory.getLogger(InMemoryDomainEventBus.class);

    /** 事件处理器注册表 */
    private final Map<Class<? extends BaseDomainEvent>, List<DomainEventHandler<? extends BaseDomainEvent>>> handlers =
            new ConcurrentHashMap<>();

    /** 待处理事件计数器 */
    private volatile long pendingEventCount = 0;

    /**
     * 异步发布领域事件。
     *
     * <p>事件以异步方式分发给所有已注册的处理器，按优先级排序后依次执行。
     * 发布完成后事件标记为已处理。</p>
     *
     * @param event 领域事件
     * @return 空 Mono，表示发布完成
     */
    @Override
    public Mono<Void> publish(BaseDomainEvent event) {
        pendingEventCount++;
        return Mono.defer(() -> {
            log.debug("Publishing event: {} (eventId={})", event.getEventName(), event.getEventId());
            return processEventAsync(event)
                    .doOnSuccess(v -> {
                        pendingEventCount--;
                        event.markAsProcessed();
                        log.debug("Event processed: {} (eventId={})", event.getEventName(), event.getEventId());
                    })
                    .doOnError(e -> {
                        pendingEventCount--;
                        log.error(
                                "Failed to process event: {} (eventId={}), error: {}",
                                event.getEventName(),
                                event.getEventId(),
                                e.getMessage(),
                                e);
                    });
        });
    }

    /**
     * 批量异步发布领域事件。
     *
     * <p>使用 Flux 依次调用 {@link #publish(BaseDomainEvent)} 批量发布事件。</p>
     *
     * @param events 领域事件列表
     * @return 空 Mono，表示全部发布完成
     */
    @Override
    public Mono<Void> publishAll(List<BaseDomainEvent> events) {
        return Flux.fromIterable(events).flatMap(this::publish).then();
    }

    /**
     * 同步发布领域事件。
     *
     * <p>事件以同步方式（concatMap）分发给所有已注册的处理器，保证处理器按注册顺序依次执行。
     * 适用于需要事务一致性保证的场景。</p>
     *
     * @param event 领域事件
     * @return 空 Mono，表示发布完成
     */
    @Override
    public Mono<Void> publishSync(BaseDomainEvent event) {
        pendingEventCount++;
        return processEventSync(event)
                .doOnSuccess(v -> {
                    pendingEventCount--;
                    event.markAsProcessed();
                    log.debug(
                            "Event processed synchronously: {} (eventId={})", event.getEventName(), event.getEventId());
                })
                .doOnError(e -> {
                    pendingEventCount--;
                    log.error(
                            "Failed to process event synchronously: {} (eventId={}), error: {}",
                            event.getEventName(),
                            event.getEventId(),
                            e.getMessage(),
                            e);
                });
    }

    /**
     * 注册事件处理器。
     *
     * <p>将指定类型事件的处理器注册到总线中，相同事件类型可注册多个处理器。</p>
     *
     * @param <T>       事件类型
     * @param eventType 事件类型 Class
     * @param handler   事件处理器
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BaseDomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        handlers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
        log.info("Subscribed handler: {} for event type: {}", handler.getName(), eventType.getSimpleName());
    }

    /**
     * 取消注册事件处理器。
     *
     * <p>从指定事件类型的处理器列表中移除目标处理器。</p>
     *
     * @param <T>       事件类型
     * @param eventType 事件类型 Class
     * @param handler   要移除的事件处理器
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BaseDomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler) {
        List<DomainEventHandler<? extends BaseDomainEvent>> handlerList = handlers.get(eventType);
        if (handlerList != null) {
            boolean removed = handlerList.remove(handler);
            if (removed) {
                log.info("Unsubscribed handler: {} from event type: {}", handler.getName(), eventType.getSimpleName());
            }
        }
    }

    /**
     * 获取指定事件类型的所有处理器。
     *
     * <p>按优先级（order）升序排列后返回。</p>
     *
     * @param <T>       事件类型
     * @param eventType 事件类型 Class
     * @return 按优先级排序的处理器列表，无处理器时返回空列表
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BaseDomainEvent> List<DomainEventHandler<T>> getHandlers(Class<T> eventType) {
        List<DomainEventHandler<? extends BaseDomainEvent>> handlersList = handlers.get(eventType);
        if (handlersList == null) {
            return List.of();
        }
        return handlersList.stream()
                .map(h -> (DomainEventHandler<T>) h)
                .sorted((h1, h2) -> Integer.compare(h1.getOrder(), h2.getOrder()))
                .collect(Collectors.toList());
    }

    /** {@inheritDoc} */
    @Override
    public boolean hasHandlers(Class<? extends BaseDomainEvent> eventType) {
        List<DomainEventHandler<? extends BaseDomainEvent>> handlersList = handlers.get(eventType);
        return handlersList != null && !handlersList.isEmpty();
    }

    /**
     * 获取当前待处理事件数量。
     *
     * @return 待处理事件数
     */
    @Override
    public long getPendingEventCount() {
        return pendingEventCount;
    }

    /** {@inheritDoc} */
    @Override
    public void shutdown() {
        log.info("Shutting down event bus...");
        handlers.clear();
        pendingEventCount = 0;
        log.info("Event bus shut down completed");
    }

    /**
     * 异步处理事件。
     *
     * @param event 领域事件
     * @return Mono&lt;Void&gt;
     */
    @SuppressWarnings("unchecked")
    private Mono<Void> processEventAsync(BaseDomainEvent event) {
        List<DomainEventHandler<? extends BaseDomainEvent>> handlersList = handlers.get(event.getClass());
        if (handlersList == null || handlersList.isEmpty()) {
            log.debug("No handlers found for event: {}", event.getEventName());
            return Mono.empty();
        }

        List<DomainEventHandler<? extends BaseDomainEvent>> sortedHandlers = handlersList.stream()
                .sorted((h1, h2) -> Integer.compare(h1.getOrder(), h2.getOrder()))
                .collect(Collectors.toList());

        return Flux.fromIterable(sortedHandlers)
                .flatMap(handler -> {
                    try {
                        return ((DomainEventHandler<BaseDomainEvent>) handler).handle(event);
                    } catch (Exception e) {
                        log.error(
                                "Error invoking handler: {} for event: {}", handler.getName(), event.getEventName(), e);
                        return Mono.empty();
                    }
                })
                .then();
    }

    /**
     * 同步处理事件。
     *
     * @param event 领域事件
     * @return Mono&lt;Void&gt;
     */
    @SuppressWarnings("unchecked")
    private Mono<Void> processEventSync(BaseDomainEvent event) {
        List<DomainEventHandler<? extends BaseDomainEvent>> handlersList = handlers.get(event.getClass());
        if (handlersList == null || handlersList.isEmpty()) {
            log.debug("No handlers found for event: {}", event.getEventName());
            return Mono.empty();
        }

        List<DomainEventHandler<? extends BaseDomainEvent>> sortedHandlers = handlersList.stream()
                .sorted((h1, h2) -> Integer.compare(h1.getOrder(), h2.getOrder()))
                .collect(Collectors.toList());

        return Flux.fromIterable(sortedHandlers)
                .concatMap(handler -> {
                    try {
                        return ((DomainEventHandler<BaseDomainEvent>) handler).handle(event);
                    } catch (Exception e) {
                        log.error(
                                "Error invoking handler: {} for event: {}", handler.getName(), event.getEventName(), e);
                        return Mono.empty();
                    }
                })
                .then();
    }
}
