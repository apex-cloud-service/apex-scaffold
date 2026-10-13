package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.gateway.log.AccessLogProperties;
import com.apex.scaffold.gateway.log.AccessLogRecord;
import com.apex.scaffold.gateway.log.AccessLogService;
import com.apex.scaffold.gateway.support.GatewayWebUtil;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.logstash.logback.marker.Markers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 结构化访问日志过滤器（全局，链路末端）.
 *
 * <p>对应迁移方案 §5.3 过滤器链第 7 位（order = 50），位于链路末端，
 * 能够观测到前面所有过滤器改写后的最终请求与响应结果。
 *
 * <p>职责：
 * <ol>
 *   <li>记录 method / uri / 客户端 IP / HTTP 状态码 / 耗时（毫秒）/ traceId，
 *       统一以 key=value 结构输出，便于日志平台按字段检索</li>
 *   <li><b>落库</b>：总开关开启时，组装 {@link AccessLogRecord} 并交由
 *       {@link AccessLogService} 异步写入（{@code doFinally} 中触发，不阻塞响应）</li>
 *   <li><b>脱敏</b>：仅记录白名单请求头，且对 {@code Authorization}、{@code Cookie}、
 *       敏感头一律以 {@code ***} 占位，杜绝 Token / 会话凭据落入日志与数据库（安全红线）</li>
 *   <li>耗时统计覆盖下游完整调用链（网关 → 微服务 → 返回），作为端到端延迟观测依据</li>
 * </ol>
 *
 * <p><b>响应式说明</b>：耗时通过 {@code System.nanoTime()} 在进入与
 * {@code doFinally} 之间计算，保证异步链路下的正确性；日志记录与落库均不阻塞事件循环。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    /** 过滤器顺序：链路末端，观测最终结果 */
    public static final int ORDER = 50;

    /** 允许记录的请求头白名单（其余一律不落日志，降低敏感信息泄露风险） */
    private static final Set<String> LOGGABLE_HEADERS =
            Set.of(HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT, HttpHeaders.USER_AGENT, "X-Client-Type", "X-Gray-Tag");

    /** 需要脱敏的请求头（即使被误加入白名单也必须打码） */
    private static final Set<String> MASKED_HEADERS =
            Set.of(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE, HttpHeaders.SET_COOKIE, "X-Api-Key", "X-Auth-Token");

    /** 访问日志落库服务 */
    private final AccessLogService accessLogService;

    /** 访问日志落库配置（总开关/采样/头长限制） */
    private final AccessLogProperties accessLogProperties;

    /**
     * 构造访问日志过滤器.
     *
     * @param accessLogService    访问日志落库服务
     * @param accessLogProperties 访问日志落库配置
     */
    public RequestLoggingFilter(AccessLogService accessLogService, AccessLogProperties accessLogProperties) {
        this.accessLogService = accessLogService;
        this.accessLogProperties = accessLogProperties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String clientIp = GatewayWebUtil.clientIp(exchange);
        String traceId = request.getHeaders().getFirst(TraceWebFilter.HEADER_TRACE_ID);
        String headers = describeHeaders(request);

        return Mono.deferContextual(context -> chain.filter(exchange).doFinally(signal -> {
            long costMillis = (System.nanoTime() - startNanos) / 1_000_000;
            Integer status = exchange.getResponse().getStatusCode() == null
                    ? null
                    : exchange.getResponse().getStatusCode().value();
            String resolvedTraceId =
                    StringUtils.hasText(traceId) ? traceId : context.getOrDefault(TraceIdUtil.TRACE_ID_KEY, null);
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String tenantId = exchange.getRequest().getHeaders().getFirst(UserContextRelayFilter.HEADER_TENANT_ID);
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("routeId", route == null ? null : route.getId());
            fields.put("status", status);
            fields.put("requestUri", path);
            fields.put("method", method);
            fields.put("duration", costMillis);
            fields.put("clientIp", clientIp);
            fields.put("headers", headers);
            Map<String, String> previous = MDC.getCopyOfContextMap();
            try {
                MDC.remove(TraceIdUtil.TRACE_ID_KEY);
                MDC.remove("tenantId");
                if (StringUtils.hasText(resolvedTraceId)) {
                    MDC.put(TraceIdUtil.TRACE_ID_KEY, resolvedTraceId);
                }
                if (StringUtils.hasText(tenantId)) {
                    MDC.put("tenantId", tenantId);
                }
                log.info(Markers.appendEntries(fields), "access");
                persistAccessLog(exchange, method, path, clientIp, resolvedTraceId, status, costMillis);
            } finally {
                if (previous == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(previous);
                }
            }
        }));
    }

    /**
     * 组装并异步落库一条访问日志.
     *
     * <p>总开关关闭时直接返回（避免无谓的脱敏与序列化开销）；开启时从
     * {@link ServerWebExchangeUtils#GATEWAY_ROUTE_ATTR} 取命中路由，从可信身份头取
     * 用户/租户/灰度标记，经 {@link AccessLogService#sanitizeHeaders} 脱敏后落库。
     * 采样与失败吞并由 {@link AccessLogService#save} 统一负责，此处仅触发订阅，不阻塞响应。
     *
     * @param exchange 当前交换
     * @param method   HTTP 方法
     * @param path     请求路径
     * @param clientIp 客户端 IP
     * @param traceId  链路追踪 ID
     * @param status   响应状态码（可空）
     * @param costMs   端到端耗时（毫秒）
     */
    private void persistAccessLog(
            ServerWebExchange exchange,
            String method,
            String path,
            String clientIp,
            String traceId,
            Integer status,
            long costMillis) {
        if (!accessLogProperties.isEnabled()) {
            return;
        }
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        String routeId = route == null ? null : route.getId();
        String serviceId = route == null || route.getUri().getHost() == null
                ? null
                : route.getUri().getHost();
        HttpHeaders requestHeaders = exchange.getRequest().getHeaders();
        String grayTag = requestHeaders.getFirst(GrayRouteFilter.HEADER_GRAY_TAG);
        String userId = requestHeaders.getFirst(UserContextRelayFilter.HEADER_USER_ID);
        String tenantId = requestHeaders.getFirst(UserContextRelayFilter.HEADER_TENANT_ID);
        String userAgent = requestHeaders.getFirst(HttpHeaders.USER_AGENT);

        AccessLogRecord record = new AccessLogRecord(
                null,
                traceId,
                method,
                path,
                routeId,
                serviceId,
                grayTag,
                status,
                costMillis,
                clientIp,
                userAgent,
                accessLogService.sanitizeHeaders(requestHeaders),
                userId,
                tenantId,
                LocalDateTime.now());
        accessLogService.save(record).subscribe();
    }

    /**
     * 提取并脱敏请求头.
     *
     * <p>仅遍历白名单头；对敏感头以 {@code ***} 打码，其余原样输出；
     * 未在白名单内的头不记录，避免无意间将敏感信息写入日志。
     *
     * @param request 当前请求
     * @return 形如 {@code {Content-Type=application/json, Authorization=***}} 的字符串
     */
    private String describeHeaders(ServerHttpRequest request) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (String name : LOGGABLE_HEADERS) {
            String value = request.getHeaders().getFirst(name);
            if (value == null) {
                continue;
            }
            if (!first) {
                builder.append(", ");
            }
            builder.append(name).append('=').append(MASKED_HEADERS.contains(name) ? "***" : value);
            first = false;
        }
        return builder.append('}').toString();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
