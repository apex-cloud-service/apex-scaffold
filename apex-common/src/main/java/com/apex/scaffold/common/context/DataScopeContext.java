package com.apex.scaffold.common.context;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Fuseable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Operators;
import reactor.core.scheduler.Schedulers;
import reactor.util.context.Context;

/**
 * 数据权限上下文。
 *
 * <p>以 {@link ThreadLocal} 承载当前请求用户的数据权限信息（{@link DataScopeInfo}）
 * 与注入配置（{@link DataScopeConfig}）。由数据权限装配端（{@code DataScopeAspect}、
 * 认证过滤器或业务代码）在查询前填充，{@code DataScopeInnerInterceptor} 在 SQL 执行前
 * 读取并自动拼接过滤条件。</p>
 *
 * <h2>H3 响应式/跨线程上下文桥接</h2>
 * <ul>
 *   <li>全局 {@link Schedulers#onScheduleHook}：任务提交时捕获当前
 *       {@link Scope} 快照，执行线程中安装 → finally 恢复，保证
 *       {@code BlockingIoSchedulers.jdbc()} 跨线程时上下文不丢。</li>
 *   <li>全局 {@link Hooks#onEachOperator}：lift {@code CoreSubscriber}，
 *       从 Reactor Context {@link #REACTOR_CONTEXT_KEY} 读取 Scope 并安装，
 *       覆盖 onNext/onError/onComplete 与 request/cancel，并区分
 *       Subscription/QueueSubscription，不破坏融合作业。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 2.2.0
 */
public final class DataScopeContext {

    /** Reactor Context 中数据权限 Scope 的键。 */
    public static final String REACTOR_CONTEXT_KEY = DataScopeContext.class.getName();

    /** ThreadLocal 数据权限条目（信息 + 注入配置） */
    private static final ThreadLocal<Scope> SCOPE = new ThreadLocal<>();

    private static final String SCHEDULER_HOOK_KEY = DataScopeContext.class.getName();
    private static final String OPERATOR_HOOK_KEY = DataScopeContext.class.getName() + ".operator";

    static {
        initializeGlobalHooks();
    }

    /** 幂等初始化全局 Reactor 与 Scheduler Hook（类加载时自动调用）。 */
    public static synchronized void initializeGlobalHooks() {
        Schedulers.resetOnScheduleHook(SCHEDULER_HOOK_KEY);
        Schedulers.onScheduleHook(SCHEDULER_HOOK_KEY, runnable -> {
            Scope captured = SCOPE.get();
            return () -> {
                Scope previous = SCOPE.get();
                installScope(captured);
                try {
                    runnable.run();
                } finally {
                    installScope(previous);
                }
            };
        });
        Hooks.resetOnEachOperator(OPERATOR_HOOK_KEY);
        Hooks.onEachOperator(
                OPERATOR_HOOK_KEY,
                Operators.lift((scannable, subscriber) -> new DataScopeContextSubscriber<>(subscriber)));
    }

