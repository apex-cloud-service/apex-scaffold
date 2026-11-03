package com.apex.scaffold.contract.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.common.event.DistributedDomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 用户主数据变更事件（跨服务契约，方案 D5）.
 *
 * <p>由 system 在用户主数据创建/更新成功后经既有领域事件通道（Outbox + MQ）发布，
 * 用于通知各订阅方"某用户主数据已发生变化"。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>发布方：apex-system-service（{@code UserService#create} / {@code UserService#update}）</li>
 *   <li>订阅方：需要本地缓存失效或主数据投影的业务服务（file、office 等）</li>
 *   <li>Topic：{@code apex-domain-event}（由 {@code apex.mq.domain-event-topic} 配置）；
 *       实现 {@link DistributedDomainEvent} 标记，由 {@code DomainEventMqBridge} 在 Outbox
 *       重放点以 {@code topic:tag} 异步广播，tag 为事件类型</li>
 *   <li>负载仅含非敏感标识字段（用户 ID 与动作类型），不含手机号、姓名等个人数据</li>
 * </ul>
 *
 * <p>事件信封字段由 {@link AbstractDomainEvent} 基类提供（eventId、createdAt、traceId、tenantId 等），
 * 本类仅携带变更动作的业务负载。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 * @see AbstractDomainEvent
 */
@Getter
public class UserChangedEvent extends AbstractDomainEvent implements DistributedDomainEvent {

    /** 动作：用户创建。 */
    public static final String ACTION_CREATED = "CREATED";

    /** 动作：用户更新。 */
    public static final String ACTION_UPDATED = "UPDATED";

    /** 事件类型：用户主数据变更。 */
    private static final String EVENT_TYPE = "USER_CHANGED";

    /** 用户 ID（事件源/聚合根 ID）。 */
    private final Long userId;

    /** 变更动作：{@link #ACTION_CREATED} / {@link #ACTION_UPDATED}。 */
    private final String action;

    /**
     * 创建用户主数据变更事件。
     *
     * @param userId 用户 ID
     * @param action 变更动作
     */
    @JsonCreator
    public UserChangedEvent(@JsonProperty("userId") Long userId, @JsonProperty("action") String action) {
        super(EVENT_TYPE, null);
        this.userId = userId;
        this.action = action;
    }

    /**
     * 获取事件源 ID（聚合根 ID）。
     *
     * @return 用户 ID 的字符串表示；为空时返回 {@code null}
     */
    @Override
    public String getSourceId() {
        return userId == null ? null : String.valueOf(userId);
    }
}
