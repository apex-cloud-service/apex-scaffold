package com.apex.scaffold.common.context;

import java.util.concurrent.Callable;
import reactor.util.context.ContextView;

/**
 * JDBC / MyBatis 线程侧与 Reactor Context 的数据范围桥接工具。
 *
 * <p>将 Reactor Context 中已解析的 {@link DataScopeContext.Scope} 安装到
 * 当前线程并包装 {@link Callable}，执行后自动清理，用于 MyBatis 拦截器
 * 在非 Reactor 线程（JDBC Worker）中获取数据权限。</p>
 *
 * @author apex-scaffold
 */
public final class DataScopeJdbcBridge {

    public static final Class<DataScopeContext.Scope> CONTEXT_KEY = DataScopeContext.Scope.class;

    private DataScopeJdbcBridge() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

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
