package com.apex.scaffold.common.context;

import java.util.concurrent.Callable;
import reactor.util.context.ContextView;

/**
 * JDBC / MyBatis 线程侧与 Reactor Context 的数据范围桥接工具。
 *
 *
 *<p>将 Reactor Context 中已解析的 {@link DataScopeContext.Scope} 安装到
 *当前线程并包装 {@link Callable}，执行后自动清理，用于 MyBatis 拦截器
 *在非 Reactor 线程（JDBC Worker）中获取数据权限。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public final class DataScopeJdbcBridge {

    /**
     * Reactor Context 中数据权限 {@link DataScopeContext.Scope} 的键。
     *
     * <p>以 {@link DataScopeContext.Scope} 类型本身作为键，避免字符串魔法值。</p>
     */
    public static final Class<DataScopeContext.Scope> CONTEXT_KEY = DataScopeContext.Scope.class;

    private DataScopeJdbcBridge() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 包装 {@link Callable}，执行前从 Reactor Context 安装数据权限并延迟安装到非 Reactor 线程。
     *
     * <p>用于 MyBatis 拦截器在 JDBC Worker 线程中访问当前请求的数据权限：从
     * {@code contextView} 读取 {@link DataScopeContext.Scope} 并安装到执行线程，
     * 调用完毕后自动清理，防止线程复用导致数据越权。</p>
     *
     * @param contextView Reactor 上下文视图（包含 {@link #CONTEXT_KEY} 对应的数据权限）
     * @param callable    需在数据权限上下文中执行的任务
     * @param <T>         任务返回类型
     * @return 包装后的 {@link Callable}，调用时自动安装并清理数据权限
     * @throws IllegalStateException 当 {@code contextView} 中缺失数据权限时抛出
     */
    public static <T> Callable<T> required(ContextView contextView, Callable<T> callable) {
        DataScopeContext.Scope scope = contextView.getOrDefault(CONTEXT_KEY, null);
        if (scope == null) {
            throw new IllegalStateException("Required data scope is missing from Reactor Context");
        }
        return () -> {
            try (AutoCloseable ignored = DataScopeContext.install(scope)) {
                return callable.call();
            }
        };
    }
}
