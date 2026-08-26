package com.apex.scaffold.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import lombok.Getter;

/**
 * 影子封禁触发领域事件
 *
 * <p>当用户达到影子封禁阈值时发布的领域事件
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class ShadowBanTriggeredEvent extends AbstractDomainEvent {

    /** 用户 ID */
    private final Long userId;

    /** 触发次数 */
    private final Integer triggerCount;

    /**
     * 创建影子封禁触发事件
     *
     * @param userId       用户 ID
     * @param triggerCount 触发次数
     */
    public ShadowBanTriggeredEvent(Long userId, Integer triggerCount) {
        super("SHADOW_BAN_TRIGGERED", null);
        this.userId = userId;
        this.triggerCount = triggerCount;
    }

    /**
     * 获取事件源ID。
     *
     * @return 用户ID的字符串表示
     */
    @Override
    public String getSourceId() {
        return String.valueOf(userId);
    }
}
