package com.apex.scaffold.domain.enums;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 活跃度标签枚举。
 *
 * <p>
 * 根据用户最近一次登录时间与当前时间的间隔，将用户分为活跃和沉睡两类，
 * 不同类型用户在投票流程中享有不同的曝光系数。
 * </p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ActivityStatus {

    /**
     * 活跃：距最近一次登录 ≤ 3 天，系数 × 1.0.
     */
    ACTIVE(1.0),

    /**
     * 沉睡：距最近一次登录 > 3 天，系数 × 0.
     */
    INACTIVE(0.0);

    private final double coefficient;

    public static final int ACTIVE_THRESHOLD_DAYS = 3;

    /**
     * 创建 {@code ActivityStatus} 实例。
     * @param coefficient 参数 {@code coefficient}
     */
    ActivityStatus(double coefficient) {
        this.coefficient = coefficient;
    }

    /**
     * 获取当前活跃度对应的曝光系数.
     *
     * @return 曝光系数
     */
    public double getCoefficient() {
        return coefficient;
    }

    /**
     * 根据最近一次登录时间获取对应的活跃度标签.
     *
     * @param lastLoginTime 用户最近一次登录时间
     * @return 对应的活跃度枚举
     */
    public static ActivityStatus fromLastLoginTime(LocalDateTime lastLoginTime) {
        if (lastLoginTime == null) {
            return INACTIVE;
        }
        long daysBetween = ChronoUnit.DAYS.between(lastLoginTime, LocalDateTime.now());
        if (daysBetween <= ACTIVE_THRESHOLD_DAYS) {
            return ACTIVE;
        }
        return INACTIVE;
    }
}
