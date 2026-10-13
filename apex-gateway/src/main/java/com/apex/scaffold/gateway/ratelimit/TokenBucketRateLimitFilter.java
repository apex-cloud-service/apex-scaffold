package com.apex.scaffold.gateway.ratelimit;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.gateway.support.GatewayWebUtil;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 分布式令牌桶限流内核（Task 2）.
 *
 * <p>网关限流的<b>唯一判定内核</b>，由 {@link com.apex.scaffold.gateway.filter.RequestRateLimitFilter}
 * 在完成「维度 + 路径」Key 拼接后委托调用，从而把「限流算法」与「维度解析」解耦。
 *
 * <h2>算法</h2>
 * 默认使用 Redis 令牌桶（Lua 原子脚本），与
 * {@code com.apex.scaffold.infrastructure.ratelimit.distributed.DistributedTokenBucketRateLimiter}
 * <b>保持同一脚本语义</b>（读取 tokens/lastRefill → 按速率补充 → 截断到容量 → 扣减或拒绝 → 写回并设置过期），
 * 保证跨实例共享配额、支持突发流量。可经 {@code algorithm=fixed-window} 切回固定窗口计数器。
 *
 * <h2>失败出口</h2>
 * <ul>
 *   <li><b>Redis 异常</b>：降级放行（宁可宽松也不阻断业务），累计 {@code apex.gateway.ratelimit.failopen} 指标。</li>
 *   <li><b>令牌不足</b>：返回 HTTP 429 + {@link ResultCode#RATE_LIMIT}，并附带
 *       {@code X-RateLimit-Remaining: 0} 与 {@code Retry-After} 头。</li>
 * </ul>
 *
 * <h2>关键约束</h2>
 * 仅「限流判定」阶段受降级保护；下游链路的异常必须原样向上传播，
 * 否则会吞掉 {@code ConnectException}，导致路由级熔断（Task 1）永远无法感知下游故障。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Component
public class TokenBucketRateLimitFilter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimitFilter.class);

    /** 令牌数 Key 后缀 */
    private static final String TOKENS_SUFFIX = ":tokens";

    /** 最后补充时间 Key 后缀 */
    private static final String TIME_SUFFIX = ":time";

    /** Key 过期时间（秒）——60 秒无访问自动清理 */
    private static final long KEY_EXPIRATION_SECONDS = 60L;

    /** 触发限流计数指标名 */
    private static final String METRIC_REJECTED = "apex.gateway.ratelimit.rejected";

    /** 降级放行计数指标名 */
    private static final String METRIC_FAILOPEN = "apex.gateway.ratelimit.failopen";

    /** 固定窗口算法标识（{@code algorithm=fixed-window} 兼容路径） */
    private static final String ALGORITHM_FIXED_WINDOW = "fixed-window";

    /**
     * Lua 脚本：原子性令牌桶算法（与 infra {@code DistributedTokenBucketRateLimiter} 同语义）.
     *
     * <p>参数：{@code KEYS[1]} 令牌数 key、{@code KEYS[2]} 最后补充时间 key；
     * {@code ARGV[1]} 容量、{@code ARGV[2]} 补充速率（令牌/秒）、{@code ARGV[3]} 当前毫秒时间戳、
     * {@code ARGV[4]} 请求令牌数、{@code ARGV[5]} 过期秒数。返回 1 成功、0 拒绝。
     */
    private static final String LUA_SCRIPT =
            """
            local tokensKey = KEYS[1]
            local timeKey = KEYS[2]
            local capacity = tonumber(ARGV[1])
            local refillRate = tonumber(ARGV[2])
            local now = tonumber(ARGV[3])
            local requested = tonumber(ARGV[4])
            local expiration = tonumber(ARGV[5])

            local tokens = tonumber(redis.call('GET', tokensKey) or capacity)
            local lastRefill = tonumber(redis.call('GET', timeKey) or now)

            local elapsed = math.max(0, (now - lastRefill) / 1000.0)
            local newTokens = math.min(capacity, tokens + elapsed * refillRate)

            if newTokens >= requested then
                local remaining = newTokens - requested
                redis.call('SET', tokensKey, remaining, 'EX', expiration)
                redis.call('SET', timeKey, now, 'EX', expiration)
                return 1
            else
                redis.call('SET', tokensKey, newTokens, 'EX', expiration)
                redis.call('SET', timeKey, now, 'EX', expiration)
                return 0
            end
            """;

    /** 响应式 Redis 模板 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** 限流配置属性（算法/默认容量/速率/窗口） */
    private final GatewayRateLimitProperties properties;

    /** 指标注册表 */
    private final MeterRegistry meterRegistry;

    /**
     * 构造限流内核.
     *
     * @param redisTemplate 响应式 Redis 模板
     * @param properties    限流配置属性
     * @param meterRegistry 指标注册表
     */
    public TokenBucketRateLimitFilter(
            ReactiveStringRedisTemplate redisTemplate,
            GatewayRateLimitProperties properties,
            MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 执行限流判定并按结果放行或终止请求.
     *
     * @param exchange    当前交换对象
     * @param chain       下游过滤链（允许时调用）
     * @param key         已拼接完成的限流 Key（含前缀 + 维度 + 路径）
     * @param capacity    桶容量
     * @param refillRate  令牌补充速率（令牌/秒，仅令牌桶使用）
     * @return 完成信号；被限流时写出 429，放行时转发下游
     */
    public Mono<Void> apply(
            ServerWebExchange exchange, GatewayFilterChain chain, String key, long capacity, double refillRate) {
        String path = exchange.getRequest().getURI().getPath();
        // 仅对「限流判定」阶段做降级放行；下游链路的异常必须原样向上传播，
        // 否则会吞掉 ConnectException，导致路由级熔断/全局异常处理永远无法感知下游故障。
        return tryAcquire(key, capacity, refillRate)
                .onErrorResume(err -> {
                    failOpenCounter().increment();
                    log.warn("限流判定异常, 降级放行: path={}, key={}, reason={}", path, key, err.getMessage());
                    return Mono.just(Boolean.TRUE);
                })
                .flatMap(allowed -> {
                    if (!allowed) {
                        rejectedCounter().increment();
                        log.warn("触发限流: path={}, key={}, capacity={}", path, key, capacity);
                        exchange.getResponse().getHeaders().set("X-RateLimit-Remaining", "0");
                        exchange.getResponse()
                                .getHeaders()
                                .set("Retry-After", String.valueOf(retryAfterSeconds(refillRate)));
                        return GatewayWebUtil.writeError(exchange, HttpStatus.TOO_MANY_REQUESTS, ResultCode.RATE_LIMIT);
                    }
                    return chain.filter(exchange);
                });
    }

    /**
     * 尝试获取 1 个令牌（仅做判定，不调用下游链）.
     *
     * <p><b>不吞异常</b>：Redis 故障会原样上抛，由 {@link #apply} 层统一降级放行。
     *
     * @param key        限流 Key
     * @param capacity   桶容量
     * @param refillRate 令牌补充速率（令牌/秒）
     * @return {@code true} 放行；{@code false} 触发限流
     */
    public Mono<Boolean> tryAcquire(String key, long capacity, double refillRate) {
        if (ALGORITHM_FIXED_WINDOW.equalsIgnoreCase(properties.getAlgorithm())) {
            return tryAcquireFixedWindow(key, capacity);
        }
        return tryAcquireTokenBucket(key, capacity, refillRate);
    }

    /**
     * 令牌桶判定（Lua 原子脚本）.
     *
     * @param key        限流 Key
     * @param capacity   桶容量
     * @param refillRate 补充速率（令牌/秒）
     * @return 是否放行
     */
    private Mono<Boolean> tryAcquireTokenBucket(String key, long capacity, double refillRate) {
        List<String> keys = List.of(key + TOKENS_SUFFIX, key + TIME_SUFFIX);
        List<String> args = List.of(
                String.valueOf(capacity),
                String.valueOf(refillRate),
                String.valueOf(System.currentTimeMillis()),
                "1",
                String.valueOf(KEY_EXPIRATION_SECONDS));
        RedisScript<Long> script = RedisScript.of(LUA_SCRIPT, Long.class);

        return redisTemplate.execute(script, keys, args).next().map(result -> result == 1L);
    }

    /**
     * 固定窗口计数判定（{@code algorithm=fixed-window} 兼容路径）.
     *
     * @param key      限流 Key
     * @param capacity 单窗口最大请求数
     * @return 是否放行
     */
    private Mono<Boolean> tryAcquireFixedWindow(String key, long capacity) {
        long windowMs = properties.getWindowMs();
        long windowStart = (System.currentTimeMillis() / windowMs) * windowMs;
        String windowKey = key + ":window:" + windowStart;

        return redisTemplate
                .opsForValue()
                .increment(windowKey)
                .flatMap(count -> {
                    if (count != null && count == 1L) {
                        return redisTemplate
                                .expire(windowKey, Duration.ofMillis(windowMs))
                                .thenReturn(count);
                    }
                    return Mono.just(count == null ? 0L : count);
                })
                .map(count -> count <= capacity);
    }

    /**
     * 计算建议的重试等待秒数（按补充 1 个令牌所需时间向上取整，至少 1 秒）.
     *
     * @param refillRate 补充速率（令牌/秒）
     * @return 重试等待秒数
     */
    private long retryAfterSeconds(double refillRate) {
        if (refillRate <= 0) {
            return 1L;
        }
        return Math.max(1L, (long) Math.ceil(1.0d / refillRate));
    }

    /**
     * 触发限流计数指标.
     *
     * @return 计数指标
     */
    private Counter rejectedCounter() {
        return meterRegistry.counter(METRIC_REJECTED);
    }

    /**
     * 降级放行计数指标.
     *
     * @return 计数指标
     */
    private Counter failOpenCounter() {
        return meterRegistry.counter(METRIC_FAILOPEN);
    }
}
