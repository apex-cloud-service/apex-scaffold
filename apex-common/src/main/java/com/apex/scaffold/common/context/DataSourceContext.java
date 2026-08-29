package com.apex.scaffold.common.context;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

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
 * <p>响应式场景下配合响应式感知的 {@code DataSourceSwitchAspect} 使用；
 * 请求结束必须调用 {@link #clear()} 释放，防止线程池复用导致数据源串库。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.3.0
 */
public final class DataSourceContext {

    /** ThreadLocal 数据源切换栈 */
    private static final ThreadLocal<Deque<String>> STACK = new ThreadLocal<>();

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

    /**
     * 弹出栈顶数据源（回到上一层数据源）。
     */
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
     * 获取当前线程的切换栈（懒创建）。
     */
    private static Deque<String> deque() {
        Deque<String> stack = STACK.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            STACK.set(stack);
        }
        return stack;
    }
}
