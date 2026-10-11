package com.apex.scaffold.gateway.fallback;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 网关熔断降级兜底处理器.
 *
 * <p>当下游服务不可用或 Resilience4j 熔断器处于 OPEN 状态时，Spring Cloud Gateway
 * 会将请求转发至 {@code forward:/__gateway/fallback}，由本处理器返回统一的
 * {@link Result} 错误响应，避免把下游原始异常（堆栈 / 内部细节）直接暴露给调用方。
 *
 * <p><b>响应式说明</b>：采用函数式 {@code Handler} 而非 {@code @RestController}，
 * 与网关「WebFlux 响应式 + 无阻塞」的栈保持一致（见 {@code GatewayArchTest} 约束）。
 *
 * <p><b>TraceId 透传</b>：优先读取请求头 {@code X-Trace-Id}（由
 * {@code TraceWebFilter} 注入），并回写到响应头，便于前端与日志平台串联排查。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
@Component
public class FallbackHandler {

    /** 追踪 ID 请求/响应头名称，与 {@code TraceWebFilter.HEADER_TRACE_ID} 保持一致 */
    public static final String HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * 处理降级请求，写入统一错误码并透传 TraceId.
     *
     * @param exchange 当前请求交换上下文（含熔断异常属性）
     * @return 状态码 503、响应体为统一 {@link Result} 的响应式结果
     */
    public Mono<ServerResponse> fallback(ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);
        String traceId = exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID);

        log.warn(
                "网关熔断降级触发 uri={} traceId={} cause={}",
                exchange.getRequest().getURI().getRawPath(),
                traceId,
                cause == null ? "unknown" : cause.getClass().getSimpleName());

        Result<Void> body = Result.error(ResultCode.SERVICE_UNAVAILABLE, traceId);
        return ServerResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_TRACE_ID, traceId == null ? "" : traceId)
                .bodyValue(body);
    }
}
