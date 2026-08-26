package com.apex.scaffold.domain.service;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import reactor.core.publisher.Mono;

/**
 * 领域事件发布器接口。
 *
 * <p>定义领域事件的发布方法规范，由基础设施层实现具体的事件总线机制。
 * 领域事件发布遵循DDD（领域驱动设计）的事件驱动架构模式，
 * 用于在领域层与其他边界（如应用服务、基础设施层）之间进行异步通信。</p>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li>解耦：领域服务不直接依赖外部系统，通过发布事件实现松耦合</li>
 *   <li>异步：事件发布后立即返回，由监听器异步处理</li>
 *   <li>最终一致性：事件消费者负责保证业务最终一致</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * domainEventPublisher.publish(new PhotoUploadedEvent(photoId, userId))
 *     .subscribe();
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see AbstractDomainEvent
 */
public interface DomainEventPublisher {
    /**
     * 发布领域事件。
     *
     * <p>将领域事件异步发送到事件总线，由已注册的相应监听器进行处理。
     * 发布调用立即返回，不阻塞调用线程。监听器的执行结果不影响发布方的业务流程。</p>
     *
     * <p>若事件总线不可用或所有监听器处理失败，发布操作仍返回成功信号
     * （Mono.empty()），遵循"发送即忘"的异步事件模式。</p>
     *
     * @param event 领域事件对象，不能为null
     * @return 发布完成信号（Mono.empty()），发布失败时返回错误信号
     * @throws IllegalArgumentException 若event为null
     */
    Mono<Void> publish(AbstractDomainEvent event);
}
