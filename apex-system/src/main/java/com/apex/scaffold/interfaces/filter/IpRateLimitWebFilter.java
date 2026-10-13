package com.apex.scaffold.interfaces.filter;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import com.apex.scaffold.interfaces.util.ClientIpResolver;
import com.apex.scaffold.interfaces.util.ResponseUtil;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

/**
 * IP 速率限制过滤器
 *
 * <p>基于 Redis Sorted Set 滑动窗口算法实现 IP 级别的请求速率限制，
 * 并对持续无视 429 响应的客户端进行短时封禁。
 *
 * <h2>滑动窗口算法</h2>
 * <p>
 * 使用 Redis Sorted Set 存储每个 IP 在某一路径下的请求时间戳，
 * Key 格式为 {@code rate:ip:<ip>:<path>}，Member 为 UUID，Score 为时间戳。
 * 每次请求时清理窗口外旧数据，统计窗口内请求数，超过阈值则触发限流。
 * </p>
 *
 * <h2>违规升级策略</h2>
 * <p>
 * 只有真实触发速率上限的请求才计为违规。业务 404、空 User-Agent 和其他业务响应
 * 均不参与封禁判断，避免将资源不足、共享出口或客户端差异误判为攻击。
 * </p>
 *
 * <h2>封禁策略</h2>
 * <ul>
 *   <li>单次超限：返回 429，不立即封禁</li>
 *   <li>违规窗口内持续超限达到阈值：封禁 {@code blockDuration} 秒（默认 5 分钟）</li>
 * </ul>
 *
 * <h2>白名单</h2>
 * <p>支持 CIDR 格式的 IP 白名单，白名单内的 IP 不受速率限制约束。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 * @see IpRateLimitProperties
 * @see ReactiveStringRedisTemplate
 * @see ResponseUtil
 */
@Slf4j
@Component
@Order(-190)
@RequiredArgsConstructor
public class IpRateLimitWebFilter implements WebFilter {

    /** Redis 滑动窗口 Key 前缀 */
    private static final String RATE_KEY_PREFIX = "rate:ip:";

    /** Redis 短时封禁状态 Key 前缀（沿用历史 Key 以兼容存量数据） */
    private static final String REPUTATION_KEY_PREFIX = "reputation:ip:";

    /** Redis 短期违规计数 Key 前缀 */
    private static final String VIOLATION_KEY_PREFIX = "rate:violation:";

    /** 封禁状态 Hash 字段：封禁截止时间戳 */
    private static final String FIELD_BLOCKED_UNTIL = "blockedUntil";

    /** CIDR 网段分隔符 */
    private static final String CIDR_SEPARATOR = "/";

    /** 路径模式变量起始标识 */
    private static final String PATTERN_VARIABLE_START = "{";

    /** 路径模式通配符标识 */
    private static final String PATTERN_WILDCARD = "*";

    /** Redis 响应式模板 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /** IP 速率限制配置属性 */
    private final IpRateLimitProperties rateLimitProperties;

    /** 安全客户端 IP 解析器 */
    private final ClientIpResolver clientIpResolver;

    /** 安全指标记录器 */
    private final SecurityMetricsRecorder securityMetricsRecorder;

    private final AtomicLong rateLimitRejected = new AtomicLong();
    private final AtomicLong blockedRejected = new AtomicLong();
    private final AtomicLong blockTriggered = new AtomicLong();

    private void logSampled(AtomicLong counter, String reason) {
        long count = counter.incrementAndGet();
        if (count > 0 && (count & (count - 1)) == 0) {
            log.warn("IP 限流事件: reason={}, total={}", reason, count);
        }
    }

    /**
     * Lua 脚本：滑动窗口限流原子操作（清理过期条目 + 添加当前请求 + 统计窗口计数 + 设置过期）。
     *
     * <p>替代原有的 3 次 Redis 网络往返（ZREMRANGEBYSCORE + ZADD + ZCOUNT），
     * 合并为单次 EVAL 调用，减少 Redis RTT。同时设置 ZSet 键 TTL 防止无流量 Key 残留。
     *
     * <pre>
     * KEYS[1]: 滑动窗口 ZSet Key
     * ARGV[1]: 窗口截止时间戳（cutoff）
     * ARGV[2]: 当前请求 Score（now）
     * ARGV[3]: 当前请求 Member
     * ARGV[4]: Key 过期时间（秒），取窗口大小的 3 倍
     * 返回: 窗口内剩余条目数（含当前请求）
     * </pre>
     */
    private static final RedisScript<Long> RATE_LIMIT_LUA = RedisScript.of(
            """
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return redis.call('ZCOUNT', KEYS[1], ARGV[1], '+inf')
            """,
            Long.class);

