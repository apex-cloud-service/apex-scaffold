package com.apex.scaffold.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 订阅激活领域事件
 *
 * <p>当用户订阅变为活跃状态时触发的领域事件，包含订阅的基本信息及有效期
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class SubscriptionActivatedEvent extends AbstractDomainEvent {

    /** 订阅 ID */
    private final Long subscriptionId;

    /** 用户 ID */
    private final Long userId;

    /** 订阅等级 */
    private final String level;

    /** 订阅开始时间 */
    private final LocalDateTime startAt;

    /** 订阅结束时间 */
    private final LocalDateTime endAt;

    /**
     * 创建订阅激活事件
     *
     * @param subscriptionId 订阅 ID
     * @param userId         用户 ID
     * @param level          订阅等级
     * @param startAt        订阅开始时间
     * @param endAt          订阅结束时间
     */
    public SubscriptionActivatedEvent(
            Long subscriptionId, Long userId, String level, LocalDateTime startAt, LocalDateTime endAt) {
        super("SUBSCRIPTION_ACTIVATED", null);
        this.subscriptionId = subscriptionId;
        this.userId = userId;
        this.level = level;
        this.startAt = startAt;
        this.endAt = endAt;
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
