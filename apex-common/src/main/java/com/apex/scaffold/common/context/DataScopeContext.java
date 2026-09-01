package com.apex.scaffold.common.context;

import java.util.Objects;
import java.util.Optional;

/**
 * 数据权限上下文。
 *
 * <p>以 {@link ThreadLocal} 承载当前请求用户的数据权限信息（{@link DataScopeInfo}）
 * 与注入配置（{@link DataScopeConfig}）。由数据权限装配端（{@code DataScopeAspect}、
 * 认证过滤器或业务代码）在查询前填充，{@code DataScopeInnerInterceptor} 在 SQL 执行前
 * 读取并自动拼接过滤条件。</p>
 *
 * <p><b>响应式场景</b>：配合响应式感知的 {@code DataScopeAspect}（{@code doFirst/doFinally}
 * 保证上下文在异步执行期间存活）使用；请求结束必须调用 {@link #clear()} 释放，
 * 防止线程池复用导致数据越权泄漏。阻塞查询经 {@code BlockingIoSchedulers.jdbc()}
 * 切换到 JDBC 线程执行时，需确保上下文在该线程可见（详见 {@code docs/DATA_SCOPE.md}）。</p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.2.0
 */
public final class DataScopeContext {

    /** ThreadLocal 数据权限条目（信息 + 注入配置） */
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

    /** 私有构造，禁止实例化 */
    private DataScopeContext() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 装配当前请求的数据权限（使用默认注入配置）。
     *
     * @param info 数据权限信息
     */
    public static void setDataScope(DataScopeInfo info) {
        SCOPE.set(new Scope(info, DataScopeConfig.defaults()));
    }

    /**
     * 装配当前请求的数据权限与注入配置。
     *
     * @param info   数据权限信息
     * @param config 注入配置（{@code @DataScope} 注解转换而来；为 {@code null} 时使用默认配置）
     */
    public static void setDataScope(DataScopeInfo info, DataScopeConfig config) {
        SCOPE.set(new Scope(info, config == null ? DataScopeConfig.defaults() : config));
    }

    /**
     * 获取当前请求的数据权限信息。
     *
     * @return 数据权限信息（未装配时为 {@link Optional#empty()}
     */
    public static Optional<DataScopeInfo> currentDataScope() {
        return currentScope().map(Scope::info);
    }

    /**
     * 获取当前请求的数据权限条目（信息 + 注入配置）。
     *
     * @return 数据权限条目（未装配时为 {@link Optional#empty()}
     */
    public static Optional<Scope> currentScope() {
        return Optional.ofNullable(SCOPE.get());
    }

    /**
     * 安装数据权限条目到当前线程并返回清理句柄。
     *
     * <p>用于需要临时覆盖 {@link #setDataScope(DataScopeInfo)} 装配结果的场景
     * （如 {@code DataScopeAspect} 的 doFirst/doFinally 配对）。返回值必须通过
     * try-with-resources 或 finally 释放，否则线程复用将导致数据越权泄漏。</p>
     *
     * @param scope 数据权限条目，不允许为 {@code null}
     * @return 释放句柄，调用 {@link AutoCloseable#close()} 恢复之前的值
     */
    public static AutoCloseable install(Scope scope) {
        Scope previous = SCOPE.get();
        SCOPE.set(Objects.requireNonNull(scope, "scope must not be null"));
        return () -> {
            if (previous == null) {
                SCOPE.remove();
            } else {
                SCOPE.set(previous);
            }
        };
    }

    /**
     * 获取当前请求的数据权限注入配置。
     *
     * @return 注入配置（未装配时为 {@link Optional#empty()}
     */
    public static Optional<DataScopeConfig> currentConfig() {
        return currentScope().map(Scope::config);
    }

    /**
     * 是否已装配有效的数据权限（存在且非"全部数据权限"）。
     *
     * <p>拦截器以此快速判断是否需要注入过滤条件。</p>
     *
     * @return {@code true} 表示需要过滤
     */
    public static boolean isActive() {
        Scope scope = SCOPE.get();
        return scope != null && !scope.info().isAll();
    }

    /**
     * 装配当前请求的数据权限部门 ID（兼容旧 API，按"本部门数据权限"处理）。
     *
     * @param deptId 部门 ID
     */
    public static void setDeptId(Long deptId) {
        SCOPE.set(new Scope(DataScopeInfo.ofDept(deptId), DataScopeConfig.defaults()));
    }

    /**
     * 获取当前请求的数据权限部门 ID（兼容旧 API）。
     *
     * @return 部门 ID（未装配时为 {@link Optional#empty()}
     */
    public static Optional<Long> currentDeptId() {
        return currentDataScope().map(DataScopeInfo::deptId);
    }

    /**
     * 释放当前请求的数据权限上下文。
     *
     * <p>必须在请求处理完成后调用，防止线程池复用导致数据越权。</p>
     */
    public static void clear() {
        SCOPE.remove();
    }

    /**
     * 数据权限条目：权限信息与其注入配置的组合。
     *
     * <p>紧凑构造器对 {@code info} 做防御性复制，避免外部篡改 {@link DataScopeInfo}
     * 内部可变集合导致上下文状态不一致。</p>
     *
     * @param info   数据权限信息（不允许为 {@code null}）
     * @param config 注入配置（不允许为 {@code null}）
     */
    public record Scope(DataScopeInfo info, DataScopeConfig config) {
        /**
         * 紧凑构造器：对 {@code info} 防御性复制并校验非空。
         *
         * @param info   数据权限信息
         * @param config 注入配置
         * @throws NullPointerException 当任一参数为 {@code null} 时抛出
         */
        public Scope {
            info = new DataScopeInfo(
                    Objects.requireNonNull(info, "info must not be null").type(),
                    info.userId(),
                    info.deptId(),
                    info.deptIds());
            config = Objects.requireNonNull(config, "config must not be null");
        }
    }
}
