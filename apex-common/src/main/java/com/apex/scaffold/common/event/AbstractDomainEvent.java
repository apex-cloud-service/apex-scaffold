package com.apex.scaffold.common.event;

import java.io.Serial;
import lombok.Getter;

/**
 * 领域事件抽象基类
 *
 * <p>所有领域事件的抽象基类，继承自 {@link BaseDomainEvent}。
 * 实现 {@link java.io.Serializable} 以支持事件序列化（如跨进程传递、事件存储）。
 * 与 {@code DomainEventBus} 兼容，所有事件处理链中的子类需确保可序列化。
 *
 * <h2>设计说明</h2>
 * <p>{@link BaseDomainEvent} 是所有事件的根基类，提供事件ID、时间戳、溯源ID等基本属性。
 * {@code AbstractDomainEvent} 在此基础上添加序列化支持，适用于需要序列化的业务事件。
 * 不需要序列化的事件可直接继承 {@link BaseDomainEvent}。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 * @see BaseDomainEvent
 */
@Getter
public abstract class AbstractDomainEvent extends BaseDomainEvent implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 构造函数，将事件类型和溯源ID委托给父类
     *
     * @param eventType 事件类型，用于区分不同的事件种类
     * @param traceId   溯源ID，用于分布式链路追踪
     */
    protected AbstractDomainEvent(String eventType, String traceId) {
        super(eventType, traceId);
    }

    /**
     * 构造函数，支持显式指定事件ID。
     *
     * <p>事件ID为空或空白时回退为随机 {@link java.util.UUID}；显式事件ID用于
     * 需要跨重试保持幂等的事件（参见 {@link BaseDomainEvent} 同名构造器）。</p>
     *
     * @param eventType 事件类型，用于区分不同的事件种类
     * @param traceId   溯源ID，用于分布式链路追踪
     * @param eventId   显式事件ID（可为空，为空时自动生成）
     */
    protected AbstractDomainEvent(String eventType, String traceId, String eventId) {
        super(eventType, traceId, eventId);
    }
}
