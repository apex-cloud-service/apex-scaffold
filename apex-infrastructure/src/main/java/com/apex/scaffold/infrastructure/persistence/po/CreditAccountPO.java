package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 算力账户持久化对象。
 *
 * <p>映射数据库表 biz_credit_account，记录用户的算力余额、登录奖励和收藏积分信息，支持乐观锁机制。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("biz_credit_account")
public class CreditAccountPO extends BasePO {

    /**
     * 创建算力账户持久化对象。
     */
    public CreditAccountPO() {}

    /** 用户ID。 */
    @TableField("user_id")
    private Long userId;

    /** 余额。 */
    @TableField("balance")
    private Long balance;

    /** 今日登录奖励已领取。 */
    @TableField("today_login_bonus")
    private Boolean todayLoginBonus;

    /** 当日收藏累计获得积分（v2.0日上限200）。 */
    @TableField("daily_favorite_total")
    private Long dailyFavoriteTotal;

    /** 收藏积分统计日期。 */
    @TableField("daily_favorite_date")
    private LocalDate dailyFavoriteDate;
}
