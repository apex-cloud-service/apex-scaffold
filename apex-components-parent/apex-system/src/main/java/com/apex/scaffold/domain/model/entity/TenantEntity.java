package com.apex.scaffold.domain.model.entity;

import com.apex.scaffold.domain.model.exception.TenantLifecycleException;
import com.apex.scaffold.domain.model.exception.TenantQuotaExceededException;
import com.apex.scaffold.domain.model.valueobject.TenantQuota;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * 租户聚合根实体。
 *
 * <p>表示 SaaS 或私有化多组织场景下的租户基础模型，承载租户基础信息、
 * 状态生命周期与配额约束。租户是数据隔离的顶层边界，所有业务数据均归属租户。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@link TenantStatus#ACTIVE} - 正常，可创建业务数据</li>
 *   <li>{@link TenantStatus#DISABLED} - 停用，运营手动暂停，可重新启用</li>
 *   <li>{@link TenantStatus#FROZEN} - 冻结，平台强制暂停（如欠费），可解冻</li>
 *   <li>{@link TenantStatus#DEACTIVATED} - 注销，已申请退出，可归档</li>
 *   <li>{@link TenantStatus#ARCHIVED} - 归档（终态），数据保留但任何迁移被拒绝</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>停用、冻结、注销、归档状态的租户不得创建新业务数据</li>
 *   <li>非正常状态的租户不允许任何配额增长</li>
 *   <li>归档为终态，任何状态迁移均被拒绝</li>
 *   <li>租户编码全局唯一，由仓储层校验</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 * @see TenantQuota
 */
@Getter
@lombok.Setter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class TenantEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 租户编码（全局唯一，作为 TenantContext 的租户 ID） */
    private String tenantCode;

    /** 租户名称 */
    private String tenantName;

    /** 租户管理员用户 ID */
    private String adminUserId;

    /** 套餐标识 */
    private String packageCode;

    /** 有效期至（null 表示长期有效） */
    private LocalDateTime expireTime;

    /** 租户状态 */
    private TenantStatus status;

    /** 租户配额 */
    private TenantQuota quota;

    /** 备注 */
    private String remark;

    /**
     * 默认构造函数。
     *
     * <p>仅供持久层框架或静态工厂 {@link #create(String, String, String, TenantQuota)} 使用，
     * 业务代码应优先使用静态工厂方法创建租户。</p>
     */
    public TenantEntity() {
        super();
    }

    /**
     * 静态工厂方法：创建租户。
     *
     * <p>新建租户默认为 {@link TenantStatus#ACTIVE}，可立即创建业务数据。</p>
     *
     * @param tenantCode 租户编码（非空，全局唯一）
     * @param tenantName 租户名称
     * @param adminUserId 租户管理员用户 ID
     * @param quota 租户配额
     * @return 新创建的租户实体
     * @throws IllegalArgumentException 当租户编码为空时抛出
     */
    public static TenantEntity create(String tenantCode, String tenantName, String adminUserId, TenantQuota quota) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("租户编码不能为空");
        }
        TenantEntity entity = new TenantEntity();
        entity.tenantCode = tenantCode;
        entity.tenantName = tenantName;
        entity.adminUserId = adminUserId;
        entity.quota = quota == null ? TenantQuota.unlimited() : quota;
        entity.status = TenantStatus.ACTIVE;
        entity.initTimestamps();
        return entity;
    }

    /**
     * 领域行为：判断当前租户是否可创建业务数据。
     *
     * <p>仅 {@link TenantStatus#ACTIVE} 且未过有效期的租户允许创建新业务数据，
     * 停用、冻结、注销和归档状态均拒绝；已配置有效期且当前时间已越过有效期的租户，
     * 即使状态仍为 ACTIVE 也拒绝，续期（回写 {@code expireTime}）后自动恢复。
     *
     * @return true-可创建业务数据，false-不可创建
     */
    public boolean canCreateBusinessData() {
        return status == TenantStatus.ACTIVE && !expired();
    }

    /**
     * 领域行为：判断当前租户是否已过有效期。
     *
     * <p>有效期为 {@code null} 表示长期有效；已配置有效期的租户在当前时间晚于
     * {@code expireTime} 后视为过期。到期只阻止新增业务数据与配额增长，
     * 不隐式改变租户状态，也不自动清理既有数据。
     *
     * @return true-已过期，false-未过期或长期有效
     */
    public boolean expired() {
        return expireTime != null && expireTime.isBefore(LocalDateTime.now());
    }

    /**
     * 领域行为：停用租户。
     *
     * <p>仅 {@link TenantStatus#ACTIVE} 可停用。</p>
     *
     * @throws TenantLifecycleException 当当前状态不允许停用时抛出
     */
    public void disable() {
        requireStatus(TenantStatus.ACTIVE, "disable");
        this.status = TenantStatus.DISABLED;
        touch();
    }

    /**
     * 领域行为：启用租户。
     *
     * <p>仅 {@link TenantStatus#DISABLED} 可重新启用。</p>
     *
     * @throws TenantLifecycleException 当当前状态不允许启用时抛出
     */
    public void enable() {
        requireStatus(TenantStatus.DISABLED, "enable");
        this.status = TenantStatus.ACTIVE;
        touch();
    }

    /**
     * 领域行为：冻结租户。
     *
     * <p>仅 {@link TenantStatus#ACTIVE} 可冻结。</p>
     *
     * @throws TenantLifecycleException 当当前状态不允许冻结时抛出
     */
    public void freeze() {
        requireStatus(TenantStatus.ACTIVE, "freeze");
        this.status = TenantStatus.FROZEN;
        touch();
    }

    /**
     * 领域行为：解冻租户。
     *
     * <p>仅 {@link TenantStatus#FROZEN} 可解冻恢复为正常。</p>
     *
     * @throws TenantLifecycleException 当当前状态不允许解冻时抛出
     */
    public void unfreeze() {
        requireStatus(TenantStatus.FROZEN, "unfreeze");
        this.status = TenantStatus.ACTIVE;
        touch();
    }

    /**
     * 领域行为：注销租户。
     *
     * <p>{@link TenantStatus#ACTIVE}、{@link TenantStatus#DISABLED}、
     * {@link TenantStatus#FROZEN} 均可注销。</p>
     *
     * @throws TenantLifecycleException 当当前状态不允许注销时抛出
     */
    public void deactivate() {
        if (status != TenantStatus.ACTIVE && status != TenantStatus.DISABLED && status != TenantStatus.FROZEN) {
            throw new TenantLifecycleException(status, "deactivate");
        }
        this.status = TenantStatus.DEACTIVATED;
        touch();
    }

    /**
     * 领域行为：归档租户（终态）。
     *
     * <p>仅 {@link TenantStatus#DEACTIVATED} 可归档，归档后任何迁移被拒绝。</p>
     *
     * @throws TenantLifecycleException 当当前状态不允许归档时抛出
     */
    public void archive() {
        requireStatus(TenantStatus.DEACTIVATED, "archive");
        this.status = TenantStatus.ARCHIVED;
        touch();
    }

    /**
     * 领域行为：校验用户数配额增长。
     *
     * <p>非正常状态租户不允许任何配额增长，正常状态租户受配额上限约束。</p>
     *
     * @param current 当前用户数
     * @param increment 申请增量
     * @throws TenantLifecycleException 当租户非正常状态时抛出
     * @throws TenantQuotaExceededException 当超出配额上限时抛出
     */
    public void assertUserQuota(long current, long increment) {
        if (!canCreateBusinessData()) {
            throw new TenantLifecycleException(status, "assertUserQuota");
        }
        quota.assertUsers(current, increment);
    }

    /**
     * 断言存储配额：当前已用字节数加上增量后不超出 {@code maxStorageMb}（方案批次 B5）。
     *
     * <p>软上限口径：先检后写，超限抛 {@link TenantQuotaExceededException}。
     * 字节按 1MB=1024×1024 向上取整换算为 MB 后交由配额值对象断言（向上取整保证
     * 不足 1MB 的增量也计入占用）。
     *
     * @param currentBytes  当前已用字节数
     * @param incrementBytes 本次新增字节数
     * @throws TenantLifecycleException       当租户状态不允许创建业务数据时抛出
     * @throws TenantQuotaExceededException   当超出存储配额时抛出
     */
    public void assertStorageQuota(long currentBytes, long incrementBytes) {
        if (!canCreateBusinessData()) {
            throw new TenantLifecycleException(status, "assertStorageQuota");
        }
        long currentMb = bytesToCeilMb(currentBytes);
        long incrementMb = bytesToCeilMb(incrementBytes);
        quota.assertStorage(currentMb, incrementMb);
    }

    /** 字节 → 向上取整 MB（0 字节映射 0MB）。 */
    private static long bytesToCeilMb(long bytes) {
        if (bytes <= 0) {
            return 0;
        }
        return (bytes + 1024L * 1024L - 1) / (1024L * 1024L);
    }

    /**
     * 校验当前状态与期望状态一致，否则抛出生命周期异常。
     *
     * @param expected 期望状态
     * @param operation 操作名称
     * @throws TenantLifecycleException 当状态不匹配时抛出
     */
    private void requireStatus(TenantStatus expected, String operation) {
        if (this.status != expected) {
            throw new TenantLifecycleException(this.status, operation);
        }
    }

    /**
     * 租户状态枚举。
     */
    public enum TenantStatus {
        /** 正常 */
        ACTIVE,
        /** 停用 */
        DISABLED,
        /** 冻结 */
        FROZEN,
        /** 注销 */
        DEACTIVATED,
        /** 归档（终态） */
        ARCHIVED
    }
}
