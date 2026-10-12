package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.common.util.TraceIdUtil;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 链路追踪 ID 注入过滤器（全局，最先执行）.
 *
 * <p>对应迁移方案 §5.3 过滤器链第 1 位（order = -1）。
 *
 * <p>职责：
 * <ol>
 *   <li>客户端未携带 {@code X-Trace-Id} 时由网关注入（复用 {@link TraceIdUtil} 生成规则）</li>
 *   <li>已携带时原样透传，便于跨系统联调与链路串联</li>
 *   <li>写入请求头、响应头、MDC 与 Reactor Context，供下游服务 / Feign 拦截器 /
 *       MQ 事件信封复用，使「HTTP 链路 + 异步消息链路」在 SkyWalking / 日志平台可串联</li>
 * </ol>
 *
 * <p><b>响应式说明</b>：TraceId 通过 {@code contextWrite} 写入 Reactor Context，
 * 保证跨线程操作符内可安全读取（见 {@link TraceIdUtil#getTraceId(reactor.util.context.ContextView)}）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
public class TraceWebFilter implements GlobalFilter, Ordered {

    /** 追踪 ID 请求头名称 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /** 过滤器顺序：最先执行，保证后续所有过滤器与下游均可见 TraceId */
    public static final int ORDER = -1;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 优先透传客户端 TraceId（兼容跨系统联调场景），否则生成新的
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID);
        String traceId = StringUtils.hasText(incoming) ? incoming : TraceIdUtil.generateTraceId();

        ServerHttpRequest mutated =
                exchange.getRequest().mutate().header(HEADER_TRACE_ID, traceId).build();

        // 响应回写 TraceId，便于前端排查问题时直接上报
        exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, traceId);

        return chain.filter(exchange.mutate().request(mutated).build())
                .contextWrite(context -> context.put(TraceIdUtil.TRACE_ID_KEY, traceId))
                .doOnEach(signal -> {
                    if (signal.isOnComplete() || signal.isOnError()) {
                        // 请求结束清理 MDC，避免线程复用时 TraceId 串味
                        MDC.remove(TraceIdUtil.TRACE_ID_KEY);
                    } else {
                        MDC.put(TraceIdUtil.TRACE_ID_KEY, traceId);
                    }
                });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
