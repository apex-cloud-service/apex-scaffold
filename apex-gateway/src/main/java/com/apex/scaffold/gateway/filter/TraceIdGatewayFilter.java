package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.common.util.TraceIdUtil;
import java.util.UUID;
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
 * <p>TraceId 的唯一来源：客户端未携带时由网关注入，已携带时透传（便于跨系统联调）。
 * 注入后写入请求头与 MDC，供下游服务、Feign 拦截器、MQ 事件信封复用，
 * 使"HTTP 请求链路 + 异步消息链路"在 Zipkin / 日志平台可串联。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
public class TraceIdGatewayFilter implements GlobalFilter, Ordered {

    /** 追踪 ID 请求头名称 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String incoming = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID);
        String traceId = StringUtils.hasText(incoming) ? incoming : generateTraceId();

        ServerHttpRequest mutated =
                exchange.getRequest().mutate().header(HEADER_TRACE_ID, traceId).build();

        // 响应回写 TraceId，便于前端排查问题时直接上报
        exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, traceId);

        return chain.filter(exchange.mutate().request(mutated).build())
                .contextWrite(context -> context.put(TraceIdUtil.TRACE_ID_KEY, traceId))
                .doOnEach(signal -> {
                    if (signal.isOnComplete() || signal.isOnError()) {
                        MDC.remove(TraceIdUtil.TRACE_ID_KEY);
                    } else {
                        MDC.put(TraceIdUtil.TRACE_ID_KEY, traceId);
                    }
                });
    }

    /**
     * 生成无连字符的 32 位 TraceId.
     *
     * @return TraceId 字符串
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
