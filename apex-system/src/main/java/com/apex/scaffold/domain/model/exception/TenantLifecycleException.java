package com.apex.scaffold.domain.model.exception;

import com.apex.scaffold.domain.model.entity.TenantEntity.TenantStatus;

/**
 * 租户生命周期状态迁移异常。
 *
 * <p>当对租户执行非法状态迁移（如从终态归档再次迁移、从不兼容状态执行操作）时抛出。
 * 应用层捕获后映射为 {@code BusinessException(ResultCode.TENANT_ILLEGAL_TRANSITION)}。
 *
 * <h2>触发场景</h2>
 * <ul>
 *   <li>对已归档（终态）租户执行任何状态迁移</li>
 *   <li>对停用租户执行冻结（需先启用）</li>
 *   <li>对未注销租户执行归档</li>
 *   <li>对非正常租户执行配额增长</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@lombok.Getter
public class TenantLifecycleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 当前租户状态 */
    private final TenantStatus currentStatus;

    /** 期望的迁移目标状态 */
    private final transient String operation;

    /**
     * 构造租户生命周期异常。
     *
     * @param currentStatus 当前租户状态
     * @param operation 被拒绝的操作名称
     */
    public TenantLifecycleException(TenantStatus currentStatus, String operation) {
        super("租户当前状态[" + currentStatus + "]不允许执行操作[" + operation + "]");
        this.currentStatus = currentStatus;
        this.operation = operation;
    }
}
