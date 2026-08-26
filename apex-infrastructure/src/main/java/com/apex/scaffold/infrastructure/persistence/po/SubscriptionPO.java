package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 订阅持久化对象。
 *
 * <p>映射数据库表 biz_subscription，记录用户的订阅信息，包括等级、有效期和状态。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_subscription")
public class SubscriptionPO extends BasePO {

    /**
     * 创建订阅持久化对象。
     */
    public SubscriptionPO() {}

    /** 用户ID。 */
    @TableField("user_id")
    private Long userId;

    /** 订阅等级（VIP/SVIP）。 */
    @TableField("level")
    private String level;

    /** 开始时间。 */
    @TableField("start_at")
    private LocalDateTime startAt;

    /** 结束时间。 */
    @TableField("end_at")
    private LocalDateTime endAt;

    /** 状态（ACTIVE/EXPIRED/CANCELED）。 */
    @TableField("status")
    private String status;
}
