package com.apex.scaffold.interfaces.filter;

import com.apex.scaffold.domain.gateway.CircuitBreakerPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * API 网关过滤器。
 *
 * <p>位于 WebFlux 过滤器链的最前端（{@code @Order(HIGHEST_PRECEDENCE + 20)}），对全部到达的
 * HTTP 请求提供统一的网关能力：请求追踪、指标采集、错误统计与日志记录。作为应用内"API 网关层"
 * 的实现，覆盖含被鉴权/限流拦截在内的所有请求，其指标可识别互联网扫描流量等非业务请求。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>请求追踪：生成或透传 {@code X-Request-Id}，写入 MDC 供日志链路关联</li>
 *   <li>指标采集：通过 Micrometer 输出请求计数、错误计数与响应时间百分位直方图</li>
 *   <li>故障隔离：预留断路器服务依赖，为后续熔断降级提供扩展点</li>
 *   <li>请求日志：按慢请求、错误响应、正常响应分级输出，控制日志噪音</li>
 * </ul>
 *
 * <h2>监控指标</h2>
 * <ul>
 *   <li>{@code apex_gateway_request_count_total}：网关请求总数（带 method/path/status/statusClass 标签）</li>
 *   <li>{@code apex_gateway_error_count_total}：网关错误响应计数（4xx/5xx，带标签）</li>
 *   <li>{@code apex_gateway_response_time_seconds}：响应时间 Timer（含百分位直方图，支持 P50/P95/P99）</li>
 * </ul>
 *
 * <h2>实现风格</h2>
 * <p>采用函数式风格实现：请求上下文与统计快照由不可变 {@code record} 承载；路径归一化、状态码
 * 分类、标签构建等为无副作用纯函数；指标登记与日志输出等副作用收敛于独立的消费方法；响应式链路
 * 通过 {@code doFinally}/{@code doOnError} 声明式编排，不依赖可变的中间状态。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class ApiGatewayFilter implements WebFilter {

    /** 需要排除的路径前缀（不参与网关统计） */
    private static final String[] EXCLUDED_PREFIXES = {"/actuator", "/health", "/favicon.ico"};

    /** 业务 API 路径前缀，用于路径归一化判断 */
    private static final String API_PREFIX = "/api";

    /** 请求计数指标名称 */
    private static final String METRIC_REQUEST_COUNT = "apex.gateway.request.count";

    /** 响应时间指标名称 */
    private static final String METRIC_RESPONSE_TIME = "apex.gateway.response.time";

    /** 错误计数指标名称 */
    private static final String METRIC_ERROR_COUNT = "apex.gateway.error.count";

    /** 慢请求阈值（毫秒） */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 3000L;

    /** HTTP 状态码常量 - 信息性响应起始 */
    private static final int HTTP_STATUS_INFORMATIONAL_START = 100;

    /** HTTP 状态码常量 - 成功响应起始 */
    private static final int HTTP_STATUS_SUCCESS_START = 200;

    /** HTTP 状态码常量 - 重定向响应起始 */
    private static final int HTTP_STATUS_REDIRECT_START = 300;

    /** HTTP 状态码常量 - 客户端错误起始 */
    private static final int HTTP_STATUS_CLIENT_ERROR_START = 400;

    /** HTTP 状态码常量 - 服务端错误起始 */
    private static final int HTTP_STATUS_SERVER_ERROR_START = 500;

    /** 请求 ID 头名称 */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 追踪 ID 头名称 */
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    /** Micrometer 指标注册表 */
    private final MeterRegistry meterRegistry;

    /** 断路器服务（预留，用于故障隔离扩展） */
    private final CircuitBreakerPort circuitBreakerService;

    /**
     * 构造 API 网关过滤器。
     *
     * @param meterRegistry         Micrometer 指标注册表
     * @param circuitBreakerService 断路器服务
     */
    public ApiGatewayFilter(MeterRegistry meterRegistry, CircuitBreakerPort circuitBreakerService) {
        this.meterRegistry = meterRegistry;
        this.circuitBreakerService = circuitBreakerService;
    }

    /**
     * 请求上下文（不可变值对象）。
     *
     * <p>封装单个请求在网关层处理所需的全部状态：请求 ID、方法、路径、开始时间戳，
     * 以及交换对象，供请求完成与异常路径统一消费。
     *
     * @param requestId 请求 ID
     * @param method    HTTP 方法
     * @param path      请求路径
     * @param startTime 请求开始时间（纳秒）
     * @param exchange  请求-响应交换对象
     */
    private record RequestContext(
            String requestId, String method, String path, long startTime, ServerWebExchange exchange) {}

    /**
     * 请求统计快照（不可变值对象）。
     *
     * <p>由请求上下文与响应状态计算得出，供指标登记与请求完成日志消费。
     *
     * @param requestId  请求 ID
     * @param method     HTTP 方法
     * @param path       请求路径
     * @param statusCode HTTP 状态码
     * @param durationNs 响应耗时（纳秒）
     * @param durationMs 响应耗时（毫秒）
     */
    private record RequestStats(
            String requestId, String method, String path, int statusCode, long durationNs, long durationMs) {

        /**
         * 从请求上下文计算统计快照（纯函数，无副作用）。
         *
         * <p>响应状态码为空时按 500 处理；耗时由请求开始时间与当前时间差计算。
         *
         * @param context   请求上下文
         * @param signalType 请求结束信号
         * @return 统计快照
         */
        private static RequestStats of(RequestContext context, SignalType signalType) {
            ServerHttpResponse response = context.exchange().getResponse();
            long duration = System.nanoTime() - context.startTime();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(duration);
            int statusCode = response.getStatusCode() != null
                    ? response.getStatusCode().value()
                    : fallbackStatusCode(signalType);
            return new RequestStats(
                    context.requestId(), context.method(), context.path(), statusCode, duration, durationMs);
        }

        /**
         * 在响应没有显式状态码时，根据响应式结束信号推断最小可信状态。
         *
         * <p>正常完成遵循 HTTP 默认成功语义；异常使用 500；客户端取消使用常见的 499 约定，
         * 避免把客户端主动断开错误地计入服务端 5xx。</p>
         *
         * @param signalType 响应式请求结束信号
         * @return 无显式 HTTP 状态码时使用的状态码
         */
        private static int fallbackStatusCode(SignalType signalType) {
            return switch (signalType) {
                case ON_ERROR -> 500;
                case CANCEL -> 499;
                default -> 200;
            };
        }
    }

    /**
     * 过滤请求并执行网关处理逻辑。
     *
     * <p>整体采用响应式链式编排：{@code doFinally} 在请求结束后（无论成功、失败或取消）登记指标；
     * {@code doOnError} 在链路抛出异常时记录错误上下文并保留原始错误，避免重新执行过滤器链。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain    WebFilter 链
     * @return 响应式处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();
        String method = request.getMethod().name();

        // 排除非业务路径，不参与网关统计
        if (shouldSkip(path)) {
            return chain.filter(exchange);
        }

        RequestContext context = buildContext(exchange, method, path);
        logRequestStart(context, request);

        return chain.filter(exchange)
                .doFinally(signalType -> recordMetrics(RequestStats.of(context, signalType)))
                .doOnError(error -> recordRequestError(error, context));
    }

    /**
     * 构建请求上下文（不可变值对象）。
     *
     * <p>生成或透传请求 ID，并写入 exchange attributes、MDC 与响应头，供下游过滤器与日志链路使用。
     *
     * @param exchange 当前请求-响应交换对象
     * @param method   请求方法
     * @param path     请求路径
     * @return 请求上下文
     */
    private RequestContext buildContext(ServerWebExchange exchange, String method, String path) {
        String requestId = generateRequestId(exchange.getRequest());
        exchange.getAttributes().put("requestId", requestId);
        MDC.put("requestId", requestId);
        exchange.getResponse().getHeaders().set(REQUEST_ID_HEADER, requestId);
        return new RequestContext(requestId, method, path, System.nanoTime(), exchange);
    }

    /**
     * 记录过滤器链异常（副作用方法）。
     *
     * <p>记录错误耗时业务指标与异常日志后保留原始异常，让 WebFlux 的统一异常处理器负责生成最终
     * 响应。错误响应计数由 {@code doFinally} 中的 {@link #recordMetrics(RequestStats)} 按状态码标签
     * 统一统计，此处不再重复登记，避免错误计数被重复累加。
     *
     * @param error   过滤器链抛出的异常
     * @param context 请求上下文
     */
    private void recordRequestError(Throwable error, RequestContext context) {
        log.error(
                "API网关请求异常, requestId: {}, method: {}, path: {}, error: {}",
                context.requestId(),
                context.method(),
                context.path(),
                error.getMessage(),
                error);
    }

    /**
     * 登记请求指标（副作用方法）。
     *
     * <p>所有指标均携带 method/path/status/statusClass 标签，支持按维度分解查询；响应时间 Timer
     * 发布百分位直方图，可通过 histogram_quantile 计算 P50/P95/P99。
     *
     * @param stats 请求统计快照
     */
    private void recordMetrics(RequestStats stats) {
        List<Tag> tags = buildTags(stats.method(), stats.path(), stats.statusCode());

        // 请求计数（带标签，支持按 method/path/status/statusClass 分解）
        meterRegistry.counter(METRIC_REQUEST_COUNT, tags).increment();

        // 响应时间（发布百分位直方图，支持 P50/P95/P99 延迟分析）
        Timer.builder(METRIC_RESPONSE_TIME)
                .description("API网关响应时间")
                .publishPercentileHistogram()
                .tags(tags)
                .register(meterRegistry)
                .record(stats.durationNs(), TimeUnit.NANOSECONDS);

        // 错误响应计数（4xx/5xx，带标签）
        if (stats.statusCode() >= HTTP_STATUS_CLIENT_ERROR_START) {
            meterRegistry.counter(METRIC_ERROR_COUNT, tags).increment();
        }

        // 慢请求告警
        if (stats.durationMs() > SLOW_REQUEST_THRESHOLD_MS) {
            log.warn(
                    "慢请求告警, method: {}, path: {}, status: {}, duration: {}ms",
                    stats.method(),
                    stats.path(),
                    stats.statusCode(),
                    stats.durationMs());
        }

        logRequestComplete(stats);
    }

    /**
     * 记录请求开始日志。
     *
     * @param context 请求上下文
     * @param request HTTP 请求
     */
    private void logRequestStart(RequestContext context, ServerHttpRequest request) {
        log.info(
                "API网关请求开始, requestId: {}, method: {}, path: {}, remoteAddress: {}",
                context.requestId(),
                context.method(),
                context.path(),
                request.getRemoteAddress());
    }

    /**
     * 记录请求完成日志。
     *
     * <p>按慢请求、错误响应、正常响应三个级别分别以 warn/debug 输出，避免日志噪音。
     *
     * @param stats 请求统计快照
     */
    private void logRequestComplete(RequestStats stats) {
        if (stats.durationMs() > SLOW_REQUEST_THRESHOLD_MS) {
            log.warn(
                    "API网关请求完成(慢请求), requestId: {}, method: {}, path: {}, status: {}, duration: {}ms",
                    stats.requestId(),
                    stats.method(),
                    stats.path(),
                    stats.statusCode(),
                    stats.durationMs());
        } else if (stats.statusCode() >= HTTP_STATUS_CLIENT_ERROR_START) {
            log.warn(
                    "API网关请求完成(错误), requestId: {}, method: {}, path: {}, status: {}, duration: {}ms",
                    stats.requestId(),
                    stats.method(),
                    stats.path(),
                    stats.statusCode(),
                    stats.durationMs());
        } else {
            log.debug(
                    "API网关请求完成, requestId: {}, method: {}, path: {}, status: {}, duration: {}ms",
                    stats.requestId(),
                    stats.method(),
                    stats.path(),
                    stats.statusCode(),
                    stats.durationMs());
        }
    }

    /**
     * 判断是否应跳过网关统计（纯函数）。
     *
     * <p>以请求路径前缀匹配排除清单，命中则直接放行且不产生任何指标。
     *
     * @param path 请求路径
     * @return 如果应跳过返回 true
     */
    private static boolean shouldSkip(String path) {
        return Arrays.stream(EXCLUDED_PREFIXES).anyMatch(path::startsWith);
    }

    /**
     * 生成请求 ID（纯函数）。
     *
     * <p>优先透传请求头中的 {@code X-Request-Id}，缺失时生成 16 位无横线 UUID。
     *
     * @param request HTTP 请求
     * @return 请求 ID
     */
    private static String generateRequestId(ServerHttpRequest request) {
        String existingId = request.getHeaders().getFirst(REQUEST_ID_HEADER);
        if (existingId != null && !existingId.isBlank()) {
            return existingId;
        }
        return java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    /**
     * 归一化请求路径（纯函数）。
     *
     * <p>仅对业务 API 前缀路径生效：将路径中的数字 ID 段替换为 {@code {id}} 占位符，控制路径标签
     * 基数，避免不同 ID 产生大量独立时序。非业务路径（如扫描探测）保持原样，便于面板识别攻击流量。
     *
     * @param path 原始请求路径
     * @return 归一化后的路径
     */
    private static String normalizePath(String path) {
        if (path.startsWith(API_PREFIX)) {
            // 替换数字 ID 和 UUID 段为 {id} 占位符，降低标签基数
            return path.replaceAll("/\\d+(?=/|$)", "/{id}")
                    .replaceAll(
                            "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)",
                            "/{id}");
        }
        return path;
    }

    /**
     * 获取状态码分类（纯函数）。
     *
     * <p>按状态码百位数分桶：1xx/2xx/3xx/4xx/5xx，其他值归入 5xx 兜底。</p>
     *
     * @param statusCode HTTP 状态码
     * @return 状态码分类（1xx/2xx/3xx/4xx/5xx）
     */
    private static String getStatusClass(int statusCode) {
        return switch (statusCode / HTTP_STATUS_INFORMATIONAL_START) {
            case 1 -> "1xx";
            case 2 -> "2xx";
            case 3 -> "3xx";
            case 4 -> "4xx";
            default -> "5xx";
        };
    }

    /**
     * 构建指标标签列表（纯函数）。
     *
     * <p>标签集为 method/path/status/statusClass 四元组，统一用于请求计数、错误计数与响应时间
     * Timer，保证各指标维度一致、可交叉聚合。
     *
     * @param method     HTTP 方法
     * @param path       请求路径（将做归一化处理）
     * @param statusCode HTTP 状态码
     * @return 标签列表
     */
    private static List<Tag> buildTags(String method, String path, int statusCode) {
        return List.of(
                Tag.of("method", method),
                Tag.of("path", normalizePath(path)),
                Tag.of("status", String.valueOf(statusCode)),
                Tag.of("statusClass", getStatusClass(statusCode)));
    }
}