    /** 重置全局 Hook（测试/容器销毁清理）。 */
    public static void resetGlobalHooks() {
        Schedulers.resetOnScheduleHook(SCHEDULER_HOOK_KEY);
        Hooks.resetOnEachOperator(OPERATOR_HOOK_KEY);
    }

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
     * @param scope 数据权限条目，不允许为 {@code null}
     * @return 释放句柄，调用 {@link AutoCloseable#close()} 恢复之前的值
     */
    public static AutoCloseable install(Scope scope) {
        Scope previous = SCOPE.get();
        SCOPE.set(Objects.requireNonNull(scope, "scope must not be null"));
        return () -> installScope(previous);
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

    /** 释放当前请求的数据权限上下文。 */
    public static void clear() {
        SCOPE.remove();
    }

    /**
     * 创建携带 Scope 的 Reactor Context。
     *
     * @param scope 数据权限条目；{@code null} 返回空 Context
     * @return 包含 Scope 的 Reactor Context
     */
    public static Context withDataScope(Scope scope) {
        if (scope == null) {
            return Context.empty();
        }
        // Scope record 的紧凑构造器会防御性复制 DataScopeInfo
        return Context.of(REACTOR_CONTEXT_KEY, new Scope(scope.info(), scope.config()));
    }

    /**
     * 在订阅 {@link Mono} 时从 Reactor Context 读取 Scope 并安装到当前线程。
     */
    public static <T> Mono<T> withDataScope(Mono<T> publisher) {
        return Mono.deferContextual(context -> {
            Scope scope = context.getOrDefault(REACTOR_CONTEXT_KEY, null);
            if (scope == null) {
                return publisher;
            }
            return Mono.fromDirect(subscriber -> {
                Scope previous = SCOPE.get();
                installScope(scope);
                try {
                    publisher.subscribe(subscriber);
                } finally {
                    installScope(previous);
                }
            });
        });
    }

    /**
     * 在订阅 {@link Flux} 时从 Reactor Context 读取 Scope 并安装到当前线程。
     */
    public static <T> Flux<T> withDataScope(Flux<T> publisher) {
        return Flux.deferContextual(context -> {
            Scope scope = context.getOrDefault(REACTOR_CONTEXT_KEY, null);
            if (scope == null) {
                return publisher;
            }
            return Flux.from(subscriber -> {
                Scope previous = SCOPE.get();
                installScope(scope);
                try {
                    publisher.subscribe(subscriber);
                } finally {
                    installScope(previous);
                }
            });
        });
    }

    private static void installScope(Scope scope) {
        if (scope == null) {
            SCOPE.remove();
            return;
        }
        SCOPE.set(new Scope(scope.info(), scope.config()));
    }

    /**
     * 数据权限条目：权限信息与其注入配置的组合。
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

    private static final class DataScopeContextSubscriber<T> implements CoreSubscriber<T> {

        private final CoreSubscriber<? super T> actual;

        private DataScopeContextSubscriber(CoreSubscriber<? super T> actual) {
            this.actual = actual;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (s instanceof Fuseable.QueueSubscription<?> queueSubscription) {
                actual.onSubscribe(new DataScopeContextQueueSubscription<>(queueSubscription, this));
            } else {
                actual.onSubscribe(new DataScopeContextSubscription(s, this));
            }
        }

        @Override
        public void onNext(T t) {
            withScope(() -> actual.onNext(t));
        }

        @Override
        public void onError(Throwable t) {
            withScope(() -> actual.onError(t));
        }

        @Override
        public void onComplete() {
            withScope(actual::onComplete);
        }

        @Override
        public Context currentContext() {
            return actual.currentContext();
        }

        private void withScope(Runnable runnable) {
            Scope scope = actual.currentContext().getOrDefault(REACTOR_CONTEXT_KEY, null);
            // 只有当调用方显式写入了 Reactor Context 时才做"安装/恢复"；
            // scope == null 时不参与线程上下文管理，避免 onComplete/doFinally
            // 后业务代码的清理被 finally 中的 restore 覆盖。
            if (scope == null) {
                runnable.run();
                return;
            }
            Scope previous = SCOPE.get();
            installScope(scope);
            try {
                runnable.run();
            } finally {
                installScope(previous);
            }
        }
    }

    private static final class DataScopeContextSubscription implements Subscription {

        private final Subscription delegate;
        private final DataScopeContextSubscriber<?> subscriber;

        private DataScopeContextSubscription(Subscription delegate, DataScopeContextSubscriber<?> subscriber) {
            this.delegate = delegate;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            subscriber.withScope(() -> delegate.request(n));
        }

        @Override
        public void cancel() {
            subscriber.withScope(delegate::cancel);
        }
    }

    private static final class DataScopeContextQueueSubscription<T> implements Fuseable.QueueSubscription<T> {

        private final Fuseable.QueueSubscription<T> delegate;
        private final DataScopeContextSubscriber<?> subscriber;

        private DataScopeContextQueueSubscription(
                Fuseable.QueueSubscription<T> delegate, DataScopeContextSubscriber<?> subscriber) {
            this.delegate = delegate;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            subscriber.withScope(() -> delegate.request(n));
        }

        @Override
        public void cancel() {
            subscriber.withScope(delegate::cancel);
        }

        @Override
        public int requestFusion(int requestedMode) {
            return delegate.requestFusion(requestedMode);
        }

        @Override
        public T poll() {
            return delegate.poll();
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public boolean isEmpty() {
            return delegate.isEmpty();
        }

        @Override
        public void clear() {
            delegate.clear();
        }

        @Override
        public Iterator<T> iterator() {
            return Collections.unmodifiableList(new ArrayList<>(delegate)).iterator();
        }

        @Override
        public Object[] toArray() {
            return delegate.toArray();
        }

        @Override
        public <U> U[] toArray(U[] a) {
            return delegate.toArray(a);
        }

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return delegate.containsAll(c);
        }

        @Override
        public boolean add(T t) {
            return delegate.add(t);
        }

        @Override
        public boolean remove(Object o) {
            return delegate.remove(o);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return delegate.removeAll(c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return delegate.retainAll(c);
        }
    }
}
