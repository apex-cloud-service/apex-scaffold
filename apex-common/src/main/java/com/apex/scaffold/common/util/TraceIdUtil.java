package com.apex.scaffold.common.util;

import java.util.UUID;
import org.slf4j.MDC;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

/**
 * 追踪ID工具类
 *
 * <p>用于生成和管理分布式系统中的追踪ID，支持链路追踪。
 *
 * <p><b>WebFlux / Reactive 注意事项：</b>
 * 在响应式操作符链中，{@link #getTraceId()} 等基于 MDC（ThreadLocal）的方法
 * 在跨线程的异步操作符中可能无法正确获取 traceId。推荐在响应式链中使用
 * {@link #initContext()} 创建 Context 并通过 {@code .contextWrite()} 传播，
 * 然后使用 {@link #getTraceId(ContextView)} 在操作符内部安全地读取。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 */
public class TraceIdUtil {

    /**
     * 创建 {@code TraceIdUtil} 实例。
     */
    private TraceIdUtil() {
        throw new UnsupportedOperationException("Tool class should not be instantiated");
    }

    /**
     * 追踪 ID 在 MDC 与 Reactor Context 中使用的键名。
     *
     * <p>统一为 {@code "traceId"}，保证日志框架（MDC）与响应式上下文
     * 使用同一键进行读写。</p>
     */
    public static final String TRACE_ID_KEY = "traceId";

    /**
     * 从当前线程的 MDC 中获取追踪 ID。
     *
     * <p>如果当前未设置追踪 ID，则自动生成一个新的。
     *
     * <p><b>注意：</b>此方法依赖 MDC（ThreadLocal）。在 WebFlux 响应式操作符链中，
     * 当操作符在不同线程执行时可能返回不一致的结果。在响应式操作符内部，
     * 推荐使用 {@link #getTraceId(ContextView)} 方法。
     *
     * @return 追踪 ID 字符串
     */
    public static String getTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    /**
     * 在当前线程的 MDC 中设置追踪 ID。
     *
     * @param traceId 追踪 ID 字符串
     */
    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
    }

    /**
     * 从当前线程的 MDC 中清除追踪 ID。
     */
    public static void clear() {
        MDC.remove(TRACE_ID_KEY);
    }

    /**
     * 生成一个新的追踪 ID。
     *
     * <p>使用 UUID 去掉中划线后作为追踪 ID，保证全局唯一性。
     *
     * @return 新生成的追踪 ID 字符串
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 在当前线程的 MDC 中初始化追踪 ID。
     *
     * <p>如果当前已有追踪 ID 则不重复生成，否则自动生成一个新的。
     */
    public static void init() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            MDC.put(TRACE_ID_KEY, generateTraceId());
        }
    }

    // ──────────────── Reactor Context API ────────────────

    /**
     * 创建包含追踪 ID 的 Reactor {@link Context}。
     *
     * <p>如果 MDC 中已有追踪 ID 则复用，否则生成新的并存入 MDC。
     * 返回的 Context 可通过 {@code .contextWrite()} 传播到响应式操作符链中。
     *
     * @return 包含追踪 ID 的 Reactor Context
     */
    public static Context initContext() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null) {
            traceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return Context.of(TRACE_ID_KEY, traceId);
    }

    /**
     * 使用指定的追踪 ID 创建 Reactor {@link Context}。
     *
     * <p>同时将追踪 ID 写入 MDC 以保证一致性。
     *
     * @param traceId 要设置的追踪 ID
     * @return 包含追踪 ID 的 Reactor Context
     */
    public static Context withTraceIdContext(String traceId) {
        MDC.put(TRACE_ID_KEY, traceId);
        return Context.of(TRACE_ID_KEY, traceId);
    }

    /**
     * 从 Reactor {@link ContextView} 中读取追踪 ID。
     *
     * <p>优先从 ContextView 中读取；若不存在则回退到 {@link #getTraceId()}。
     *
     * @param ctx Reactor ContextView（从 {@code Mono.deferContextual()} 等获取）
     * @return 追踪 ID，不会返回 null
     */
    public static String getTraceId(ContextView ctx) {
        if (ctx.hasKey(TRACE_ID_KEY)) {
            return ctx.get(TRACE_ID_KEY);
        }
        return getTraceId();
    }
}
