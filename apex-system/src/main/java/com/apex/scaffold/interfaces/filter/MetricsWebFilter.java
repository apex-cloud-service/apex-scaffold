package com.apex.scaffold.interfaces.filter;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * API 接口响应时间监控过滤器
 *
 * <p>基于 Micrometer Timer 实现接口级响应时间监控，发布真实的 Prometheus 直方图桶，
 * 由 Grafana/PromQL 按查询窗口计算 P50/P95/P99，用于性能监控和告警触发。
 *
 * <h2>监控指标</h2>
 * <ul>
 *   <li><strong>apex.api.response.time</strong>：接口响应时间（Micrometer 按秒导出），
 *       包含 method（HTTP方法）、path（请求路径）、status（状态码）三个标签</li>
 *   <li><strong>apex.api.request.count</strong>：接口请求总数计数器，
 *       包含 method、path、status 三个标签</li>
 * </ul>
 *
 * <h2>采集策略</h2>
 * <ul>
 *   <li>仅对 {@code /api} 开头的业务接口采集；{@code /actuator}、{@code /health} 及其他非业务路径跳过</li>
 *   <li>对 {@code /api} 开头的业务接口进行全量采集</li>
 *   <li>路径参数会进行归一化处理（如 {@code /api/users/123} → {@code /api/users/{id}}）</li>
 * </ul>
 *
 * <h2>使用方式</h2>
 * <p>配置 Prometheus 查询时使用 {@code apex_api_response_time_seconds_bucket} 计算分位数；
 * {@code _max} 只表示进程内当前最大值，不作为稳定的 P95/P99 替代。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see Timer
 * @see MeterRegistry
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class MetricsWebFilter implements WebFilter {

    /** 需要排除的路径前缀（系统监控端点） */
    private static final String EXCLUDED_PREFIX = "/actuator";

    /** 业务 API 路径前缀 */
    private static final String API_PREFIX = "/api";

    /** 健康检查路径，不计入请求统计指标。 */
    private static final String HEALTH_PATH = "/health";

    /** 接口响应时间指标名称（单位：秒），按路径和状态码打标签。 */
    private static final String METRIC_RESPONSE_TIME = "apex.api.response.time";

    /** 接口请求计数指标名称，按路径、方法和状态码打标签。 */
    private static final String METRIC_REQUEST_COUNT = "apex.api.request.count";

    /** 慢请求阈值（毫秒） */
    private static final long SLOW_REQUEST_THRESHOLD_MS = 1000L;

    /** Micrometer 指标注册表。 */
    private final MeterRegistry meterRegistry;

    /**
     * 构造 API 响应时间监控过滤器
     *
     * @param meterRegistry Micrometer 指标注册器
     */
    public MetricsWebFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 过滤请求并记录响应时间指标
     *
     * <p>在请求处理前记录开始时间，响应完成后记录结束时间，
     * 并计算响应耗时写入 Micrometer Timer。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain    WebFilter 链
     * @return 响应式处理结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();

        // 排除非业务路径（系统监控端点不采集）
        if (shouldSkip(path)) {
            return chain.filter(exchange);
        }

        String method = exchange.getRequest().getMethod().name();
        // 归一化路径（将路径参数替换为占位符）
        String normalizedPath = normalizePath(path);

        // 记录请求开始时间
        long startTime = System.nanoTime();

        return chain.filter(exchange).doFinally(signalType -> {
            // 计算响应耗时
            long duration = System.nanoTime() - startTime;
            long durationMs = TimeUnit.NANOSECONDS.toMillis(duration);

            // 获取响应状态码
            ServerHttpResponse response = exchange.getResponse();
            int statusCode = response.getStatusCode() != null
                    ? response.getStatusCode().value()
                    : fallbackStatusCode(signalType);

            // 构建指标标签
            List<Tag> tags = List.of(
                    Tag.of("method", method),
                    Tag.of("path", normalizedPath),
                    Tag.of("status", String.valueOf(statusCode)));

            // 记录响应时间（Timer）
            Timer.builder(METRIC_RESPONSE_TIME)
                    .description("API 接口响应时间")
                    .publishPercentileHistogram()
                    .publishPercentiles(0.95, 0.99)
                    .tags(tags)
                    .register(meterRegistry)
                    .record(duration, TimeUnit.NANOSECONDS);

            // 记录请求计数（Counter）
            meterRegistry.counter(METRIC_REQUEST_COUNT, tags).increment();

            // 慢请求日志（超过阈值的请求记录 WARN 日志）
            if (durationMs > SLOW_REQUEST_THRESHOLD_MS) {
                log.warn(
                        "慢请求告警, method: {}, path: {}, status: {}, duration: {}ms",
                        method,
                        normalizedPath,
                        statusCode,
                        durationMs);
            } else if (log.isDebugEnabled()) {
                log.debug(
                        "API 请求指标, method: {}, path: {}, status: {}, duration: {}ms",
                        method,
                        normalizedPath,
                        statusCode,
                        durationMs);
            }
        });
    }

    /**
     * 在响应没有显式状态码时，根据响应式结束信号推断最小可信状态。
     *
     * <p>响应式请求可能因以下原因结束而没有显式设置状态码：
     * <ul>
     *   <li><b>正常完成（ON_COMPLETE）</b>：使用 HTTP 默认的 200</li>
     *   <li><b>异常终止（ON_ERROR）</b>：使用 500 表示服务端错误</li>
     *   <li><b>客户端取消（CANCEL）</b>：使用 499 约定（Nginx 标准），避免误算为服务端错误</li>
     * </ul>
     *
     * @param signalType 响应式请求结束信号
     * @return 推断的 HTTP 状态码
     */
    private int fallbackStatusCode(SignalType signalType) {
        return switch (signalType) {
            case ON_ERROR -> 500;
            case CANCEL -> 499;
            default -> 200;
        };
    }

    /**
     * 判断是否应跳过指标采集
     *
     * <p>仅保留业务 {@code /api/**} 请求；对于 Actuator、健康检查以及其他非业务路径不进行指标采集，
     * 避免监控抓取自身和静态/扫描流量干扰业务 API 指标。
     *
     * @param path 请求路径
     * @return 如果应跳过采集返回 true
     */
    private boolean shouldSkip(String path) {
        return path.startsWith(EXCLUDED_PREFIX) || HEALTH_PATH.equals(path) || !path.startsWith(API_PREFIX);
    }

    /**
     * 归一化请求路径
     *
     * <p>将路径中的具体 ID 参数替换为统一的占位符，避免指标标签基数过高。
     * 例如：
     * <ul>
     *   <li>{@code /api/users/123} → {@code /api/users/{id}}</li>
     *   <li>{@code /api/orders/456/items} → {@code /api/orders/{id}/items}</li>
     * </ul>
     *
     * @param path 原始请求路径
     * @return 归一化后的路径
     */
    private String normalizePath(String path) {
        // 仅对 /api 开头的业务路径进行归一化
        if (path.startsWith(API_PREFIX)) {
            // 替换数字 ID 和 UUID 段为 {id} 占位符，降低标签基数
            return path.replaceAll("/\\d+(?=/|$)", "/{id}")
                    .replaceAll(
                            "/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}(?=/|$)",
                            "/{id}");
        }
        return path;
    }
}
