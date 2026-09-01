package com.apex.scaffold.interfaces.filter;

import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.RequestUtil;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import com.apex.scaffold.interfaces.util.ResponseUtil;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 全局安全防护过滤器
 *
 * <p>作为安全防护的第一道防线，在请求进入业务处理前执行以下安全检测与防护：
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li><b>IP 黑名单检测</b>：基于 {@link RedissonClient} Redis 分布式存储的 IP 黑名单，
 *       被列入黑名单的 IP 将直接返回 403 拒绝访问，支持多实例部署时的黑名单同步</li>
 *   <li><b>User-Agent 检查</b>：检测空 User-Agent 请求，对非白名单路径的请求
 *       标记为可疑并记录告警日志</li>
 * </ul>
 *
 * <p>安全响应头注入由 {@link SecurityHeaderWebFilter} 统一负责，本过滤器不重复注入。
 *
 * <h2>执行顺序</h2>
 * <p>
 * 该过滤器优先级最高（{@code @Order(-200)}），在所有安全过滤器之前执行，
 * 确保恶意请求在进入业务逻辑前即被拦截。
 * </p>
 *
 * <h2>IP 黑名单存储机制</h2>
 * <p>
 * 使用 Redis 作为分布式存储，Key 格式为 {@code blacklist:ip:<ip>}，Value 为封禁时间戳（毫秒）。
 * Redis 的 TTL 机制自动清理过期的黑名单条目，无需手动维护。外部系统可通过
 * {@link #getBlacklist()}、{@link #blockIp(String)}、{@link #unblockIp(String)} 方法
 * 动态管理黑名单。
 * </p>
 *
 * <h2>线程模型</h2>
 * <p>
 * Redisson 的同步阻塞 API 通过 {@code subscribeOn(BlockingIoSchedulers.cache())}
 * 隔离到缓存专用弹性线程池，避免阻塞 Reactor 的事件循环线程，同时不挤占 JDBC/核心业务池。
 * </p>
 *
 * @author Apex Team
 * @version 3.1.0
 * @since 1.0.0
 * @see SecurityGuardProperties
 * @see ResponseUtil
 * @see SecurityHeaderWebFilter
 * @see RedissonClient
 */
@Slf4j
@Component
@Order(-200)
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.security.guard", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GlobalGuardWebFilter implements WebFilter {

    /** 未知 IP 标识 */
    private static final String UNKNOWN_IP = "unknown";

    /** Redis 黑名单 Key 前缀 */
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:ip:";

    /** 安全防护配置属性，包含 IP 封禁、限流和登录保护等策略参数。 */
    private final SecurityGuardProperties guardProperties;

    /** Redisson 客户端，提供分布式存储能力 */
    private final RedissonClient redissonClient;

    /** 安全指标记录器，将安全事件写入 Prometheus 指标。 */
    private final SecurityMetricsRecorder securityMetricsRecorder;

    /** IP 黑名单本地正/负缓存，降低普通请求对 Redis 的读取放大。 */
    private final Map<String, BlacklistCacheEntry> blacklistCache = new ConcurrentHashMap<>();

    /**
     * 执行全局安全防护过滤
     *
     * <p>按顺序执行以下检测：
     * <ol>
     * <li>检查客户端 IP 是否在黑名单中（异步查询 Redis）</li>
     * <li>检查 User-Agent 是否为空</li>
     * </ol>
     *
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @param chain    WebFlux 过滤器链
     * @return 表示过滤完成的 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String clientIp = RequestUtil.getClientIp(exchange.getRequest());
        if (UNKNOWN_IP.equals(clientIp)) {
            return chain.filter(exchange);
        }

        return isBlacklisted(clientIp).flatMap(isBlacklisted -> {
            if (isBlacklisted) {
                log.warn("黑名单 IP 请求被拦截: {}", clientIp);
                return ResponseUtil.forbidden(exchange, ResultCode.FORBIDDEN.getMsg());
            }

            if (guardProperties.isUaCheckEnabled()) {
                String userAgent = exchange.getRequest().getHeaders().getFirst("User-Agent");
                if (userAgent == null || userAgent.isBlank()) {
                    String path = exchange.getRequest().getURI().getPath();
                    if (!isUaAllowEmptyPath(path)) {
                        log.warn("检测到空 User-Agent 请求，IP: {}, Path: {}", clientIp, path);
                    }
                }
            }

            return chain.filter(exchange);
        });
    }

    /**
     * 判断客户端 IP 是否在黑名单中
     *
     * <p>从 Redis 中查询该 IP 的封禁记录，同时验证封禁时间戳是否在 TTL 有效期内。
     * Redis 的 TTL 机制会自动清理过期条目。
     *
     * @param clientIp 客户端 IP 地址
     * @return 如果在黑名单中且未过期返回 true，否则返回 false
     */
    private Mono<Boolean> isBlacklisted(String clientIp) {
        BlacklistCacheEntry cached = blacklistCache.get(clientIp);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtMillis() > now) {
            return Mono.just(cached.blacklisted());
        }
        if (cached != null) {
            blacklistCache.remove(clientIp, cached);
        }
        String key = BLACKLIST_KEY_PREFIX + clientIp;
        return Mono.fromCallable(() -> {
                    RBucket<Long> bucket = redissonClient.getBucket(key);
                    Long banTimestamp = bucket.get();
                    if (banTimestamp == null) {
                        cacheBlacklistResult(clientIp, false, now);
                        return false;
                    }
                    // Redis TTL 会自动清理过期条目，这里做双重验证确保安全性
                    long ttlMillis = guardProperties.getIpBlacklistCacheTtl() * 1000L;
                    if (System.currentTimeMillis() - banTimestamp > ttlMillis) {
                        bucket.delete();
                        cacheBlacklistResult(clientIp, false, now);
                        return false;
                    }
                    cacheBlacklistResult(clientIp, true, now);
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.cache());
    }

    /**
     * 缓存 IP 黑名单判断结果。
     *
     * <p>按上限淘汰最旧条目，根据命中与否分别采用正向与反向缓存的 TTL。</p>
     *
     * @param clientIp    客户端 IP 地址
     * @param blacklisted 是否命中黑名单
     * @param now         当前时间戳（毫秒）
     */
    private void cacheBlacklistResult(String clientIp, boolean blacklisted, long now) {
        int maxSize = Math.max(1, guardProperties.getIpBlacklistCacheSize());
        if (blacklistCache.size() >= maxSize && !blacklistCache.containsKey(clientIp)) {
            blacklistCache.entrySet().stream().findAny().ifPresent(entry -> blacklistCache.remove(entry.getKey()));
        }
        int ttlSeconds = blacklisted
                ? Math.max(1, guardProperties.getIpBlacklistCacheTtl())
                : Math.max(1, guardProperties.getIpBlacklistNegativeCacheTtl());
        blacklistCache.put(clientIp, new BlacklistCacheEntry(blacklisted, now + ttlSeconds * 1000L));
    }

    /**
     * 判断请求路径是否在 User-Agent 空值白名单中
     *
     * <p>对于健康检查、监控探针等路径，允许 User-Agent 为空而不告警。
     *
     * @param path 请求路径
     * @return 如果在白名单中返回 true
     */
    private boolean isUaAllowEmptyPath(String path) {
        return guardProperties.getUaAllowEmptyPaths().stream().anyMatch(path::equals);
    }

    /**
     * 获取当前黑名单缓存（从 Redis 读取）
     *
     * <p>供外部管理接口使用，可查看当前被封锁的 IP 列表及其封禁时间。
     * 注意：此方法会扫描所有黑名单 Key，在黑名单较大时可能影响性能，建议谨慎使用。
     *
     * @return 黑名单缓存的不可修改视图，Key 为 IP 地址，Value 为封禁时间戳（毫秒）
     */
    public Map<String, Long> getBlacklist() {
        Map<String, Long> result = new HashMap<>(16);
        try {
            Iterable<String> keys = redissonClient.getKeys().getKeysByPattern(BLACKLIST_KEY_PREFIX + "*");
            for (String key : keys) {
                String ip = key.substring(BLACKLIST_KEY_PREFIX.length());
                RBucket<Long> bucket = redissonClient.getBucket(key);
                Long timestamp = bucket.get();
                if (timestamp != null) {
                    result.put(ip, timestamp);
                }
            }
        } catch (Exception e) {
            log.error("获取黑名单失败", e);
        }
        return Map.copyOf(result);
    }

    /**
     * 将 IP 加入黑名单
     *
     * <p>供外部管理接口使用，动态封禁指定 IP 地址。封禁记录存储在 Redis 中，
     * 支持跨实例共享，自动在配置的 TTL 后过期。
     *
     * @param ip 需要封禁的 IP 地址
     */
    public void blockIp(String ip) {
        String key = BLACKLIST_KEY_PREFIX + ip;
        try {
            RBucket<Long> bucket = redissonClient.getBucket(key);
            long now = System.currentTimeMillis();
            bucket.set(now, Duration.ofSeconds(guardProperties.getIpBlacklistCacheTtl()));
            cacheBlacklistResult(ip, true, now);
            securityMetricsRecorder.recordIpBlocked();
            log.info("IP 已加入黑名单: {}", ip);
        } catch (Exception e) {
            log.error("加入黑名单失败, IP: {}", ip, e);
        }
    }

    /**
     * 将 IP 从黑名单中移除
     *
     * <p>供外部管理接口使用，解封指定 IP 地址。
     *
     * @param ip 需要解封的 IP 地址
     */
    public void unblockIp(String ip) {
        String key = BLACKLIST_KEY_PREFIX + ip;
        try {
            redissonClient.getBucket(key).delete();
            blacklistCache.remove(ip);
            log.info("IP 已从黑名单移除: {}", ip);
        } catch (Exception e) {
            log.error("移除黑名单失败, IP: {}", ip, e);
        }
    }

    private record BlacklistCacheEntry(boolean blacklisted, long expiresAtMillis) {}
}
