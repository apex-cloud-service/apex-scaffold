package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.gateway.ratelimit.GatewayRateLimitProperties;
import com.apex.scaffold.gateway.ratelimit.TokenBucketRateLimitFilter;
import com.apex.scaffold.gateway.support.GatewayWebUtil;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 限流过滤器（全局）.
 *
 * <p>对应迁移方案 §5.3 过滤器链第 5 位（order = 30）。
 *
 * <p>本过滤器只负责「<b>维度 + 路径</b>」Key 的拼接与阈值解析，实际限流判定委托给
 * {@link TokenBucketRateLimitFilter}（Redis 分布式令牌桶，Task 2）。职责分离后，
 * 限流算法演进（令牌桶 / 固定窗口）无需改动本过滤器。
 *
 * <p><b>维度优先级</b>：租户（{@code X-Tenant-Id}）→ 用户（{@code X-User-Id}）→ IP。
 * 取首个可解析的维度身份，叠加请求路径形成
 * {@code {key-prefix}{scope}:{identity}:{path}}，从而同时满足
 * 「租户/用户/IP 级 + 路径级」限流要求（方案 §1.2.2）。
 *
 * <p><b>阈值解析</b>：优先匹配 {@code apex.gateway.rate-limit.rules[].resource}
 * （Ant 风格路径通配），命中则用其 {@code capacity}/{@code refill-rate}，
 * 否则回退 {@code default-capacity}/{@code default-refill-rate}。
 *
 * <p><b>失败出口</b>：令牌不足返回 HTTP 429 + {@link com.apex.scaffold.common.result.ResultCode#RATE_LIMIT}
 * 及 {@code X-RateLimit-Remaining}/{@code Retry-After} 头；Redis 异常由内核降级放行
 * （宁可宽松也不阻断业务），且<b>不影响下游异常向上传播</b>，保证路由级熔断（Task 1）可感知下游故障。
 *
 * @author Apex Team
 * @since 1.3.1
 * @see com.apex.scaffold.gateway.config.RateLimitConfig
 * @see TokenBucketRateLimitFilter
 */
@Component
public class RequestRateLimitFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestRateLimitFilter.class);

    /** 过滤器顺序：上下文注入（20）之后、灰度（40）之前 */
    public static final int ORDER = 30;

    /** Ant 风格路径匹配器（用于 {@code rules[].resource} 匹配） */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /** 租户维度限流 Key 解析器（优先级最高） */
    private final KeyResolver tenantKeyResolver;

    /** 用户维度限流 Key 解析器（优先级次之） */
    private final KeyResolver userKeyResolver;

    /** IP 维度限流 Key 解析器（默认兜底） */
    private final KeyResolver ipKeyResolver;

    /** 限流判定内核（Redis 令牌桶 / 固定窗口） */
    private final TokenBucketRateLimitFilter rateLimitCore;

    /** 限流配置属性（开关 / 算法 / 阈值 / 规则） */
    private final GatewayRateLimitProperties properties;

    /**
     * 构造限流过滤器.
     *
     * @param tenantKeyResolver 租户维度 KeyResolver
     * @param userKeyResolver   用户维度 KeyResolver
     * @param ipKeyResolver     IP 维度 KeyResolver（{@code RateLimitConfig} 主维度）
     * @param rateLimitCore     限流判定内核
     * @param properties        限流配置属性
     */
    public RequestRateLimitFilter(
            @Qualifier("tenantKeyResolver") KeyResolver tenantKeyResolver,
            @Qualifier("userKeyResolver") KeyResolver userKeyResolver,
            @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver,
            TokenBucketRateLimitFilter rateLimitCore,
            GatewayRateLimitProperties properties) {
        this.tenantKeyResolver = tenantKeyResolver;
        this.userKeyResolver = userKeyResolver;
        this.ipKeyResolver = ipKeyResolver;
        this.rateLimitCore = rateLimitCore;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();

        return resolveIdentity(exchange)
                .map(identity -> buildKey(identity, path))
                .flatMap(key -> {
                    Threshold threshold = resolveThreshold(path);
                    return rateLimitCore.apply(exchange, chain, key, threshold.capacity(), threshold.refillRate());
                });
    }

    /**
     * 按优先级解析限流维度身份：租户 → 用户 → IP.
     *
     * @param exchange 当前交换对象
     * @return 首个可解析的维度身份（{@code scope:identity}）；IP 维度始终可用，故必定非空
     */
    private Mono<String> resolveIdentity(ServerWebExchange exchange) {
        return tenantKeyResolver
                .resolve(exchange)
                .switchIfEmpty(Mono.defer(() -> userKeyResolver.resolve(exchange)))
                .switchIfEmpty(Mono.defer(() -> ipKeyResolver.resolve(exchange)))
                .switchIfEmpty(Mono.fromSupplier(() -> "ip:" + GatewayWebUtil.clientIp(exchange)));
    }

    /**
     * 拼接限流 Key：{@code {key-prefix}{scope}:{identity}:{path}}.
     *
     * @param identity 维度身份（{@code scope:identity}）
     * @param path     请求路径
     * @return 完整限流 Key
     */
    private String buildKey(String identity, String path) {
        return properties.getKeyPrefix() + identity + ":" + path;
    }

    /**
     * 解析路径对应的限流阈值（容量 + 补充速率）.
     *
     * <p>按 {@code rules} 声明顺序匹配首个命中的 Ant 规则，未命中回退默认值。
     *
     * @param path 请求路径
     * @return 解析后的阈值
     */
    private Threshold resolveThreshold(String path) {
        List<GatewayRateLimitProperties.Rule> rules = properties.getRules();
        if (rules != null) {
            for (GatewayRateLimitProperties.Rule rule : rules) {
                String resource = rule.getResource();
                if (resource != null && PATH_MATCHER.match(resource, path)) {
                    long capacity = rule.getCapacity() != null ? rule.getCapacity() : properties.getDefaultCapacity();
                    double refillRate =
                            rule.getRefillRate() != null ? rule.getRefillRate() : properties.getDefaultRefillRate();
                    log.debug("限流规则命中: path={}, resource={}, capacity={}", path, resource, capacity);
                    return new Threshold(capacity, refillRate);
                }
            }
        }
        return new Threshold(properties.getDefaultCapacity(), properties.getDefaultRefillRate());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * 限流阈值二元组（桶容量 + 令牌补充速率）.
     *
     * @param capacity   桶容量
     * @param refillRate 令牌补充速率（令牌/秒）
     */
    private record Threshold(long capacity, double refillRate) {}
}
