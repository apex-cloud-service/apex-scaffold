package com.apex.scaffold.common.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Getter;

/**
 * 领域事件抽象基类。
 *
 * <p>所有领域事件的基类，提供事件的基本属性和方法。
 * 领域事件用于在领域层内部或跨层传递领域发生的重要业务事件。</p>
 *
 * <h2>事件特性</h2>
 * <ul>
 *   <li>事件具有唯一标识（eventId）</li>
 *   <li>事件有创建时间戳</li>
 *   <li>事件可以有溯源ID（traceId）用于链路追踪</li>
 *   <li>事件可以标记为是否已处理（线程安全）</li>
 * </ul>
 *
 * <h2>线程安全</h2>
 * <p>{@code processed} 字段使用 {@link AtomicBoolean} 保证多线程环境下的原子操作，
 * 避免事件总线中的竞态条件。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public abstract class BaseDomainEvent {

    /** 事件唯一标识 */
    @JsonIgnore
    private final String eventId;

    /** 事件创建时间 */
    @JsonIgnore
    private final LocalDateTime createdAt;

    /** 溯源ID，用于链路追踪 */
    @JsonIgnore
    private final String traceId;

    /** 是否已处理（线程安全） */
    @JsonIgnore
    private final AtomicBoolean processed = new AtomicBoolean(false);

    /** 事件类型 */
    @JsonIgnore
    private final String eventType;

    /**
     * 构造函数，自动生成事件ID和时间戳。
     *
     * @param eventType 事件类型
     * @param traceId   溯源ID（可为空）
     */
    protected BaseDomainEvent(String eventType, String traceId) {
        this(eventType, traceId, null);
    }

    /**
     * 构造函数，支持显式指定事件ID。
     *
     * <p>事件ID为空或空白时回退为随机 {@link UUID}。显式事件ID用于需要跨重试
     * 保持幂等的事件（如 {@code photo-ingestion:{photoId}:v1}），借助 Outbox 的
     * {@code uk_outbox_event_id} 唯一约束避免同一业务动作重复投递。</p>
     *
     * @param eventType 事件类型
     * @param traceId   溯源ID（可为空）
     * @param eventId   显式事件ID（可为空，为空时自动生成）
     */
    protected BaseDomainEvent(String eventType, String traceId, String eventId) {
        this.eventId = eventId == null || eventId.isBlank() ? UUID.randomUUID().toString() : eventId;
        this.createdAt = LocalDateTime.now();
        this.traceId = traceId;
        this.eventType = eventType;
    }

    /**
     * 构造函数，自动生成事件ID、时间戳和溯源ID。
     *
     * @param eventType 事件类型
     */
    protected BaseDomainEvent(String eventType) {
        this(eventType, UUID.randomUUID().toString());
    }

    /**
     * 标记事件为已处理。
     *
     * <p>使用 {@link AtomicBoolean#compareAndSet} 保证线程安全，
     * 只有第一个调用此方法的线程能够成功将事件标记为已处理。</p>
     */
    public void markAsProcessed() {
        processed.compareAndSet(false, true);
    }

    /**
     * 判断事件是否已过期。
     *
     * <p>根据当前系统时间判断事件自创建时间起是否已超过指定分钟数。
     * 这是 {@link #isExpired(int, LocalDateTime)} 的便捷方法，使用 {@link LocalDateTime#now()} 作为参考时间。</p>
     *
     * @param minutes 过期时间（分钟）
     * @return true-已过期，false-未过期
     */
    public boolean isExpired(int minutes) {
        return isExpired(minutes, LocalDateTime.now());
    }

    /**
     * 判断事件是否已过期（可注入参考时间）。
     *
     * <p>根据指定的参考时间判断事件自创建时间起是否已超过指定分钟数。
     * 该方法支持注入 {@code referenceTime} 以便进行单元测试。</p>
     *
     * @param minutes       过期时间（分钟）
     * @param referenceTime 参考时间（通常为 {@code LocalDateTime.now()}）
     * @return true-已过期，false-未过期
     */
    public boolean isExpired(int minutes, LocalDateTime referenceTime) {
        return createdAt.plusMinutes(minutes).isBefore(referenceTime);
    }

    /**
     * 判断事件是否已处理。
     *
     * @return true-已处理，false-未处理
     */
    public boolean isProcessed() {
        return processed.get();
    }

    /**
     * 获取事件源（触发事件的聚合根ID）。
     *
     * @return 事件源ID
     */
    public abstract String getSourceId();

    /**
     * 获取事件名称。
     *
     * @return 事件名称
     */
    public String getEventName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 返回领域事件的字符串表示。
     *
     * @return 包含事件ID、事件类型和发生时间的事件字符串
     */
    @Override
    public String toString() {
        return String.format(
                "BaseDomainEvent{eventId='%s', eventType='%s', sourceId='%s', createdAt=%s, processed=%s}",
                eventId, eventType, getSourceId(), createdAt, processed.get());
    }
}
