package com.apex.scaffold.interfaces.filter;

import brave.Span;
import brave.Tracer;
import brave.propagation.TraceContext;
import com.apex.scaffold.common.util.RequestUtil;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 基于 Brave 的 WebFlux 链路追踪过滤器。
 *
 * <p>为进入的每个 HTTP 请求创建 SERVER 类型的 Span，并补充 HTTP 标签
 * （method/path/status_code）。Span 完成后由 {@code TraceConfig} 注册的
 * {@link brave.handler.SpanHandler} 处理：写日志（zipkin.log）与上报 Zipkin。
 *
 * <h2>链路 TraceId 一致策略</h2>
 * <p>本过滤器排在 {@link TraceFilter} 之后（{@code @Order(HIGHEST_PRECEDENCE + 1)}），
 * 复用 {@link TraceFilter} 已生成并放入 exchange attribute 的 TraceId 作为
 * Span 的 TraceId，保证响应头 X-Trace-Id、MDC 日志与 Zipkin 中的 TraceId 一致。
 * 若 TraceId 缺失或不合法，则回退由 Brave 自行生成新的根 Span。</p>
 *
 * <h2>Span 生命周期</h2>
 * <ul>
 *   <li>请求进入时创建并启动 Span</li>
 *   <li>响应完成时（含异常）补充 http.status_code 标签并结束 Span</li>
 *   <li>错误响应额外标记 error 标签</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@RequiredArgsConstructor
public class BraveWebTracingFilter implements WebFilter {

    /** TraceFilter 写入 exchange attribute 的 TraceId 键名，与 TraceFilter 保持一致 */
    private static final String TRACE_ID_ATTR = "traceId";

    /** 无法获取客户端 IP 时的标识，与 RequestUtil 保持一致 */
    private static final String UNKNOWN_IP = "unknown";

    /** 合法 TraceId 的十六进制长度（128-bit 无分隔符） */
    private static final int TRACE_ID_HEX_LENGTH = 32;

    /** Brave 追踪器，由 TraceConfig 提供 */
    private final Tracer tracer;

    /**
     * 创建 SERVER Span 并贯穿整个请求生命周期。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain    WebFlux 过滤器链
     * @return 表示过滤完成的 {@link Mono}{@code <Void>}
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        Span span = startServerSpan(exchange, request);
        exchange.getAttributes().put(BraveWebTracingFilter.class.getName() + ".span", span);

        return chain.filter(exchange).doFinally(signalType -> finishServerSpan(exchange, span));
    }

    /**
     * 启动 SERVER Span，并设置 HTTP 基础标签。
     *
     * <p>优先复用 exchange attribute 中的 TraceId（由 {@link TraceFilter} 生成），
     * 使日志、响应头与 Zipkin 的 TraceId 保持一致。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param request  当前 HTTP 请求
     * @return 已启动的 Span
     */
    private Span startServerSpan(ServerWebExchange exchange, ServerHttpRequest request) {
        Span span = createSpanFromTraceId(exchange);
        span.kind(Span.Kind.SERVER)
                .name(request.getMethod().name() + " "
                        + request.getPath().pathWithinApplication().value())
                .tag("http.method", request.getMethod().name())
                .tag("http.path", request.getPath().pathWithinApplication().value());

        Optional.ofNullable(RequestUtil.getClientIp(request))
                .filter(clientIp -> !UNKNOWN_IP.equals(clientIp))
                .ifPresent(clientIp -> span.remoteIpAndPort(clientIp, 0));
        span.start();
        return span;
    }

    /**
     * 根据 TraceId 创建 Span。
     *
     * <p>通过 Optional 管道依次完成：类型转换、合法性过滤、根上下文构建，
     * 任一环节缺失时回退到 Brave 的 nextSpan 生成新根 Span（按采样率采样）。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @return 已创建但未启动的 Span
     */
    private Span createSpanFromTraceId(ServerWebExchange exchange) {
        return Optional.ofNullable(exchange.getAttributes().get(TRACE_ID_ATTR))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(BraveWebTracingFilter::isValidTraceId)
                .flatMap(BraveWebTracingFilter::buildRootContext)
                .map(tracer::newChild)
                .orElseGet(tracer::nextSpan);
    }

    /**
     * 校验 TraceId 是否为合法的 128-bit 十六进制字符串。
     *
     * @param traceId 待校验的 TraceId
     * @return 合法返回 true
     */
    private static boolean isValidTraceId(String traceId) {
        return traceId != null
                && traceId.length() == TRACE_ID_HEX_LENGTH
                && traceId.chars().allMatch(BraveWebTracingFilter::isHexDigit);
    }

    /**
     * 判断字符是否为十六进制数字。
     *
     * @param codePoint 字符的 Unicode 码点
     * @return 十六进制数字返回 true
     */
    private static boolean isHexDigit(int codePoint) {
        return (codePoint >= '0' && codePoint <= '9')
                || (codePoint >= 'a' && codePoint <= 'f')
                || (codePoint >= 'A' && codePoint <= 'F');
    }

    /**
     * 基于已有 TraceId 构建根上下文。
     *
     * <p>TraceId 全 0 属于非法值，此时返回空 Optional 交由调用方回退。
     * SpanId 通过与 1 做或运算保证非 0，避免构建非法上下文。
     *
     * @param traceId 合法的 128-bit 十六进制 TraceId
     * @return 根上下文，非法时返回空 Optional
     */
    private static Optional<TraceContext> buildRootContext(String traceId) {
        long traceIdHigh = Long.parseUnsignedLong(traceId.substring(0, 16), 16);
        long traceIdLow = Long.parseUnsignedLong(traceId.substring(16), 16);
        return Stream.of(traceIdHigh, traceIdLow)
                .filter(value -> value != 0L)
                .findAny()
                .map(ignored -> TraceContext.newBuilder()
                        .traceIdHigh(traceIdHigh)
                        .traceId(traceIdLow)
                        .spanId(ThreadLocalRandom.current().nextLong() | 1L)
                        .sampled(true)
                        .build());
    }

    /**
     * 结束 SERVER Span，补充 http.status_code 与错误标签。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param span     待结束的 Span
     */
    private void finishServerSpan(ServerWebExchange exchange, Span span) {
        Optional<HttpStatusCode> statusCode =
                Optional.ofNullable(exchange.getResponse().getStatusCode());
        statusCode.map(HttpStatusCode::value).ifPresent(code -> span.tag("http.status_code", String.valueOf(code)));
        statusCode
                .map(HttpStatusCode::value)
                .filter(code -> code >= 400)
                .ifPresent(code -> span.tag("error", String.valueOf(code)));
        span.finish();
    }
}
