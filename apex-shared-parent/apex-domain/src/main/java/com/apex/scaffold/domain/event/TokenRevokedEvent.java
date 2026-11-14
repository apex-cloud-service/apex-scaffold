package com.apex.scaffold.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

/**
 * 令牌撤销事件
 *
 * <p>用户登出或管理员强制下线时，认证中心撤销该用户在指定租户下的全部令牌后发布此事件，
 * 用于通知各业务服务清理本地权限缓存与在线态副本，保证"登出传播"的最终一致性。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>发布方：auth-service（认证授权中心）</li>
 *   <li>订阅方：system、workflow、file 等业务服务</li>
 *   <li>用途：权限缓存失效广播，各服务收到后清理本地缓存的用户权限与在线态</li>
 * </ul>
 *
 * <p>事件信封字段由 {@link AbstractDomainEvent} 基类提供（eventId、occurredAt、traceId、
 * tenantId、traceId 等），本类仅携带撤销动作的业务负载（userId、tenantId、reason）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 * @see AbstractDomainEvent
 */
@Getter
public class TokenRevokedEvent extends AbstractDomainEvent {

    /** 事件类型：令牌撤销。 */
    private static final String EVENT_TYPE = "TOKEN_REVOKED";

    /** 被撤销令牌的用户 ID（事件源/聚合根 ID）。 */
    private final Long userId;

    /** 租户 ID，用于多租户隔离与投递时恢复租户上下文。 */
    private final String tenantId;

    /** 撤销原因（如 LOGOUT、ADMIN_KICK、PASSWORD_CHANGED），便于订阅方按需处理。 */
    private final String reason;

    /**
     * 创建令牌撤销事件。
     *
     * @param userId   被撤销令牌的用户 ID
     * @param tenantId 租户 ID
     * @param reason   撤销原因
     */
    @JsonCreator
    public TokenRevokedEvent(
            @JsonProperty("userId") Long userId,
            @JsonProperty("tenantId") String tenantId,
            @JsonProperty("reason") String reason) {
        super(EVENT_TYPE, null);
        this.userId = userId;
        this.tenantId = tenantId;
        this.reason = reason;
    }

    /**
     * 获取事件源 ID（聚合根 ID）。
     *
     * @return 用户 ID 的字符串表示；用户 ID 为空时返回 {@code null}
     */
    @Override
    public String getSourceId() {
        return userId == null ? null : String.valueOf(userId);
    }
}
