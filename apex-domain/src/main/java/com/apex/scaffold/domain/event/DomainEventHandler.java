package com.apex.scaffold.domain.event;

import com.apex.scaffold.common.event.BaseDomainEvent;
import reactor.core.publisher.Mono;

/**
 * 领域事件处理器接口。
 *
 * <p>定义处理特定领域事件的接口。
 * 事件处理器负责处理领域事件，执行相应的业务逻辑。</p>
 *
 * <h2>处理器实现规范</h2>
 * <ul>
 *   <li>每个处理器应该只处理一种类型的事件</li>
 *   <li>处理器应该是无状态的</li>
 *   <li>处理器不应该抛出异常，如果需要异常处理应该在内部处理</li>
 *   <li>处理器应该支持异步处理</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * @Component
 * public class UserRegisteredEventHandler implements DomainEventHandler<UserRegisteredEvent> {
 *
 *     @Override
 *     public Mono<Void> handle(UserRegisteredEvent event) {
 *         // 处理用户注册事件
 *         return Mono.empty();
 *     }
 *
 *     @Override
 *     public int getOrder() {
 *         return 1; // 执行顺序
 *     }
 * }
 * }</pre>
 *
 * @param <T> 事件类型
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface DomainEventHandler<T extends BaseDomainEvent> {

    /**
     * 处理领域事件。
     *
     * @param event 领域事件
     * @return Mono&lt;Void&gt;
     */
    Mono<Void> handle(T event);

    /**
     * 获取处理器的执行顺序。
     *
     * <p>数值越小，优先级越高。默认值为100。</p>
     *
     * @return 执行顺序
     */
    default int getOrder() {
        return 100;
    }

    /**
     * 获取处理器名称。
     *
     * @return 处理器名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 判断处理器是否支持该事件类型。
     *
     * @param eventType 事件类型
     * @return true-支持，false-不支持
     */
    default boolean supports(Class<? extends BaseDomainEvent> eventType) {
        return getEventType().isAssignableFrom(eventType);
    }

    /**
     * 获取处理的事件类型。
     *
     * @return 事件类型
     */
    Class<T> getEventType();
}
