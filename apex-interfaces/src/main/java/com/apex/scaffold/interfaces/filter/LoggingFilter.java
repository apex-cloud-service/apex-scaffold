package com.apex.scaffold.interfaces.filter;

import java.net.InetSocketAddress;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 请求/响应日志过滤器，记录完整的 HTTP 请求元数据。
 *
 * <p>在 REQUEST_FILE appender 中输出每个业务请求的结构化详情，
 * 涵盖请求头、查询参数、响应体大小和异常堆栈，方便联调与排障。</p>
 *
 * <h2>MDC 上下文</h2>
 * <p>在请求处理开始时将 {@code traceId}（Micrometer Tracing 注入的链路 ID）与
 * {@code requestId}（{@code ApiGatewayFilter} 生成的请求 ID）写入 MDC，使控制台与全部
 * 文件 Appender 的 pattern 统一携带链路标识；响应完成后在 {@code doOnSuccess}/{@code doOnError}
 * 中写入 method/uri/status/duration/clientIp/userId 并统一清理，避免上下文残留。</p>
 *
 * <h2>记录内容</h2>
 * <table>
 *   <tr><th>类别</th><th>字段</th><th>示例</th></tr>
 *   <tr><td rowspan="4">请求头</td><td>User-Agent</td><td>Mozilla/5.0 ...</td></tr>
 *   <tr><td>Content-Type</td><td>application/json</td></tr>
 *   <tr><td>Referer</td><td>https://app.example.com</td></tr>
 *   <tr><td>Content-Length</td><td>256</td></tr>
 *   <tr><td>查询参数</td><td>query</td><td>?page=1&amp;size=10</td></tr>
 *   <tr><td rowspan="2">响应</td><td>status/duration</td><td>200/97ms</td></tr>
 *   <tr><td>responseSize</td><td>Content-Length 响应头值</td></tr>
 *   <tr><td>异常</td><td>error</td><td>完整的异常类名+消息+堆栈</td></tr>
 * </table>
 *
 * @author Apex Team
 * @since 2.1.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class LoggingFilter implements WebFilter {

    private static final String REFERER_PLACEHOLDER = "-";

    private static final String[] EXCLUDED_PREFIXES = {
        "/actuator", "/health", "/favicon.ico", "/swagger-ui", "/v3/api-docs", "/webjars", "/doc.html"
    };

    private static final long SLOW_REQUEST_THRESHOLD_MS = 3000L;
    private static final int HTTP_STATUS_CLIENT_ERROR_START = 400;

    /**
     * {@inheritDoc}
     *
     * @param exchange 当前请求-响应交换对象
     * @param chain    过滤器链
     * @return 过滤链执行结果
     */
    @Override
    @SuppressWarnings("PMD.MethodTooLongRule")
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";

        if (shouldSkip(path)) {
            return chain.filter(exchange);
        }

        return Mono.defer(() -> {
            long startTime = System.nanoTime();
            return Mono.defer(() -> chain.filter(exchange))
                    .doOnSuccess(v -> logRequest(exchange, method, path, startTime, null))
                    .doOnError(error -> logRequest(exchange, method, path, startTime, error));
        });
    }

    private void logRequest(ServerWebExchange exchange, String method, String path, long startTime, Throwable error) {
        int status = error != null
                ? 500
                : exchange.getResponse().getStatusCode() == null
                        ? 200
                        : exchange.getResponse().getStatusCode().value();
        long duration = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
        boolean warning = status >= HTTP_STATUS_CLIENT_ERROR_START || duration > SLOW_REQUEST_THRESHOLD_MS;
        if (error != null ? !log.isErrorEnabled() : warning ? !log.isWarnEnabled() : !log.isInfoEnabled()) {
            return;
        }
        java.util.Map<String, String> previous = MDC.getCopyOfContextMap();
        try {
            MDC.put("traceId", safeAttribute(exchange, "traceId"));
            MDC.put("requestId", safeAttribute(exchange, "requestId"));
            MDC.put("userId", safeAttribute(exchange, "userId"));
            MDC.put("method", method);
            MDC.put("uri", com.apex.scaffold.common.util.LogSafetyUtil.text(path, 256));
            MDC.put("status", String.valueOf(status));
            MDC.put("duration", String.valueOf(duration));
            MDC.put(
                    "clientIp",
                    com.apex.scaffold.common.util.LogSafetyUtil.text(
                            Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                                    .map(InetSocketAddress::getHostString)
                                    .orElse("-"),
                            64));
            HttpHeaders headers = exchange.getRequest().getHeaders();
            String detail = buildDetail(
                    method,
                    com.apex.scaffold.common.util.LogSafetyUtil.text(path, 256),
                    status,
                    duration,
                    null,
                    toHeaderValue(headers, HttpHeaders.USER_AGENT, "-"),
                    toHeaderValue(headers, HttpHeaders.CONTENT_TYPE, "-"),
                    com.apex.scaffold.common.util.LogSafetyUtil.uri(headers.getFirst(HttpHeaders.REFERER), 120),
                    headers.getContentLength(),
                    exchange.getResponse().getHeaders().getContentLength());
            if (error != null) {
                log.error("请求异常 | {} error={}", detail, error.getClass().getSimpleName());
            } else if (warning) {
                log.warn("请求完成 | {}", detail);
            } else {
                log.info("请求完成 | {}", detail);
            }
        } finally {
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }

    private String safeAttribute(ServerWebExchange exchange, String name) {
        Object value = exchange.getAttribute(name);
        return com.apex.scaffold.common.util.LogSafetyUtil.text(value instanceof String text ? text : "-", 64);
    }

    /**
     * 构建请求详情摘要字符串。
     *
     * <p>将请求的关键信息（方法、路径、状态码、耗时、请求/响应体大小、User-Agent、Content-Type、Referer）
     * 格式化为单行日志摘要，便于日志分析和问题排查。
     *
     * @param method       HTTP 方法
     * @param path         请求路径
     * @param statusCode   响应状态码
     * @param durationMs   响应耗时（毫秒）
     * @param query        查询参数字符串
     * @param userAgent    User-Agent 请求头
     * @param contentType  Content-Type 请求头
     * @param referer      Referer 请求头
     * @param requestSize  请求体大小（字节）
     * @param responseSize 响应体大小（字节）
     * @return 格式化的详情字符串
     */
    private String buildDetail(
            String method,
            String path,
            int statusCode,
            long durationMs,
            String query,
            String userAgent,
            String contentType,
            String referer,
            long requestSize,
            long responseSize) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("method=").append(method).append(" path=").append(path);
        if (query != null && !query.isEmpty()) {
            sb.append("?").append(query);
        }
        sb.append(" status=")
                .append(statusCode)
                .append(" duration=")
                .append(durationMs)
                .append("ms")
                .append(" reqSize=")
                .append(requestSize)
                .append("B")
                .append(" respSize=")
                .append(responseSize)
                .append("B")
                .append(" UA=")
                .append(truncate(userAgent, 80))
                .append(" CT=")
                .append(truncate(contentType, 60));
        if (referer != null && !referer.isEmpty() && !REFERER_PLACEHOLDER.equals(referer)) {
            sb.append(" referer=").append(truncate(referer, 120));
        }
        return sb.toString();
    }

    /**
     * 安全读取请求头，不存在时返回默认值。
     *
     * <p>封装请求头读取逻辑，当请求头不存在或为空字符串时返回指定的默认值，
     * 避免日志中出现 null 值。
     *
     * @param headers      HTTP 请求头集合
     * @param name         请求头名称
     * @param defaultValue 默认值
     * @return 请求头值或默认值
     */
    private static String toHeaderValue(HttpHeaders headers, String name, String defaultValue) {
        String value = headers.getFirst(name);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }

    /**
     * 截断过长字符串，防止日志膨胀。
     *
     * <p>当字符串长度超过指定最大长度时，截取前 maxLen 个字符并在末尾添加 "..."，
     * 避免过长的 User-Agent、Referer 等字段导致日志行过长。
     *
     * @param value  原始字符串
     * @param maxLen 最大长度
     * @return 截断后的字符串，null 值返回 "-"
     */
    private static String truncate(String value, int maxLen) {
        return com.apex.scaffold.common.util.LogSafetyUtil.text(value, maxLen);
    }

    /**
     * 清除 MDC 中的请求级字段，防止上下文残留到其他请求。
     *
     * <p>在请求处理结束后（{@code doOnSuccess}/{@code doOnError} 中）调用，
     * 清除 traceId、requestId、method、uri、clientIp、status、duration、userId 等字段，
     * 避免 Reactor 线程复用时将上一个请求的 MDC 上下文泄露到下一个请求。
     */
    private static void clearMdc() {
        MDC.remove("traceId");
        MDC.remove("requestId");
        MDC.remove("method");
        MDC.remove("uri");
        MDC.remove("clientIp");
        MDC.remove("status");
        MDC.remove("duration");
        MDC.remove("userId");
    }

    /**
     * 判断请求路径是否应跳过日志记录。
     *
     * <p>对于系统监控端点（/actuator、/health、/swagger-ui 等）和静态资源路径，
     * 跳过详细日志记录，避免日志膨胀和监控流量干扰。
     *
     * @param path 请求路径
     * @return 如果应跳过日志记录返回 true
     */
    private boolean shouldSkip(String path) {
        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
