package com.apex.scaffold.domain.model.valueobject;

import com.apex.scaffold.domain.model.exception.TenantQuotaExceededException;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 租户配额值对象。
 *
 * <p>封装租户的资源配额上限与校验逻辑，支持用户数、存储量等维度的配额检查。
 * 值对象具有不可变性，一旦创建其值不能改变；配额变更需创建新实例。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code maxUsers} - 最大用户数</li>
 *   <li>{@code maxStorageMb} - 最大存储容量（MB）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Getter
@EqualsAndHashCode
public class TenantQuota {

    /** 用户数配额维度 */
    public static final String DIMENSION_USERS = "users";

    /** 存储配额维度 */
    public static final String DIMENSION_STORAGE = "storage";

    /** 最大用户数，null 表示不限制 */
    private final Integer maxUsers;

    /** 最大存储容量（MB），null 表示不限制 */
    private final Integer maxStorageMb;

    /**
     * 构造租户配额。
     *
     * @param maxUsers 最大用户数
     * @param maxStorageMb 最大存储容量（MB）
     */
    private TenantQuota(Integer maxUsers, Integer maxStorageMb) {
        this.maxUsers = maxUsers;
        this.maxStorageMb = maxStorageMb;
    }

    /**
     * 创建租户配额。
     *
     * @param maxUsers 最大用户数
     * @param maxStorageMb 最大存储容量（MB）
     * @return 租户配额实例
     */
    public static TenantQuota of(Integer maxUsers, Integer maxStorageMb) {
        return new TenantQuota(maxUsers, maxStorageMb);
    }

    /**
     * 创建不限制的租户配额。
     *
     * @return 不限制的配额实例
     */
    public static TenantQuota unlimited() {
        return new TenantQuota(null, null);
    }

    /**
     * 校验用户数配额。
     *
     * <p>当申请增量使使用量超过上限时抛出 {@link TenantQuotaExceededException}。
     * 上限为 null 表示不限制。</p>
     *
     * @param current 当前用户数
     * @param increment 申请增量
     * @throws TenantQuotaExceededException 当超出配额时抛出
     */
    public void assertUsers(long current, long increment) {
        if (maxUsers == null) {
            return;
        }
        if (current + increment > maxUsers) {
            throw new TenantQuotaExceededException(DIMENSION_USERS, current, increment, maxUsers);
        }
    }

    /**
     * 校验存储配额。
     *
     * @param current 当前存储量（MB）
     * @param increment 申请增量（MB）
     * @throws TenantQuotaExceededException 当超出配额时抛出
     */
    public void assertStorage(long current, long increment) {
        if (maxStorageMb == null) {
            return;
        }
        if (current + increment > maxStorageMb) {
            throw new TenantQuotaExceededException(DIMENSION_STORAGE, current, increment, maxStorageMb);
        }
    }
}
