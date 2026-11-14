package com.apex.scaffold.interfaces.filter;

import com.apex.scaffold.common.util.TraceIdUtil;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 链路追踪过滤器（最高优先级）。
 *
 * <p>作为请求链中的第一个业务过滤器（{@code @Order(HIGHEST_PRECEDENCE)}），
 * 在请求入口处统一处理分布式链路追踪标识（TraceId）的生成与传播：
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li><b>提取 TraceId</b>：优先从请求头 {@code X-Trace-Id} 获取上游传递的 TraceId，
 *       实现跨服务链路串联</li>
 *   <li><b>生成 TraceId</b>：若请求头中不存在，自动生成新的 UUID（去连字符）作为 TraceId</li>
 *   <li><b>写入 MDC</b>：将 TraceId 写入 SLF4J 的 {@link org.slf4j.MDC}，
 *       确保整个请求生命周期内的日志自动携带 TraceId</li>
 *   <li><b>响应头回传</b>：将 TraceId 写入响应头 {@code X-Trace-Id}，
 *       便于客户端进行问题追踪</li>
 *   <li><b>Reactor Context 传播</b>：通过 {@code contextWrite} 将 TraceId
 *       写入 Reactor Context，供响应式链路中下游算子使用</li>
 *   <li><b>自动清理</b>：请求结束时通过 {@code doFinally} 清除 MDC 和 TraceIdUtil 中的 TraceId，
 *       防止线程复用时 TraceId 泄露到下一个请求</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>由于 WebFlux 的响应式线程模型不再依赖 ThreadLocal，
 * TraceId 通过 Reactor Context 在整个响应式链路中传递。下游 Handler 可通过
 * {@code Mono.deferContextual(ctx -> ctx.get(TraceIdUtil.TRACE_ID_KEY))} 获取。</p>
 *
 * <h2>设计说明</h2>
 * <p>本过滤器在过滤器链的最前端执行（{@link org.springframework.core.Ordered#HIGHEST_PRECEDENCE}），
 * 确保 TraceId 在 JWT 认证、日志记录、安全校验等所有后续处理之前就绪。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.common.util.TraceIdUtil
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceFilter implements WebFilter {

    /** TraceId 请求头名称，用于上下游服务间传递 */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** ServerWebExchange attributes 中存储 TraceId 的键名 */
    private static final String TRACE_ID_ATTR = "traceId";

    /**
     * 处理链路追踪：提取/生成 TraceId 并传播。
     *
     * <ol>
     *   <li>从请求头 {@code X-Trace-Id} 读取 TraceId，不存在则生成新的 UUID</li>
     *   <li>设置到 {@link com.apex.scaffold.common.util.TraceIdUtil} 和交换对象 attributes</li>
     *   <li>将 TraceId 写入响应头 {@code X-Trace-Id}</li>
     *   <li>通过 {@code contextWrite} 将 TraceId 写入 Reactor Context</li>
     *   <li>请求结束时通过 {@code doFinally} 清理 MDC 和 TraceIdUtil</li>
     * </ol>
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain    WebFlux 过滤器链
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 从请求头中获取TraceId，如果没有则生成新的
        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
        }

        exchange.getAttributes().put(TRACE_ID_ATTR, traceId);

        // 将TraceId添加到响应头
        String finalTraceId = traceId;
        exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        logWithTraceId(finalTraceId, "TraceId设置完成: {}");

        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(TraceIdUtil.TRACE_ID_KEY, finalTraceId))
                .doFinally(signalType -> logWithTraceId(finalTraceId, "TraceId已清除: {}"));
    }

    /**
     * 在日志回调内绑定请求标识并恢复当前线程的原标识。
     *
     * @param traceId 请求链路标识
     * @param message 日志模板
     */
    private void logWithTraceId(String traceId, String message) {
        String previous = MDC.get(TraceIdUtil.TRACE_ID_KEY);
        try {
            MDC.put(TraceIdUtil.TRACE_ID_KEY, traceId);
            log.debug(message, traceId);
        } finally {
            if (previous == null) {
                MDC.remove(TraceIdUtil.TRACE_ID_KEY);
            } else {
                MDC.put(TraceIdUtil.TRACE_ID_KEY, previous);
            }
        }
    }

    /**
     * 生成新的 TraceId（UUID 格式，去连字符）。
     *
     * <p>当请求头中不存在上游传递的 TraceId 时，本方法生成一个新的全局唯一标识符，
     * 作为当前请求链路的起始 TraceId。
     *
     * @return 32 位十六进制字符串（UUID 去除连字符后的形式）
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
