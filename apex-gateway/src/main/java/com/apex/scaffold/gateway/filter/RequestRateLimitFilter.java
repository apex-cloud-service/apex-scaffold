package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.gateway.support.GatewayWebUtil;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 限流过滤器（全局）.
 *
 * <p>对应迁移方案 §5.3 过滤器链第 5 位（order = 30）。
 *
 * <p>在网关统一执行全局限流：优先按<b>用户维度</b>（已认证请求，{@code X-User-Id} 存在），
 * 匿名请求按<b>IP 维度</b>回退；两种维度再叠加<b>路径维度</b>，
 * 从而同时满足「IP 级 + 路径级」限流要求（方案 §1.2.2）。
 *
 * <p><b>限流算法</b>：Redis 固定窗口计数器（每窗口自增，首次写入设置 TTL）。
 * 阈值由 {@code apex.gateway.rate-limit.*} 配置（yml 或 Nacos 动态下发）：
 * <ul>
 *   <li>{@code capacity} — 单窗口允许的最大请求数</li>
 *   <li>{@code window-ms} — 窗口长度（毫秒）</li>
 * </ul>
 *
 * <p><b>失败出口</b>：触发限流返回 HTTP 429 + {@link ResultCode#RATE_LIMIT} JSON，
 * 与下游单体错误码逐字段一致（方案 §17 验收）。
 *
 * @author Apex Team
 * @since 1.3.1
 * @see com.apex.scaffold.gateway.config.RateLimitConfig
 */
@Component
public class RequestRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestRateLimitFilter.class);

    /** 过滤器顺序：上下文注入（20）之后、灰度（40）之前 */
    public static final int ORDER = 30;

    /** IP 维度限流 Key 解析器（匿名回退） */
    private final KeyResolver ipKeyResolver;

    /** 用户维度限流 Key 解析器（已认证优先） */
    private final KeyResolver userKeyResolver;

    /** 限流计数用的响应式 Redis 模板 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** 单窗口允许的最大请求数 */
    private final int capacity;

    /** 窗口长度（毫秒） */
    private final long windowMillis;

    /**
     * 构造限流过滤器.
     *
     * @param ipKeyResolver   IP 维度 KeyResolver（{@code RateLimitConfig} 主维度）
     * @param userKeyResolver 用户维度 KeyResolver
     * @param redisTemplate   Redis 响应式模板（窗口计数）
     * @param capacity        单窗口最大请求数
     * @param windowMillis    窗口毫秒数
     */
    public RequestRateLimitFilter(
            @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver,
            @Qualifier("userKeyResolver") KeyResolver userKeyResolver,
            ReactiveStringRedisTemplate redisTemplate,
            @Value("${apex.gateway.rate-limit.capacity:1000}") int capacity,
            @Value("${apex.gateway.rate-limit.window-ms:60000}") long windowMillis) {
        this.ipKeyResolver = ipKeyResolver;
        this.userKeyResolver = userKeyResolver;
        this.redisTemplate = redisTemplate;
        this.capacity = capacity;
        this.windowMillis = windowMillis;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
        String path = exchange.getRequest().getURI().getPath();

        Mono<String> keyMono =
                StringUtils.hasText(userId) ? userKeyResolver.resolve(exchange) : ipKeyResolver.resolve(exchange);

        // 仅对「限流判定」阶段做降级放行；下游链路的异常必须原样向上传播，
        // 否则会吞掉 ConnectException，导致路由级熔断/全局异常处理永远无法感知下游故障。
        return keyMono
                // 叠加路径维度：同一身份在同一路径上独立计数
                .map(baseKey -> baseKey + ":path:" + path)
                .flatMap(this::decide)
                .onErrorResume(err -> {
                    // Redis 异常时降级放行（宁可宽松也不阻断业务），记录告警
                    log.warn("限流判定异常, 降级放行: path={}, reason={}", path, err.getMessage());
                    return Mono.just(Boolean.TRUE);
                })
                .flatMap(allowed -> {
                    if (!allowed) {
                        log.warn("触发限流: path={}, limit={}", path, capacity);
                        return GatewayWebUtil.writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, ResultCode.RATE_LIMIT);
                    }
                    return chain.filter(exchange);
                });
    }

    /**
     * 判定当前请求是否允许放行（固定窗口计数）.
     *
     * <p>固定窗口：以「当前时间 / 窗口长度」作为窗口编号拼接 Key，
     * 自增计数；首次写入设置 TTL 自动过期，避免 Key 无限堆积。
     *
     * <p>本方法<b>只做限流判定</b>，不调用 {@code chain.filter}，以便上游 {@code onErrorResume}
     * 仅兜住 Redis 等基础设施异常，而不会误吞下游业务异常。
     *
     * @param key 限流 Key（含维度前缀 + 路径）
     * @return {@code true} 放行；{@code false} 触发限流
     */
    private Mono<Boolean> decide(String key) {
        long windowStart = (System.currentTimeMillis() / windowMillis) * windowMillis;
        String windowKey = key + ":window:" + windowStart;

        return redisTemplate
                .opsForValue()
                .increment(windowKey)
                .flatMap(count -> {
                    if (count != null && count == 1L) {
                        // 窗口首次命中：设置过期时间，保证自动清理
                        return redisTemplate
                                .expire(windowKey, Duration.ofMillis(windowMillis))
                                .thenReturn(count);
                    }
                    return Mono.just(count == null ? 0L : count);
                })
                .map(count -> count <= capacity);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
