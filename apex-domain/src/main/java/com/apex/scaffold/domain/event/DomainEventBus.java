package com.apex.scaffold.domain.event;

import com.apex.scaffold.common.event.BaseDomainEvent;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 领域事件总线接口。
 *
 * <p>提供领域事件的发布、订阅和处理能力。
 * 事件总线是领域层的核心组件，负责在领域层内部传递事件。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>同步处理：事件发布后立即同步执行所有处理器</li>
 *   <li>异步处理：事件发布后放入队列，异步执行处理器</li>
 *   <li>事务性发布：事件在事务提交后才发布</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface DomainEventBus {

    /**
     * 发布领域事件（异步）。
     *
     * @param event 领域事件
     */
    Mono<Void> publish(BaseDomainEvent event);

    /**
     * 发布多个领域事件（异步）。
     *
     * @param events 领域事件列表
     */
    Mono<Void> publishAll(List<BaseDomainEvent> events);

    /**
     * 发布领域事件（同步）。
     *
     * @param event 领域事件
     */
    Mono<Void> publishSync(BaseDomainEvent event);

    /**
     * 订阅特定类型的事件。
     *
     * @param eventType 事件类型
     * @param handler   事件处理器
     * @param <T>       事件类型泛型
     */
    <T extends BaseDomainEvent> void subscribe(Class<T> eventType, DomainEventHandler<T> handler);

    /**
     * 取消订阅特定类型的事件。
     *
     * @param eventType 事件类型
     * @param handler   事件处理器
     * @param <T>       事件类型泛型
     */
    <T extends BaseDomainEvent> void unsubscribe(Class<T> eventType, DomainEventHandler<T> handler);

    /**
     * 获取特定类型的事件处理器列表。
     *
     * @param eventType 事件类型
     * @param <T>       事件类型泛型
     * @return 事件处理器列表
     */
    <T extends BaseDomainEvent> List<DomainEventHandler<T>> getHandlers(Class<T> eventType);

    /**
     * 检查是否有特定类型的事件处理器。
     *
     * @param eventType 事件类型
     * @return true-有处理器，false-无处理器
     */
    boolean hasHandlers(Class<? extends BaseDomainEvent> eventType);

    /**
     * 获取已发布但未处理的事件数量。
     *
     * @return 未处理事件数量
     */
    long getPendingEventCount();

    /**
     * 关闭事件总线，释放资源。
     */
    void shutdown();
}
