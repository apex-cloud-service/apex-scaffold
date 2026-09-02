package com.apex.scaffold.common.context;

import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
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
 * 多租户上下文（C10 多租户）。
 *
 * <p>以 {@link ThreadLocal} 承载当前请求的租户 ID，由 {@code TenantAspect}
 * 在进入 {@code @TenantAware} 方法前从可信认证身份装配，
 * 业务层和持久化层通过本类读取当前租户。</p>
 *
 * <p>响应式场景下配合 {@code ReactorMdcConfig} 的 MDC 贯穿机制使用；
 * 请求结束必须调用 {@link #clear()} 释放，防止线程复用导致跨租户泄漏。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
public final class TenantContext {

    /**
     * Reactor Context 中租户 ID 的键。
     *
     * <p>以 {@link TenantContext} 类名作为键，避免字符串魔法值与跨类碰撞。</p>
     */
    public static final String REACTOR_CONTEXT_KEY = TenantContext.class.getName();

    /** ThreadLocal 租户 ID */
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();

    private static final String SCHEDULER_HOOK_KEY = TenantContext.class.getName();
    private static final String OPERATOR_HOOK_KEY = TenantContext.class.getName() + ".operator";

    static {
        initializeGlobalHooks();
    }

    /**
     * 幂等初始化全局 Reactor 与 Scheduler Hook。
     *
     * <p>容器首次加载类时会自动调用；测试若执行过 {@link #resetGlobalHooks()}，可再次调用本方法
     * 恢复默认传播机制，避免同 JVM 测试顺序造成上下文传播能力缺失。</p>
     */
    public static synchronized void initializeGlobalHooks() {
        Schedulers.resetOnScheduleHook(SCHEDULER_HOOK_KEY);
        Schedulers.onScheduleHook(SCHEDULER_HOOK_KEY, runnable -> {
            String capturedTenantId = TENANT_ID.get();
            return () -> {
                String previous = install(capturedTenantId);
                try {
                    runnable.run();
                } finally {
                    restore(previous);
                }
            };
        });
        Hooks.resetOnEachOperator(OPERATOR_HOOK_KEY);
        Hooks.onEachOperator(
                OPERATOR_HOOK_KEY,
                Operators.lift((scannable, subscriber) -> new TenantContextSubscriber<>(subscriber)));
    }

    private static final class TenantContextSubscriber<T> implements CoreSubscriber<T> {

        private final CoreSubscriber<? super T> actual;

        private TenantContextSubscriber(CoreSubscriber<? super T> actual) {
            this.actual = actual;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (s instanceof Fuseable.QueueSubscription<?> queueSubscription) {
                actual.onSubscribe(new TenantContextQueueSubscription<>(queueSubscription, this));
            } else {
                actual.onSubscribe(new TenantContextSubscription(s, this));
            }
        }

        @Override
        public void onNext(T t) {
            withTenant(() -> actual.onNext(t));
        }

        @Override
        public void onError(Throwable t) {
            withTenant(() -> actual.onError(t));
        }

        @Override
        public void onComplete() {
            withTenant(actual::onComplete);
        }

        @Override
        public Context currentContext() {
            return actual.currentContext();
        }

        private void withTenant(Runnable runnable) {
            String tenantId = actual.currentContext().getOrDefault(REACTOR_CONTEXT_KEY, null);
            // 只有当调用方显式写入了 Reactor Context 时才做"安装/恢复"；
            // tenantId == null 时不参与线程上下文管理，避免 onComplete/doFinally
            // 后业务代码的清理被 finally 中的 restore 覆盖。
            if (tenantId == null) {
                runnable.run();
                return;
            }
            String previous = install(tenantId);
            try {
                runnable.run();
            } finally {
                restore(previous);
            }
        }
    }

    private static final class TenantContextSubscription implements Subscription {

        private final Subscription delegate;
        private final TenantContextSubscriber<?> subscriber;

        private TenantContextSubscription(Subscription delegate, TenantContextSubscriber<?> subscriber) {
            this.delegate = delegate;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            subscriber.withTenant(() -> delegate.request(n));
        }

        @Override
        public void cancel() {
            subscriber.withTenant(delegate::cancel);
        }
    }

    private static final class TenantContextQueueSubscription<T> implements Fuseable.QueueSubscription<T> {

        private final Fuseable.QueueSubscription<T> delegate;
        private final TenantContextSubscriber<?> subscriber;

        private TenantContextQueueSubscription(
                Fuseable.QueueSubscription<T> delegate, TenantContextSubscriber<?> subscriber) {
            this.delegate = delegate;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            subscriber.withTenant(() -> delegate.request(n));
        }

        @Override
        public void cancel() {
            subscriber.withTenant(delegate::cancel);
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
            return new Iterator<>() {
                private final Iterator<T> delegateIterator = delegate.iterator();

                @Override
                public boolean hasNext() {
                    return delegateIterator.hasNext();
                }

                @Override
                public T next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return delegateIterator.next();
                }
            };
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

    /**
     * 重置全局注册的 Reactor 与 Scheduler Hook。
     *
     * <p>仅限测试或容器销毁清理调用，避免同 JVM 内重复注册造成上下文传播顺序漂移。</p>
     */
    public static void resetGlobalHooks() {
        Schedulers.resetOnScheduleHook(SCHEDULER_HOOK_KEY);
        Hooks.resetOnEachOperator(OPERATOR_HOOK_KEY);
    }

    /** 私有构造，禁止实例化 */
    private TenantContext() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 装配当前请求的租户 ID。
     *
     * <p>用于显式设置租户（如响应式发布器、请求网关入口）。与
     * {@link #restoreTenantId(String)} 配对使用，保证调用方在 finally 中能恢复
     * 线程复用前的旧值，避免跨租户泄漏。</p>
     *
     * @param tenantId 租户 ID（如租户编码）；{@code null} 等价于清除
     * @return 写入前的旧租户 ID（未装配时为 {@code null}）
     */
    public static String setTenantId(String tenantId) {
        String previous = TENANT_ID.get();
        if (tenantId == null) {
            TENANT_ID.remove();
        } else {
            TENANT_ID.set(tenantId);
        }
        return previous;
    }

    /**
     * 恢复装配前的租户 ID。
     *
     * <p>与 {@link #setTenantId(String)} 配对，在 finally 中调用：
     * <pre>{@code String prev = TenantContext.setTenantId(id);
     * try { ... } finally { TenantContext.restoreTenantId(prev); }}</pre>
     *
     * @param previous {@link #setTenantId(String)} 返回的旧租户 ID
     */
    public static void restoreTenantId(String previous) {
        if (previous == null) {
            TENANT_ID.remove();
        } else {
            TENANT_ID.set(previous);
        }
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

    /**
     * 创建携带租户 ID 的 Reactor {@link Context}。
     *
     * <p>配合 {@code .contextWrite()} 在响应式操作符链中传播租户身份，
     * 下游通过 {@link #withTenant(Mono)} / {@link #withTenant(Flux)} 读取。</p>
     *
     * @param tenantId 租户 ID，不允许为空
     * @return 包含租户 ID 的 Reactor Context
     * @throws IllegalArgumentException 当租户 ID 为 {@code null} 或空白时抛出
     */
    public static Context withTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("租户 ID 不能为空");
        }
        return Context.of(REACTOR_CONTEXT_KEY, tenantId);
    }

    /**
     * 在订阅 {@link Mono} 时从 Reactor Context 读取租户 ID 并安装到当前线程。
     *
     * <p>响应式链中阻塞式租户访问（如 MyBatis 查询）切换到其他线程执行时，
     * 通过本方法在订阅阶段捕获 {@link ThreadLocal} 租户上下文，执行完毕后自动恢复，
     * 防止跨线程租户泄漏。未装配租户 ID 时直接透传原始发布者。</p>
     *
     * @param publisher 原始 {@link Mono} 发布者
     * @param <T>       数据流元素类型
     * @return 包装后的 {@link Mono}
     */
    public static <T> Mono<T> withTenant(Mono<T> publisher) {
        return Mono.deferContextual(context -> {
            String tenantId = context.getOrDefault(REACTOR_CONTEXT_KEY, null);
            if (tenantId == null || tenantId.isBlank()) {
                return publisher;
            }
            return Mono.fromDirect(subscriber -> {
                String previous = install(tenantId);
                try {
                    publisher.subscribe(subscriber);
                } finally {
                    restore(previous);
                }
            });
        });
    }

    /**
     * 在订阅 {@link Flux} 时从 Reactor Context 读取租户 ID 并安装到当前线程。
     *
     * <p>语义与 {@link #withTenant(Mono)} 一致，适用于多元素数据流；未装配租户 ID 时
     * 直接透传原始发布者。</p>
     *
     * @param publisher 原始 {@link Flux} 发布者
     * @param <T>       数据流元素类型
     * @return 包装后的 {@link Flux}
     */
    public static <T> Flux<T> withTenant(Flux<T> publisher) {
        return Flux.deferContextual(context -> {
            String tenantId = context.getOrDefault(REACTOR_CONTEXT_KEY, null);
            if (tenantId == null || tenantId.isBlank()) {
                return publisher;
            }
            return Flux.from(subscriber -> {
                String previous = install(tenantId);
                try {
                    publisher.subscribe(subscriber);
                } finally {
                    restore(previous);
                }
            });
        });
    }

    private static String install(String tenantId) {
        String previous = TENANT_ID.get();
        TENANT_ID.set(tenantId);
        return previous;
    }

    private static void restore(String previous) {
        if (previous == null) {
            clear();
        } else {
            TENANT_ID.set(previous);
        }
    }
}
