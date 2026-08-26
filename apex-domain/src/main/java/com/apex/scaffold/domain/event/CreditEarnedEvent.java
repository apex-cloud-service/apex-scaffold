package com.apex.scaffold.domain.event;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import lombok.Getter;

/**
 * 积分赚取领域事件
 *
 * <p>当用户通过签到、完成任务等方式赚取积分时发布的事件
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class CreditEarnedEvent extends AbstractDomainEvent {

    /** 用户 ID */
    private final Long userId;

    /** 赚取积分数量 */
    private final Long amount;

    /** 积分总余额 */
    private final Long totalBalance;

    /** 积分来源（如：DAILY_CHECKIN, COMPLETE_PROFILE 等） */
    private final String source;

    /**
     * 创建积分赚取事件
     *
     * @param userId       用户 ID
     * @param amount       赚取积分数量
     * @param totalBalance 积分总余额
     * @param source       积分来源
     */
    public CreditEarnedEvent(Long userId, Long amount, Long totalBalance, String source) {
        super("CREDIT_EARNED", null);
        this.userId = userId;
        this.amount = amount;
        this.totalBalance = totalBalance;
        this.source = source;
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
