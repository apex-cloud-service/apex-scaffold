package com.apex.scaffold.common.event;

/**
 * 可跨服务分发的领域事件标记（方案 D5 主数据分发）。
 *
 * <p>实现该接口的领域事件在 Outbox 投递（进程内重放）之外，还会经
 * {@code DomainEventMqBridge} 以 {@code topic:tag} 形式发送到分布式事件
 * Topic（默认 {@code apex-domain-event}，tag = {@link #mqTag()}），供其他服务的
 * MQ 订阅方消费。未实现该接口的领域事件保持既有进程内语义，不跨服务广播。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public interface DistributedDomainEvent {

    /**
     * MQ 投递 tag（默认事件类型，订阅方以 selectorExpression 按需订阅）。
     *
     * @return 事件类型；非 {@link AbstractDomainEvent} 实现返回空串（桥接器跳过）
     */
    default String mqTag() {
        return this instanceof AbstractDomainEvent event ? event.getEventType() : "";
    }
}
