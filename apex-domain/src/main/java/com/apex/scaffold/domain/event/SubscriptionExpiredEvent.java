package com.apex.scaffold.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import lombok.Getter;

/**
 * 订阅过期领域事件
 *
 * <p>当用户订阅到期时触发的领域事件，包含订阅的基本信息
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class SubscriptionExpiredEvent extends AbstractDomainEvent {

    /** 订阅 ID */
    private final Long subscriptionId;

    /** 用户 ID */
    private final Long userId;

    /** 订阅等级 */
    private final String level;

    /**
     * 创建订阅过期事件
     *
     * @param subscriptionId 订阅 ID
     * @param userId         用户 ID
     * @param level          订阅等级
     */
    public SubscriptionExpiredEvent(Long subscriptionId, Long userId, String level) {
        super("SUBSCRIPTION_EXPIRED", null);
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.level = level;
    }

    /**
     * 获取事件源ID。
     *
     * @return 订阅ID的字符串表示
     */
    @Override
    public String getSourceId() {
        return String.valueOf(subscriptionId);
    }
}
