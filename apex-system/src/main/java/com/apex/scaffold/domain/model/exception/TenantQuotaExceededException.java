package com.apex.scaffold.domain.model.exception;

/**
 * 租户配额超限异常。
 *
 * <p>当租户的资源使用量（用户数、存储量等）增长超出配额上限时抛出。
 * 应用层捕获后映射为 {@code BusinessException(ResultCode.TENANT_QUOTA_EXCEEDED)}。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@lombok.Getter
public class TenantQuotaExceededException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 配额维度（如 users、storage） */
    private final String dimension;

    /** 当前使用量 */
    private final long current;

    /** 申请增量 */
    private final long increment;

    /** 配额上限 */
    private final long max;

    /**
     * 构造配额超限异常。
     *
     * @param dimension 配额维度
     * @param current 当前使用量
     * @param increment 申请增量
     * @param max 配额上限
     */
    public TenantQuotaExceededException(String dimension, long current, long increment, long max) {
        super("租户配额超限[" + dimension + "]: 当前=" + current + ", 增量=" + increment + ", 上限=" + max);
        this.dimension = dimension;
        this.current = current;
        this.increment = increment;
        this.max = max;
    }
}
