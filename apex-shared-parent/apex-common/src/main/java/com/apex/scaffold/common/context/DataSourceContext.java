package com.apex.scaffold.common.context;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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
 * <h2>线程安全性</h2>
 * <ul>
 *   <li>在 {@link #initializeGlobalHooks()} 中挂载全局
 *       {@link Schedulers#onScheduleHook}：任务提交到 Scheduler 时捕获当前线程的
 *       数据源栈快照，在执行线程内重建后运行，finally 清理，解决
 *       BlockingIoSchedulers.jdbc() 等跨线程切换时数据源丢失。</li>
 *   <li>显式的 {@link #withDataSource(Mono)} / {@link #withDataSource(Flux)} 桥接：
 *       在订阅阶段从 Reactor Context（{@link #REACTOR_CONTEXT_KEY}）读取栈快照并安装，
 *       执行完毕后自动恢复。</li>
 * </ul>
 *
 * <p><b>为何不再注册全局 {@code Hooks.onEachOperator}：</b>全局按操作符 lift
 * {@code CoreSubscriber} 会把 WebFlux 路由与 Sentinel 内部的
 * {@code FluxConcatMap$ConcatMapInner}、{@code MonoNext} 等订阅者一并包装。这些内部
 * 订阅者的 {@code currentContext()}/{@code cancel()} 会经由其 {@code parent} 反向引用回环，
 * 与包装后的 {@code actual} 链形成环状图，导致 {@code currentContext()} 与 {@code cancel()}
 * 无限互递归并抛出 {@link StackOverflowError}。</p>
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

    static {
        initializeGlobalHooks();
    }

    /** 幂等初始化全局 Scheduler Hook（加载类时自动调用）。 */
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
    }

    /** 重置全局 Scheduler Hook（测试/容器销毁清理用）。 */
    public static void resetGlobalHooks() {
        Schedulers.resetOnScheduleHook(SCHEDULER_HOOK_KEY);
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
}
