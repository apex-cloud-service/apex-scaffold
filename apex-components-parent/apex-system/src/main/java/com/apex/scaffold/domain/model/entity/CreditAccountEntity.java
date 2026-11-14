package com.apex.scaffold.domain.model.entity;

import java.io.Serial;
import java.time.LocalDate;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * CreditAccount领域实体 v2.1
 *
 * <p>积分算力账户，记录用户算力余额、每日登录奖励领取状态、
 * 以及收藏每日积分上限控制。支持乐观锁机制保证并发安全。</p>
 *
 * <h2>线程安全性</h2>
 * <p>使用乐观锁机制（version字段）防止并发更新冲突。
 * 在更新前检查版本号，更新时使用CAS操作确保数据一致性。</p>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 */
@Getter
@lombok.Setter
@lombok.NoArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class CreditAccountEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 乐观锁版本号
     *
     * <p>用于并发控制，每次更新时自动递增。
     * 在高并发场景下，乐观锁可有效防止数据不一致问题。</p>
     *
     * <p><strong>使用方式</strong></p>
     * <ul>
     *   <li>查询时获取当前version值</li>
     *   <li>更新时携带version值</li>
     *   <li>数据库使用WHERE条件校验version</li>
     *   <li>更新成功后version自动+1</li>
     * </ul>
     */
    private Long version = 0L;

    /**
     * 用户ID
     *
     * <p>关联的用户主键ID，作为外键关联用户表。</p>
     */
    private Long userId;

    /**
     * 积分余额
     *
     * <p>用户当前的积分算力余额，上限为2000。</p>
     *
     */
    private Long balance;

    /**
     * 今日登录奖励领取状态
     *
     * <p>true-已领取今日登录奖励，false-未领取</p>
     */
    private Boolean todayLoginBonus = false;

    /**
     * 当日收藏累计获得积分
     *
     * <p>记录当天通过收藏获得的积分总数，用于控制日上限。</p>
     *
     */
    private Long dailyFavoriteTotal = 0L;

    /**
     * 收藏积分统计日期
     *
     * <p>记录每日收藏统计的日期，用于判断是否需要重置统计。</p>
     */
    private LocalDate dailyFavoriteDate;

    /**
     * 静态工厂方法：创建新积分账户
     *
     * <p>创建初始状态的积分账户，余额为0，版本号为0。</p>
     *
     * @param userId 用户ID
     * @return 新创建的积分账户实体
     */
    public static CreditAccountEntity create(Long userId) {
        CreditAccountEntity account = new CreditAccountEntity();
        account.setUserId(userId);
        account.setBalance(0L);
        account.setVersion(0L);
        account.setTodayLoginBonus(false);
        account.setDailyFavoriteTotal(0L);
        return account;
    }

    /**
     * 增加版本号
     *
     * <p>在更新操作完成后调用，递增版本号用于乐观锁校验。</p>
     */
    public void incrementVersion() {
        this.version = this.version != null ? this.version + 1 : 1;
    }
}
