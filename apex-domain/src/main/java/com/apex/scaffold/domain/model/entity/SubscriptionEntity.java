package com.apex.scaffold.domain.model.entity;

import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 订阅聚合根实体
 *
 * <p>
 * 表示用户的会员订阅信息。
 * 支持VIP和SVIP等级，有明确的有效期。
 * </p>
 *
 * <h2>核心业务规则</h2>
 * <ul>
 *   <li>订阅有有效期限制</li>
 *   <li>可自动续费或手动续费</li>
 *   <li>过期后自动降级</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see BaseEntity
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SubscriptionEntity extends BaseEntity {

    /**
     * 序列化版本号
     */
    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 订阅等级
     *
     * @see SubscriptionLevel
     */
    private String level;

    /**
     * 开始时间
     */
    private LocalDateTime startAt;

    /**
     * 结束时间
     */
    private LocalDateTime endAt;

    /**
     * 订阅状态
     *
     * @see SubscriptionStatus
     */
    private String status;

    /** Sets userId. For persistence/construction use only. */
    public void setUserId(Long value) {
        this.userId = value;
    }

    /** Sets level. For persistence/construction use only. */
    public void setLevel(String value) {
        this.level = value;
    }

    /** Sets startAt. For persistence/construction use only. */
    public void setStartAt(LocalDateTime value) {
        this.startAt = value;
    }

    /** Sets endAt. For persistence/construction use only. */
    public void setEndAt(LocalDateTime value) {
        this.endAt = value;
    }

    /** Sets status. For persistence/construction use only. */
    public void setStatus(String value) {
        this.status = value;
    }

    /**
     * 默认构造函数
     */
    public SubscriptionEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建VIP订阅
     *
     * @param userId 用户ID
     * @param durationDays 订阅天数
     * @return 新创建的订阅实体
     */
    public static SubscriptionEntity createVipSubscription(Long userId, int durationDays) {
        LocalDateTime now = LocalDateTime.now();
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.userId = userId;
        entity.level = SubscriptionLevel.VIP.getCode();
        entity.startAt = now;
        entity.endAt = now.plusDays(durationDays);
        entity.status = SubscriptionStatus.ACTIVE.getCode();
        entity.setTimestamps();
        return entity;
    }

    /**
     * 静态工厂方法：创建SVIP订阅
     *
     * @param userId 用户ID
     * @param durationDays 订阅天数
     * @return 新创建的订阅实体
     */
    public static SubscriptionEntity createSvipSubscription(Long userId, int durationDays) {
        LocalDateTime now = LocalDateTime.now();
        SubscriptionEntity entity = new SubscriptionEntity();
        entity.userId = userId;
        entity.level = SubscriptionLevel.SVIP.getCode();
        entity.startAt = now;
        entity.endAt = now.plusDays(durationDays);
        entity.status = SubscriptionStatus.ACTIVE.getCode();
        entity.setTimestamps();
        return entity;
    }

    /**
     * 设置时间戳
     */
    private void setTimestamps() {
        LocalDateTime now = LocalDateTime.now();
        this.setCreateTime(now);
        this.setUpdateTime(now);
        this.setDeleted(LogicalDeletion.NOT_DELETED.getCode());
    }

    /**
     * 领域行为：续订
     *
     * @param durationDays 续订天数
     */
    public void renew(int durationDays) {
        LocalDateTime now = LocalDateTime.now();
        if (isExpired()) {
            this.startAt = now;
            this.endAt = now.plusDays(durationDays);
        } else {
            this.endAt = this.endAt.plusDays(durationDays);
        }
        this.status = SubscriptionStatus.ACTIVE.getCode();
        touch();
    }

    /**
     * 领域行为：取消订阅
     */
    public void cancel() {
        this.status = SubscriptionStatus.CANCELED.getCode();
        touch();
    }

    /**
     * 领域行为：检查是否有效
     *
     * @return true-有效，false-无效
     */
    public boolean isActive() {
        return SubscriptionStatus.ACTIVE.getCode().equals(status) && !isExpired();
    }

    /**
     * 领域行为：检查是否过期
     *
     * @return true-过期，false-未过期
     */
    public boolean isExpired() {
        return endAt != null && LocalDateTime.now().isAfter(endAt);
    }

    /**
     * 领域行为：标记为过期
     */
    public void markAsExpired() {
        this.status = SubscriptionStatus.EXPIRED.getCode();
        touch();
    }

    /**
     * 领域行为：升级到SVIP
     *
     * @param durationDays 升级后天数
     */
    public void upgradeToSvip(int durationDays) {
        this.level = SubscriptionLevel.SVIP.getCode();
        renew(durationDays);
    }

    /**
     * 订阅等级枚举
     */
    public enum SubscriptionLevel {
        /** VIP会员 */
        VIP("VIP"),
        /** SVIP超级会员 */
        SVIP("SVIP");

        private final String code;

        /**
         * 创建 {@code SubscriptionLevel} 实例。
         *
         * @param code 枚举编码
         */
        SubscriptionLevel(String code) {
            this.code = code;
        }

        /**
         * 获取{@code code}。
         * @return 字符串结果
         */
        public String getCode() {
            return code;
        }

        /**
         * 执行 {@code fromCode} 操作。
         * @param code 参数 {@code code}
         * @return 操作结果
         */
        public static SubscriptionLevel fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (SubscriptionLevel level : values()) {
                if (level.code.equals(code)) {
                    return level;
                }
            }
            return null;
        }
    }

    /**
     * 订阅状态枚举
     */
    public enum SubscriptionStatus {
        /** 生效中 */
        ACTIVE("ACTIVE"),
        /** 已过期 */
        EXPIRED("EXPIRED"),
        /** 已取消 */
        CANCELED("CANCELED");

        private final String code;

        /**
         * 创建 {@code SubscriptionStatus} 实例。
         *
         * @param code 枚举编码
         */
        SubscriptionStatus(String code) {
            this.code = code;
        }

        /**
         * 获取{@code code}。
         * @return 字符串结果
         */
        public String getCode() {
            return code;
        }

        /**
         * 执行 {@code fromCode} 操作。
         * @param code 参数 {@code code}
         * @return 操作结果
         */
        public static SubscriptionStatus fromCode(String code) {
            if (code == null) {
                return null;
            }
            for (SubscriptionStatus status : values()) {
                if (status.code.equals(code)) {
                    return status;
                }
            }
            return null;
        }
    }
}
