package com.apex.scaffold.common.context;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
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
 * 数据源切换上下文（自定义数据源动态切换）。
 *
 * <p>以 {@link ThreadLocal} + {@link Deque} 承载当前线程的数据源切换栈，
 * 由 {@code DataSourceSwitchAspect} 在进入 {@code @DataSource} 方法前
 * {@link #push(String)}、方法结束后 {@link #pop()}。使用栈结构支持嵌套切换
 * （外层走从库、内层走报表库），内层释放后自动回到外层数据源。</p>
 *
 * <p><b>与 Baomidou 的关系</b>：本上下文是本脚手架的"自定义数据源"事实来源
 * （供日志/埋点/读取当前数据源），实际路由由切面同步委派到 Baomidou 的
 * {@code DynamicDataSourceContextHolder}（驱动 {@code DynamicRoutingDataSource} 选库）。</p>
 *
 * <h2>H3 响应式/跨线程上下文桥接</h2>
 * <ul>
 *   <li>在 {@link #initializeGlobalHooks()} 中挂载全局
 *       {@link Schedulers#onScheduleHook}：任务提交到 Scheduler 时捕获当前线程的
 *       数据源栈快照，在执行线程内重建后运行，finally 清理，解决
 *       BlockingIoSchedulers.jdbc() 等跨线程切换时数据源丢失。</li>
 *   <li>挂载全局 {@link Hooks#onEachOperator}：lift {@code CoreSubscriber}，
 *       从 Reactor Context（{@link #REACTOR_CONTEXT_KEY}）读取栈快照并安装，
 *       保证每个 onNext/onError/onComplete、request/cancel 执行前已恢复上下文，
 *       并区分普通 Subscription 与 {@link Fuseable.QueueSubscription}，避免融合作业模式
 *       丢失上下文。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 2.3.0
 */
public final class DataSourceContext {

    /** Reactor Context 中数据源栈快照的键。 */
    public static final String REACTOR_CONTEXT_KEY = DataSourceContext.class.getName();

    /** ThreadLocal 数据源切换栈 */
    private static final ThreadLocal<Deque<String>> STACK = new ThreadLocal<>();

    private static final String SCHEDULER_HOOK_KEY = DataSourceContext.class.getName();
    private static final String OPERATOR_HOOK_KEY = DataSourceContext.class.getName() + ".operator";

    static {
        initializeGlobalHooks();
    }

    /** 幂等初始化全局 Reactor 与 Scheduler Hook（加载类时自动调用）。 */
    public static synchronized void initializeGlobalHooks() {
        Schedulers.resetOnScheduleHook(SCHEDULER_HOOK_KEY);
        Schedulers.onScheduleHook(SCHEDULER_HOOK_KEY, runnable -> {
            Deque<String> snapshot = snapshotStack();
            return () -> {
                Deque<String> previous = snapshotStack();
                installStack(snapshot);
                try {
                    runnable.run();
                } finally {
                    installStack(previous);
                }
            };
        });
        Hooks.resetOnEachOperator(OPERATOR_HOOK_KEY);
        Hooks.onEachOperator(
                OPERATOR_HOOK_KEY,
                Operators.lift((scannable, subscriber) -> new DataSourceContextSubscriber<>(subscriber)));
    }

    /** 重置全局 Hook（测试/容器销毁清理用）。 */
    public static void resetGlobalHooks() {
        Schedulers.resetOnScheduleHook(SCHEDULER_HOOK_KEY);
        Hooks.resetOnEachOperator(OPERATOR_HOOK_KEY);
    }

    /** 私有构造，禁止实例化 */
    private DataSourceContext() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 切换并压栈目标数据源。
     *
     * @param dataSourceName 数据源名称（为空/空白时忽略，保持当前数据源）
     */
    public static void push(String dataSourceName) {
        if (dataSourceName == null || dataSourceName.isBlank()) {
            return;
        }
        deque().push(dataSourceName);
    }

    /** 弹出栈顶数据源（回到上一层数据源）。 */
    public static void pop() {
        Deque<String> stack = STACK.get();
        if (stack == null || stack.isEmpty()) {
            return;
        }
        stack.pop();
        if (stack.isEmpty()) {
            STACK.remove();
        }
    }

    /**
     * 获取当前生效的数据源名称。
     *
     * @return 数据源名称（未切换时为 {@link Optional#empty()}，使用主数据源）
     */
    public static Optional<String> current() {
        Deque<String> stack = STACK.get();
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(stack.peek());
    }

    /**
     * 是否正在执行数据源切换。
     *
     * @return {@code true} 表示存在生效的切换
     */
    public static boolean isSwitched() {
        Deque<String> stack = STACK.get();
        return stack != null && !stack.isEmpty();
    }

    /**
     * 释放当前线程的全部数据源切换上下文。
     *
     * <p>必须在请求处理完成后调用，防止线程池复用导致数据源串库。</p>
     */
    public static void clear() {
        STACK.remove();
    }

    /**
     * 创建携带数据源栈快照的 Reactor {@link Context}。
     *
     * @param dataSourceName 数据源名称；{@code null} 或空白时本方法为 no-op（返回空 Context）
     * @return 包含数据源栈快照的 Reactor Context
     */
    public static Context withDataSource(String dataSourceName) {
        if (dataSourceName == null || dataSourceName.isBlank()) {
            return Context.empty();
        }
        Deque<String> stack = new ArrayDeque<>(1);
        stack.push(dataSourceName);
        return Context.of(REACTOR_CONTEXT_KEY, stack);
    }

    /**
     * 在订阅 {@link Mono} 时从 Reactor Context 读取数据源栈并安装到当前线程。
     *
     * @param publisher 原始发布者
     * @param <T>       元素类型
     * @return 包装后的 Mono
     */
    public static <T> Mono<T> withDataSource(Mono<T> publisher) {
        return Mono.deferContextual(context -> {
            Deque<String> snapshot = context.getOrDefault(REACTOR_CONTEXT_KEY, null);
            if (snapshot == null || snapshot.isEmpty()) {
                return publisher;
            }
            return Mono.fromDirect(subscriber -> {
                Deque<String> previous = snapshotStack();
                installStack(snapshot);
                try {
                    publisher.subscribe(subscriber);
                } finally {
                    installStack(previous);
                }
            });
        });
    }

    /**
     * 在订阅 {@link Flux} 时从 Reactor Context 读取数据源栈并安装到当前线程。
     *
     * @param publisher 原始发布者
     * @param <T>       元素类型
     * @return 包装后的 Flux
     */
    public static <T> Flux<T> withDataSource(Flux<T> publisher) {
        return Flux.deferContextual(context -> {
            Deque<String> snapshot = context.getOrDefault(REACTOR_CONTEXT_KEY, null);
            if (snapshot == null || snapshot.isEmpty()) {
                return publisher;
            }
            return Flux.from(subscriber -> {
                Deque<String> previous = snapshotStack();
                installStack(snapshot);
                try {
                    publisher.subscribe(subscriber);
                } finally {
                    installStack(previous);
                }
            });
        });
    }

    /** 获取当前线程的切换栈（懒创建）。 */
    private static Deque<String> deque() {
        Deque<String> stack = STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            STACK.set(stack);
        }
        return stack;
    }

    /** 取得当前栈的只读副本；若空返回 null（便于 Hook 的"空不处理"快速路径）。 */
    private static Deque<String> snapshotStack() {
        Deque<String> current = STACK.get();
        if (current == null || current.isEmpty()) {
            return null;
        }
        return new ArrayDeque<>(current);
    }

    /** 以给定快照安装当前线程；若为 null/空则清理。 */
    private static void installStack(Deque<String> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            STACK.remove();
            return;
        }
        STACK.set(new ArrayDeque<>(snapshot));
    }

    private static final class DataSourceContextSubscriber<T> implements CoreSubscriber<T> {

        private final CoreSubscriber<? super T> actual;

        private DataSourceContextSubscriber(CoreSubscriber<? super T> actual) {
            this.actual = actual;
        }

        @Override
        public void onSubscribe(Subscription s) {
            if (s instanceof Fuseable.QueueSubscription<?> queueSubscription) {
                actual.onSubscribe(new DataSourceContextQueueSubscription<>(queueSubscription, this));
            } else {
                actual.onSubscribe(new DataSourceContextSubscription(s, this));
            }
        }

        @Override
        public void onNext(T t) {
            withContext(() -> actual.onNext(t));
        }

        @Override
        public void onError(Throwable t) {
            withContext(() -> actual.onError(t));
        }

        @Override
        public void onComplete() {
            withContext(actual::onComplete);
        }

        @Override
        public Context currentContext() {
            return actual.currentContext();
        }

        private void withContext(Runnable runnable) {
            Deque<String> snapshot = actual.currentContext().getOrDefault(REACTOR_CONTEXT_KEY, null);
            // 只有当调用方显式写入了 Reactor Context（快照非空）时才做"安装/恢复"。
            // 当 snapshot == null 时，我们不参与线程上下文管理：onComplete/doFinally 中
            // 业务代码可能自行 pop() 切换栈，如果此时强行恢复 previous 会把业务 restore
            // 的效果覆盖（测试 assertThat(current().isEmpty()) 失败）。
            if (snapshot == null || snapshot.isEmpty()) {
                runnable.run();
                return;
            }
            Deque<String> previous = snapshotStack();
            installStack(snapshot);
            try {
                runnable.run();
            } finally {
                installStack(previous);
            }
        }
    }

    private static final class DataSourceContextSubscription implements Subscription {

        private final Subscription delegate;
        private final DataSourceContextSubscriber<?> subscriber;

        private DataSourceContextSubscription(Subscription delegate, DataSourceContextSubscriber<?> subscriber) {
            this.delegate = delegate;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            subscriber.withContext(() -> delegate.request(n));
        }

        @Override
        public void cancel() {
            subscriber.withContext(delegate::cancel);
        }
    }

    private static final class DataSourceContextQueueSubscription<T> implements Fuseable.QueueSubscription<T> {

        private final Fuseable.QueueSubscription<T> delegate;
        private final DataSourceContextSubscriber<?> subscriber;

        private DataSourceContextQueueSubscription(
                Fuseable.QueueSubscription<T> delegate, DataSourceContextSubscriber<?> subscriber) {
            this.delegate = delegate;
            this.subscriber = subscriber;
        }

        @Override
        public void request(long n) {
            subscriber.withContext(() -> delegate.request(n));
        }

        @Override
        public void cancel() {
            subscriber.withContext(delegate::cancel);
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
                private final Iterator<T> it = delegate.iterator();

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public T next() {
                    if (!hasNext()) {
                        throw new NoSuchElementException();
                    }
                    return it.next();
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
}
