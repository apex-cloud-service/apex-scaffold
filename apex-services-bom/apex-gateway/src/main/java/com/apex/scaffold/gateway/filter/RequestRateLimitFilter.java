package com.apex.scaffold.gateway.filter;

import com.apex.scaffold.gateway.ratelimit.ClientTierClient;
import com.apex.scaffold.gateway.ratelimit.GatewayRateLimitProperties;
import com.apex.scaffold.gateway.ratelimit.TokenBucketRateLimitFilter;
import com.apex.scaffold.gateway.support.GatewayWebUtil;
import io.jsonwebtoken.Claims;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
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
 * <p><b>开放流量例外（D2）</b>：{@code /open-api/**} 固定按应用维度
 * {@code client:{clientId}} 判定（开放应用令牌无用户身份，租户维度会令同一租户下
 * 所有应用共享配额），阈值取应用注册档位 {@code rateLimitTier} 经
 * {@link ClientTierClient} 查询映射 {@code open-api-tiers}；档位查询 fail-open
 * 回退默认档，无法识别 client_id 时回退既有维度。
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
@Slf4j
@Component
public class RequestRateLimitFilter implements GlobalFilter, Ordered {

    /** 过滤器顺序：上下文注入（20）之后、灰度（40）之前 */
    public static final int ORDER = 30;

    /** 开放接口路径前缀（D2 按应用分档限流的触发范围） */
    private static final String OPEN_API_PREFIX = "/open-api/";

    /** 令牌 client_id claim 键 */
    private static final String CLAIM_CLIENT_ID = "client_id";

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

    /** 开放应用限流档查询客户端（D2，fail-open） */
    private final ClientTierClient clientTierClient;

    /** 限流配置属性（开关 / 算法 / 阈值 / 规则） */
    private final GatewayRateLimitProperties properties;

    /**
     * 构造限流过滤器.
     *
     * @param tenantKeyResolver 租户维度 KeyResolver
     * @param userKeyResolver   用户维度 KeyResolver
     * @param ipKeyResolver     IP 维度 KeyResolver（{@code RateLimitConfig} 主维度）
     * @param rateLimitCore     限流判定内核
     * @param clientTierClient  开放应用限流档查询客户端（D2）
     * @param properties        限流配置属性
     */
    public RequestRateLimitFilter(
            @Qualifier("tenantKeyResolver") KeyResolver tenantKeyResolver,
            @Qualifier("userKeyResolver") KeyResolver userKeyResolver,
            @Qualifier("ipKeyResolver") KeyResolver ipKeyResolver,
            TokenBucketRateLimitFilter rateLimitCore,
            ClientTierClient clientTierClient,
            GatewayRateLimitProperties properties) {
        this.tenantKeyResolver = tenantKeyResolver;
        this.userKeyResolver = userKeyResolver;
        this.ipKeyResolver = ipKeyResolver;
        this.rateLimitCore = rateLimitCore;
        this.clientTierClient = clientTierClient;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!properties.isEnabled()) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();

        return resolveLimit(exchange, path)
                .flatMap(limit ->
                        rateLimitCore.apply(exchange, chain, limit.key(), limit.capacity(), limit.refillRate()));
    }

    /**
     * 解析本次请求的限流键与生效阈值.
     *
     * <p>{@code /open-api/**} 开放流量固定按应用维度 {@code client:{clientId}} 判定，
     * 阈值取应用注册档位（{@code rateLimitTier} → {@code open-api-tiers} 映射）；
     * 无法识别 client_id 时回退既有「租户 → 用户 → IP」维度与路径阈值。
     *
     * @param exchange 当前交换对象
     * @param path     请求路径
     * @return 限流键与阈值
     */
    private Mono<ResolvedLimit> resolveLimit(ServerWebExchange exchange, String path) {
        if (path.startsWith(OPEN_API_PREFIX)) {
            Claims claims = exchange.getAttribute(JwtSignatureFilter.ATTR_CLAIMS);
            Object rawClientId = claims == null ? null : claims.get(CLAIM_CLIENT_ID);
            String clientId = rawClientId == null ? "" : rawClientId.toString().trim();
            if (!clientId.isEmpty()) {
                return clientTierClient.tierOf(clientId).map(tier -> {
                    Threshold threshold = thresholdOfTier(tier);
                    return new ResolvedLimit(
                            buildKey("client:" + clientId, path), threshold.capacity(), threshold.refillRate());
                });
            }
        }
        return resolveIdentity(exchange).map(identity -> {
            Threshold threshold = resolveThreshold(path);
            return new ResolvedLimit(buildKey(identity, path), threshold.capacity(), threshold.refillRate());
        });
    }

    /**
     * 解析开放应用档位阈值：未知档回退 {@code default} 档，缺省字段回退全局默认值。
     *
     * @param tier 档位编码
     * @return 生效阈值
     */
    private Threshold thresholdOfTier(String tier) {
        GatewayRateLimitProperties.Tier resolved = properties.resolveTierThreshold(tier);
        return new Threshold(resolved.getCapacity(), resolved.getRefillRate());
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

    /**
     * 本次请求的限流键与生效阈值.
     *
     * @param key        完整限流键
     * @param capacity   桶容量
     * @param refillRate 令牌补充速率（令牌/秒）
     */
    private record ResolvedLimit(String key, long capacity, double refillRate) {}
}