    /**
     * Lua 脚本：在固定违规窗口内原子递增计数，并仅在首次写入时设置 TTL。
     */
    private static final RedisScript<Long> VIOLATION_COUNT_LUA = RedisScript.of(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """,
            Long.class);

    /**
     * 执行速率限制过滤
     *
     * <p>按顺序执行：白名单/排除路径检查、短时封禁状态检查、滑动窗口限流，
     * 以及真实超限后的违规升级判断。
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain    WebFlux 过滤器链
     * @return 表示过滤完成的 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!rateLimitProperties.isEnabled()) {
            return chain.filter(exchange);
        }

        String clientIp = getClientIp(exchange);
        if (clientIp == null) {
            return chain.filter(exchange);
        }

        if (isWhitelisted(clientIp)) {
            return chain.filter(exchange);
        }

        String path = exchange.getRequest().getURI().getPath();

        if (isPathExcluded(path)) {
            return chain.filter(exchange);
        }

        String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
        String clientFingerprint = generateClientFingerprint(clientIp, userAgent);
        PathLimitPolicy pathLimitPolicy = resolvePathLimitPolicy(path);
        String windowKey = RATE_KEY_PREFIX + clientFingerprint + ":" + pathLimitPolicy.rateKey();
        String reputationKey = REPUTATION_KEY_PREFIX + clientFingerprint;
        String violationKey = VIOLATION_KEY_PREFIX + clientFingerprint;

        long now = System.currentTimeMillis();
        int limit = pathLimitPolicy.limit();
        int windowSize = rateLimitProperties.getDefaultWindow();
        long cutoff = now - (windowSize * 1000L);
        String member = java.util.UUID.randomUUID().toString().replace("-", "");

        return checkBlocked(reputationKey, now).flatMap(blockedUntil -> {
            if (blockedUntil != null && now < blockedUntil) {
                logSampled(blockedRejected, "already-blocked");
                return ResponseUtil.error(
                        exchange,
                        HttpStatus.TOO_MANY_REQUESTS,
                        ResultCode.RATE_LIMIT.getCode(),
                        ResultCode.RATE_LIMIT.getMsg());
            }
            return doRateLimit(
                    exchange,
                    chain,
                    windowKey,
                    reputationKey,
                    violationKey,
                    member,
                    now,
                    cutoff,
                    limit,
                    clientIp,
                    path,
                    windowSize);
        });
    }

    /**
     * 通过 Lua 脚本原子执行滑动窗口限流（清理 + 添加 + 计数单次网络往返）。
     */
    private Mono<Void> doRateLimit(
            ServerWebExchange exchange,
            WebFilterChain chain,
            String windowKey,
            String reputationKey,
            String violationKey,
            String member,
            long now,
            long cutoff,
            int limit,
            String clientIp,
            String path,
            int windowSize) {
        return Mono.from(redisTemplate.execute(
                        RATE_LIMIT_LUA,
                        Arrays.asList(windowKey),
                        String.valueOf(cutoff),
                        String.valueOf(now),
                        member,
                        String.valueOf(windowSize * 3)))
                .flatMap(count -> {
                    if (count > limit) {
                        logSampled(rateLimitRejected, "rate-exceeded");
                        securityMetricsRecorder.recordRateLimitRejected();
                        return registerViolationAndRespond(exchange, reputationKey, violationKey, clientIp);
                    }
                    return chain.filter(exchange);
                });
    }

    /**
     * 检查客户端 IP 是否被封禁
     *
     * <p>从 Redis Hash 中获取 {@code blockedUntil} 字段，
     * 判断当前时间是否在封禁有效期内。
     *
     * @param reputationKey 信誉 Redis Key
     * @return 封禁截止时间戳 Mono，若未封禁返回 Mono.empty()
     */
    private Mono<Long> checkBlocked(String reputationKey, long now) {
        return redisTemplate
                .opsForHash()
                .get(reputationKey, FIELD_BLOCKED_UNTIL)
                .flatMap(val -> {
                    long blockedUntil;
                    try {
                        blockedUntil = Long.parseLong(val.toString());
                    } catch (NumberFormatException e) {
                        return Mono.just(0L);
                    }
                    long maximumBlockedUntil = now + (rateLimitProperties.getBlockDuration() * 1000L);
                    if (blockedUntil > maximumBlockedUntil) {
                        return redisTemplate
                                .opsForHash()
                                .put(reputationKey, FIELD_BLOCKED_UNTIL, String.valueOf(maximumBlockedUntil))
                                .thenReturn(maximumBlockedUntil);
                    }
                    return Mono.just(blockedUntil);
                })
                .defaultIfEmpty(0L);
    }

    /**
     * 记录一次真实速率超限，并在客户端持续无视 429 时执行短时封禁。
     *
     * @param exchange      当前 HTTP 请求-响应交换对象
     * @param reputationKey 信誉 Redis Key
     * @param violationKey  短期违规计数 Key
     * @param clientIp      客户端 IP
     * @return 表示限流响应的 Mono
     */
    private Mono<Void> registerViolationAndRespond(
            ServerWebExchange exchange, String reputationKey, String violationKey, String clientIp) {
        return Mono.from(redisTemplate.execute(
                        VIOLATION_COUNT_LUA,
                        List.of(violationKey),
                        String.valueOf(rateLimitProperties.getViolationWindow())))
                .flatMap(violations -> {
                    if (violations < rateLimitProperties.getBlockThreshold()) {
                        return Mono.empty();
                    }
                    long blockedUntil = System.currentTimeMillis() + (rateLimitProperties.getBlockDuration() * 1000L);
                    logSampled(blockTriggered, "block-triggered");
                    securityMetricsRecorder.recordIpBlocked();
                    return redisTemplate
                            .opsForHash()
                            .put(reputationKey, FIELD_BLOCKED_UNTIL, String.valueOf(blockedUntil))
                            .then(redisTemplate.expire(
                                    reputationKey, Duration.ofSeconds(rateLimitProperties.getBlockDuration())))
                            .then();
                })
                .then(ResponseUtil.error(
                        exchange,
                        HttpStatus.TOO_MANY_REQUESTS,
                        ResultCode.RATE_LIMIT.getCode(),
                        ResultCode.RATE_LIMIT.getMsg()));
    }

    /**
     * 判断请求路径是否在排除列表中
     *
     * <p>路径排除列表内的路径完全不参与 IP 限流和信誉系统，
     * 用于保护正常高频业务接口（如投票、探索）不被安全敏感接口的封禁策略影响。
     *
     * @param path 请求路径
     * @return 如果路径被排除返回 true
     */
    private boolean isPathExcluded(String path) {
        List<String> excludes = rateLimitProperties.getPathExcludes();
        if (excludes == null || excludes.isEmpty()) {
            return false;
        }
        return excludes.stream().anyMatch(path::startsWith);
    }

    /**
     * 获取请求路径对应的速率限制阈值
     *
     * <p>优先使用路径级覆盖配置，若未配置则使用默认值。
     *
     * @param path 请求路径
     * @return 速率限制阈值
     */
    private PathLimitPolicy resolvePathLimitPolicy(String path) {
        Map<String, Integer> pathLimits = rateLimitProperties.getPathLimits();
        if (pathLimits == null || pathLimits.isEmpty()) {
            return new PathLimitPolicy(path, rateLimitProperties.getDefaultLimit());
        }
        Integer exactLimit = pathLimits.get(path);
        if (exactLimit != null) {
            return new PathLimitPolicy(path, exactLimit);
        }
        PathContainer requestPath = PathContainer.parsePath(path);
        return pathLimits.entrySet().stream()
                .filter(entry -> isPathPatternMatch(entry.getKey(), requestPath))
                .map(entry -> new PathLimitPolicy(entry.getKey(), entry.getValue()))
                .findFirst()
                .orElseGet(() -> new PathLimitPolicy(path, rateLimitProperties.getDefaultLimit()));
    }

    /**
     * 判断配置路径模式是否匹配请求路径。
     *
     * <p>仅对包含 {@code {}} 或 {@code *} 的路径模式进行解析匹配，
     * 非法模式将被忽略并返回 {@code false}。</p>
     *
     * @param configuredPath 配置的路径模式
     * @param requestPath    请求路径
     * @return 匹配时返回 {@code true}
     */
    private boolean isPathPatternMatch(String configuredPath, PathContainer requestPath) {
        if (!configuredPath.contains(PATTERN_VARIABLE_START) && !configuredPath.contains(PATTERN_WILDCARD)) {
            return false;
        }
        try {
            return PathPatternParser.defaultInstance.parse(configuredPath).matches(requestPath);
        } catch (IllegalArgumentException e) {
            log.warn("忽略无效的 IP 限流路径模式: {}", configuredPath);
            return false;
        }
    }

    private record PathLimitPolicy(String rateKey, int limit) {}

    /**
     * 检查客户端 IP 是否在白名单中
     *
     * <p>支持精确 IP 匹配和 CIDR 网段匹配。
     *
     * @param clientIp 客户端 IP 地址
     * @return 如果在白名单中返回 true
     */
    private boolean isWhitelisted(String clientIp) {
        List<String> whitelist = rateLimitProperties.getIpWhitelist();
        if (whitelist == null || whitelist.isEmpty()) {
            return false;
        }
        return whitelist.stream().anyMatch(entry -> isIpMatch(clientIp, entry));
    }

    /**
     * 判断 IP 是否与白名单条目匹配
     *
     * <p>支持精确 IP（如 192.168.1.1）和 CIDR 格式（如 10.0.0.0/8）。
     *
     * @param ip      客户端 IP
     * @param pattern 白名单条目
     * @return 如果匹配返回 true
     */
    private boolean isIpMatch(String ip, String pattern) {
        if (pattern.contains(CIDR_SEPARATOR)) {
            return isInCidrRange(ip, pattern);
        }
        return ip.equals(pattern);
    }

    /**
     * 判断 IP 是否在指定 CIDR 网段内
     *
     * <p>仅支持 IPv4 地址。将 IP 转换为 32 位整数后与网段掩码进行按位与运算。
     *
     * @param ip   需要判断的 IP 地址
     * @param cidr CIDR 网段，格式如 10.0.0.0/8
     * @return 如果在网段内返回 true
     */
    private boolean isInCidrRange(String ip, String cidr) {
        try {
            String[] parts = cidr.split("/");
            String subnetStr = parts[0];
            int prefixLen = parts.length > 1 ? Integer.parseInt(parts[1]) : 32;

            int ipInt = ipToInt(ip);
            int subnetInt = ipToInt(subnetStr);
            int mask = prefixLen == 0 ? 0 : 0xFFFFFFFF << (32 - prefixLen);

            return (ipInt & mask) == (subnetInt & mask);
        } catch (Exception e) {
            log.warn("CIDR 匹配异常, IP: {}, CIDR: {}", ip, cidr);
            return false;
        }
    }

    /**
     * 将 IPv4 地址转换为 32 位整数
     *
     * @param ip IPv4 地址
     * @return 32 位整数表示
     */
    private int ipToInt(String ip) {
        String[] octets = ip.split("\\.");
        return (Integer.parseInt(octets[0]) << 24)
                | (Integer.parseInt(octets[1]) << 16)
                | (Integer.parseInt(octets[2]) << 8)
                | Integer.parseInt(octets[3]);
    }

    /**
     * 获取客户端真实 IP 地址
     *
     * <p>优先从 {@code X-Forwarded-For} 请求头获取，其次从远程地址提取。
     *
     * @param exchange 当前 HTTP 请求 - 响应交换对象
     * @return 客户端 IP 地址，若无法获取则返回 null
     */
    private String getClientIp(ServerWebExchange exchange) {
        return clientIpResolver.resolve(
                exchange.getRequest().getRemoteAddress(),
                exchange.getRequest().getHeaders().get("X-Forwarded-For"),
                exchange.getRequest().getHeaders().getFirst("X-Real-IP"));
    }

    /**
     * 生成客户端指纹标识
     *
     * <p>结合客户端 IP 和 User-Agent 生成唯一指纹，用于增强限流 Key 的准确性。
     * 在代理场景下，仅依赖 IP 可能导致误判（多个用户共享同一出口 IP），
     * 通过加入 User-Agent 特征可以更精确地识别不同客户端。
     *
     * <h2>生成规则</h2>
     * <ul>
     *   <li>若 User-Agent 为空：返回 {@code ip}</li>
     *   <li>若 User-Agent 不为空：返回 {@code ip:uaHash}，其中 uaHash 为 User-Agent 的哈希值</li>
     * </ul>
     *
     * @param clientIp 客户端 IP 地址
     * @param userAgent 客户端 User-Agent 请求头
     * @return 客户端指纹标识
     */
    private String generateClientFingerprint(String clientIp, String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return clientIp;
        }

        int uaHash = userAgent.hashCode();
        return clientIp + ":" + Math.abs(uaHash);
    }
}
