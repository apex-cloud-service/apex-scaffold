package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.gateway.support.GatewayWebUtil;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
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
 *   <li><b>脱敏</b>：仅记录白名单请求头，且对 {@code Authorization}、{@code Cookie}、
 *       敏感头一律以 {@code ***} 占位，杜绝 Token / 会话凭据落入日志（安全红线）</li>
 *   <li>耗时统计覆盖下游完整调用链（网关 → 微服务 → 返回），作为端到端延迟观测依据</li>
 * </ol>
 *
 * <p><b>响应式说明</b>：耗时通过 {@code System.nanoTime()} 在进入与
 * {@code doFinally} 之间计算，保证异步链路下的正确性；日志记录不阻塞事件循环。
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

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startNanos = System.nanoTime();
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();
        String path = request.getURI().getPath();
        String clientIp = GatewayWebUtil.clientIp(exchange);
        String traceId = request.getHeaders().getFirst(TraceWebFilter.HEADER_TRACE_ID);
        String headers = describeHeaders(request);

        return chain.filter(exchange).doFinally(signal -> {
            long costMillis = (System.nanoTime() - startNanos) / 1_000_000;
            Integer status = exchange.getResponse().getStatusCode() == null
                    ? null
                    : exchange.getResponse().getStatusCode().value();
            // 结构化访问日志：字段固定，便于日志平台解析与聚合
            log.info(
                    "access method={} uri={} status={} costMs={} clientIp={} traceId={} headers={}",
                    method,
                    path,
                    status,
                    costMillis,
                    clientIp,
                    StringUtils.hasText(traceId) ? traceId : TraceIdUtil.getTraceId(),
                    headers);
        });
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
