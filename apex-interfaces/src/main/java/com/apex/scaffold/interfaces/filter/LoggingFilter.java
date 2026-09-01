package com.apex.scaffold.interfaces.filter;

import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
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
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";

        if (shouldSkip(path)) {
            return chain.filter(exchange);
        }

        long startTime = System.currentTimeMillis();
        String queryString = request.getURI().getRawQuery();
        String traceId = (String) exchange.getAttributes().getOrDefault("traceId", "-");
        String userId = (String) exchange.getAttributes().getOrDefault("userId", "-");
        String clientIp = Optional.ofNullable(request.getRemoteAddress())
                .map(InetSocketAddress::getHostString)
                .orElse("-");

        HttpHeaders headers = request.getHeaders();
        String userAgent = toHeaderValue(headers, HttpHeaders.USER_AGENT, "-");
        String contentType = toHeaderValue(headers, HttpHeaders.CONTENT_TYPE, "-");
        String referer = toHeaderValue(headers, HttpHeaders.REFERER, "-");
        long requestSize = headers.getContentLength();

        AtomicBoolean errorLogged = new AtomicBoolean(false);

        return chain.filter(exchange)
                .doOnSuccess(v -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    ServerHttpResponse response = exchange.getResponse();
                    int statusCode = response.getStatusCode() != null
                            ? response.getStatusCode().value()
                            : 200;
                    long responseSize = response.getHeaders().getContentLength();

                    MDC.put("method", method);
                    MDC.put("uri", path);
                    MDC.put("clientIp", clientIp);
                    MDC.put("status", String.valueOf(statusCode));
                    MDC.put("duration", String.valueOf(durationMs));
                    MDC.put("userId", userId);

                    String detail = buildDetail(
                            method,
                            path,
                            statusCode,
                            durationMs,
                            queryString,
                            userAgent,
                            contentType,
                            referer,
                            requestSize,
                            responseSize);

                    if (statusCode >= HTTP_STATUS_CLIENT_ERROR_START) {
                        log.warn("请求完成(错误) | traceId={} | {}", traceId, detail);
                        errorLogged.set(true);
                    } else if (durationMs > SLOW_REQUEST_THRESHOLD_MS) {
                        log.warn("请求完成(慢请求) | traceId={} | {}", traceId, detail);
                    } else {
                        log.info("请求完成 | traceId={} | {}", traceId, detail);
                    }
                    clearMdc();
                })
                .doOnError(e -> {
                    long durationMs = System.currentTimeMillis() - startTime;

                    MDC.put("method", method);
                    MDC.put("uri", path);
                    MDC.put("clientIp", clientIp);
                    MDC.put("status", "500");
                    MDC.put("duration", String.valueOf(durationMs));
                    MDC.put("userId", userId);

                    if (!errorLogged.get()) {
                        log.error(
                                "请求异常 | traceId={} | method={} path={} duration={}ms "
                                        + "error={}: {} query={} UA={} CT={} referer={} reqSize={}B",
                                traceId,
                                method,
                                path,
                                durationMs,
                                e.getClass().getSimpleName(),
                                e.getMessage(),
                                queryString != null ? queryString : "",
                                userAgent,
                                contentType,
                                referer,
                                requestSize,
                                e);
                    }
                    clearMdc();
                });
    }

    /**
     * 构建请求详情摘要字符串。
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
        if (referer != null && !referer.isEmpty() && !"-".equals(referer)) {
            sb.append(" referer=").append(truncate(referer, 120));
        }
        return sb.toString();
    }

    /**
     * 安全读取请求头，不存在时返回默认值。
     */
    private static String toHeaderValue(HttpHeaders headers, String name, String defaultValue) {
        String value = headers.getFirst(name);
        return value != null && !value.isEmpty() ? value : defaultValue;
    }

    /**
     * 截断过长字符串，防止日志膨胀。
     */
    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return "-";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    /**
     * 清除 MDC 中的请求级字段。
     */
    private static void clearMdc() {
        MDC.remove("method");
        MDC.remove("uri");
        MDC.remove("clientIp");
        MDC.remove("status");
        MDC.remove("duration");
        MDC.remove("userId");
    }

    /**
     * 执行对应的业务处理；该操作由 {@code shouldSkip} 定义。
     *
     * @param path 方法输入 {@code path}，类型为 {@code String}
     * @return 满足条件时返回 {@code true}，否则返回 {@code false}
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
