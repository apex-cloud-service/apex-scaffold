package com.apex.scaffold.common.context;

import java.util.Optional;

/**
 * 数据权限上下文（部门级）。
 *
 * <p>以 {@link ThreadLocal} 承载当前请求用户的数据权限部门范围，
 * 由 {@code DataScopeAspect} 在进入 {@code @DataScope} 方法前装配，
 * 业务层通过 {@link #currentDeptId()} 读取后构造 {@code dept_id} 查询条件。</p>
 *
 * <p>响应式场景下配合项目既有 {@code ReactorMdcConfig} 的 MDC 贯穿机制使用，
 * 请求结束后必须调用 {@link #clear()} 释放，避免线程复用导致越权泄漏。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
public final class DataScopeContext {

    /** ThreadLocal 数据权限部门 ID */
    private static final ThreadLocal<Long> DEPT_ID = new ThreadLocal<>();

    /** 私有构造，禁止实例化 */
    private DataScopeContext() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 装配当前请求的数据权限部门 ID。
     *
     * @param deptId 部门 ID
     */
    public static void setDeptId(Long deptId) {
        DEPT_ID.set(deptId);
    }

    /**
     * 获取当前请求的数据权限部门 ID。
     *
     * @return 部门 ID（未装配时为 {@link Optional#empty()}
     */
    public static Optional<Long> currentDeptId() {
        return Optional.ofNullable(DEPT_ID.get());
    }

    /**
     * 释放当前请求的数据权限上下文。
     *
     * <p>必须在请求处理完成后调用，防止线程池复用导致数据越权。</p>
     */
    public static void clear() {
        DEPT_ID.remove();
    }
}
