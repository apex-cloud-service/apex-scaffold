package com.apex.scaffold.common.event;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
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
 * <h2>职责范围</h2>
 * <ul>
 *   <li>事件具有唯一标识（eventId）</li>
 *   <li>事件有创建时间戳</li>
 *   <li>事件可以有溯源ID（traceId）用于链路追踪</li>
 *   <li>事件可以标记为是否已处理（线程安全）</li>
 * </ul>
 *
 * <h2>线程安全性</h2>
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
    @JsonProperty("eventId")
    private String eventId;

    /** 事件创建时间 */
    @JsonProperty("createdAt")
    private LocalDateTime createdAt;

    /** 溯源ID，用于链路追踪 */
    @JsonProperty("traceId")
    private String traceId;

    /** 是否已处理（线程安全） */
    @JsonIgnore
    private final AtomicBoolean processed = new AtomicBoolean(false);

    /** 事件类型 */
    @JsonProperty("eventType")
    private String eventType;

    /**
     * 租户 ID（发布时写入，跨线程监听器从此读取，不再依赖 TenantContext ThreadLocal）。
     *
     * <p>非 final 以允许发布端在构造后通过 {@link #attachTenantContext(String)} 注入；
     * 正常业务代码不应调用 setter，只由事件发布器（如 EnhancedDomainEventPublisherImpl）
     * 在构造事件后即刻写入一次。</p>
     */
    private String tenantId;

    /**
     * 恢复租户字段（仅反序列化路径使用）.
     *
     * @param tenantId 原始租户
     */
    @JsonProperty("tenantId")
    private void setTenantIdOnDeserialize(String tenantId) {
        this.tenantId = tenantId;
    }

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
     * 把发布时的租户上下文注入事件对象。
     *
     * <p>应仅由发布器在发布前调用一次；监听器通过 {@code getTenantId()}
     * 读取，避免跨线程监听器访问 {@code TenantContext ThreadLocal}。</p>
     *
     * @param tenantId 当前发布请求的租户 ID（可 {@code null} 表示无租户）
     */
    public void attachTenantContext(String tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * 从持久化信封恢复事件元数据。
     *
     * <p>反序列化路径无法经过带参构造器时使用；正常构造不应调用。
     *
     * @param eventId   原始事件 ID；为空时保留当前值
     * @param createdAt 原始发生时间；为空时保留当前值
     * @param traceId   原始追踪号
     * @param eventType 事件类型；为空时保留当前值
     * @param tenantId  原始租户；非空时覆盖
     */
    protected void restoreEnvelope(
            String eventId, LocalDateTime createdAt, String traceId, String eventType, String tenantId) {
        if (eventId != null && !eventId.isBlank()) {
            this.eventId = eventId;
        }
        if (createdAt != null) {
            this.createdAt = createdAt;
        }
        if (traceId != null) {
            this.traceId = traceId;
        }
        if (eventType != null && !eventType.isBlank()) {
            this.eventType = eventType;
        }
        if (tenantId != null && !tenantId.isBlank()) {
            this.tenantId = tenantId;
        }
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
