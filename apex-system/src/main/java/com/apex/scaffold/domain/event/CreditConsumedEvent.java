package com.apex.scaffold.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import lombok.Getter;

/**
 * 积分消费领域事件
 *
 * <p>当用户消费积分使用某项功能时发布的事件，记录消费金额及剩余余额
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class CreditConsumedEvent extends AbstractDomainEvent {

    /** 用户 ID */
    private final Long userId;

    /** 消费积分数量 */
    private final Long amount;

    /** 消费后剩余积分余额 */
    private final Long remainingBalance;

    /** 消费场景（如：GENERATE_REPORT, START_CHAT 等） */
    private final String scene;

    /**
     * 创建积分消费事件
     *
     * @param userId           用户 ID
     * @param amount           消费积分数量
     * @param remainingBalance 消费后剩余积分余额
     * @param scene            消费场景
     */
    public CreditConsumedEvent(Long userId, Long amount, Long remainingBalance, String scene) {
        super("CREDIT_CONSUMED", null);
        this.userId = userId;
        this.amount = amount;
        this.remainingBalance = remainingBalance;
        this.scene = scene;
    }

    /**
     * 获取事件源ID。
     *
     * @return 用户ID的字符串表示
     * @since 1.0.0
     */
    @Override
    public String getSourceId() {
        return String.valueOf(userId);
    }
}
