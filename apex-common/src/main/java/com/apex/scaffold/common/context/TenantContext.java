package com.apex.scaffold.common.context;

import java.util.Optional;

/**
 * 多租户上下文（C10 多租户）。
 *
 * <p>以 {@link ThreadLocal} 承载当前请求的租户 ID，由 {@code TenantAspect}
 * 在进入 {@code @TenantAware} 方法前从请求头 {@code X-Tenant-Id} 装配，
 * 业务层读取后用于租户隔离（SQL 过滤 / 分库分表键）。</p>
 *
 * <p>响应式场景下配合 {@code ReactorMdcConfig} 的 MDC 贯穿机制使用；
 * 请求结束必须调用 {@link #clear()} 释放，防止线程复用导致跨租户泄漏。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
public final class TenantContext {

    /** ThreadLocal 租户 ID */
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    /** 私有构造，禁止实例化 */
    private TenantContext() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 装配当前请求的租户 ID。
     *
     * @param tenantId 租户 ID（如租户编码）
     */
    public static void setTenantId(String tenantId) {
        TENANT_ID.set(tenantId);
    }

    /**
     * 获取当前请求的租户 ID。
     *
     * @return 租户 ID（未装配时为 {@link Optional#empty()}
     */
    public static Optional<String> currentTenantId() {
        return Optional.ofNullable(TENANT_ID.get());
    }

    /**
     * 释放当前请求的租户上下文。
     *
     * <p>必须在请求处理完成后调用，防止线程池复用导致跨租户数据泄漏。</p>
     */
    public static void clear() {
        TENANT_ID.remove();
    }
}
