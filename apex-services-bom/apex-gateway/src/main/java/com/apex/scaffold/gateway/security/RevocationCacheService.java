package com.apex.scaffold.gateway.security;

import com.apex.scaffold.common.util.JsonUtil;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.MediaType;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 网关令牌撤销缓存（统一认证收口 A3）：周期拉取认证中心撤销增量并维护本地 {@code jti} 撤销集。
 *
 * <p>行为约定：
 * <ul>
 *   <li>启动游标回看 {@code lookback-seconds}（默认覆盖 2 小时访问令牌全生命周期），重启不漏撤销；</li>
 *   <li>本地缓存 TTL = max(2 × 拉取间隔, 60s)，撤销记录过期前必然经历至少一次完整拉取窗口；</li>
 *   <li>拉取失败保留游标与缓存并告警，下个周期重试；</li>
 *   <li>响应为 {@code Result} 封装：{@code data.revocations[].jti} 记录入缓存，
 *       {@code data.cursor} 单调推进。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
public class RevocationCacheService implements InitializingBean, DisposableBean {

    private final RevocationProperties properties;
    private final WebClient webClient;

    /** 已撤销 jti → 过期时间（epoch 毫秒）；撤销记录在缓存窗口内必然经历完整拉取周期 */
    private final java.util.concurrent.ConcurrentHashMap<String, Long> revokedTokens =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** 撤销时间游标（epoch 毫秒），单调推进 */
    private final AtomicLong cursor = new AtomicLong();

    /** 撤销记录保留窗口（毫秒） */
    private final long retentionMillis;

    /** 周期拉取订阅句柄 */
    private volatile Disposable pullDisposable;

    /**
     * 构造撤销缓存服务。
     *
     * @param properties 撤销流配置
     */
    public RevocationCacheService(RevocationProperties properties) {
        this(properties, new com.apex.scaffold.common.internal.InternalAuthProperties(), null);
    }

    /**
     * 构造撤销流缓存（带内部调用签名）。
     *
     * @param properties 撤销流配置
     * @param internalAuthProperties 内部认证配置
     * @param signer 内部签名器；未配置时不签名
     */
    public RevocationCacheService(
            RevocationProperties properties,
            com.apex.scaffold.common.internal.InternalAuthProperties internalAuthProperties,
            com.apex.scaffold.common.internal.InternalServiceSigner signer) {
        this.properties = properties;
        this.webClient = com.apex.scaffold.common.internal.InternalAuthExchangeFilter.apply(
                        WebClient.builder(), internalAuthProperties, signer)
                .build();
        long ttlSeconds = Math.max(60, 2 * Math.max(5, properties.getPullIntervalSeconds()));
        this.retentionMillis = ttlSeconds * 1000L;
    }

    @Override
    public void afterPropertiesSet() {
        cursor.set(System.currentTimeMillis() - Math.max(0, properties.getLookbackSeconds()) * 1000L);
        if (!enabled()) {
            log.info("网关撤销缓存未启用（apex.gateway.revocation.enabled/url 未配置），撤销感知依赖业务服务自查");
            return;
        }
        Duration interval = Duration.ofSeconds(Math.max(5, properties.getPullIntervalSeconds()));
        pullDisposable = pull().onErrorResume(e -> {
                    log.warn("撤销流初始拉取失败，等待下个周期重试: url={}, reason={}", properties.getUrl(), e.getMessage());
                    return Mono.empty();
                })
                .thenMany(Flux.interval(interval, interval))
                .concatMap(ignore -> pull().onErrorResume(e -> {
                    log.warn("撤销流周期拉取失败，保留游标与缓存: url={}, reason={}", properties.getUrl(), e.getMessage());
                    return Mono.empty();
                }))
                .subscribe();
        log.info(
                "网关撤销流拉取已启动: url={}, interval={}s, lookback={}s",
                properties.getUrl(),
                interval.getSeconds(),
                properties.getLookbackSeconds());
    }

    @Override
    public void destroy() {
        Disposable disposable = pullDisposable;
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }

    /**
     * 撤销拉取是否生效。
     *
     * @return enabled 且 url 非空
     */
    public boolean enabled() {
        return properties.isEnabled()
                && properties.getUrl() != null
                && !properties.getUrl().isBlank();
    }

    /**
     * 判断令牌是否已被撤销（本地缓存视图）。
     *
     * @param jti JWT 唯一标识；缺失时视为未撤销（撤销流仅覆盖携带 jti 的认证中心令牌）
     * @return 缓存命中撤销集时为 {@code true}
     */
    public boolean isRevoked(@Nullable String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        Long expiry = revokedTokens.get(jti);
        if (expiry == null) {
            return false;
        }
        if (expiry < System.currentTimeMillis()) {
            revokedTokens.remove(jti);
            return false;
        }
        return true;
    }

    /**
     * 当前缓存规模（测试与指标观察）。
     *
     * @return 已登记撤销 jti 数
     */
    public long size() {
        long now = System.currentTimeMillis();
        revokedTokens.values().removeIf(expiry -> expiry < now);
        return revokedTokens.size();
    }

    /**
     * 执行一次拉取（初始 + 周期共用；测试可经包内可见性注入替身响应）。
     *
     */
    Mono<Void> pull() {
        String uri = properties.getUrl()
                + (properties.getUrl().contains("?") ? "&" : "?")
                + "since=" + cursor.get()
                + "&limit=" + Math.max(1, Math.min(properties.getBatchLimit(), 1000));
        return webClient
                .get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(Math.max(1000, properties.getTimeoutMillis())))
                .doOnNext(this::consume)
                .then();
    }

    /**
     * 解析 {@code Result} 封装的撤销增量并入缓存。
     *
     * @param body 响应体
     */
    public void consume(String body) {
        Map<?, ?> root;
        try {
            root = JsonUtil.fromJson(body, Map.class);
        } catch (RuntimeException e) {
            log.warn("撤销流响应解析失败（保留既有缓存与游标）: reason={}", e.getMessage());
            return;
        }
        if (root == null) {
            log.warn("撤销流响应解析失败：空响应体");
            return;
        }
        Object data = root.get("data");
        if (!(data instanceof Map<?, ?> feed)) {
            log.debug("撤销流响应无 data 节点，跳过");
            return;
        }
        Object revocations = feed.get("revocations");
        int recorded = 0;
        long maxRevokedAt = Long.MIN_VALUE;
        if (revocations instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> entry)) {
                    continue;
                }
                Object jti = entry.get("jti");
                Object revokedAt = entry.get("revokedAt");
                if (jti instanceof String token && !token.isBlank() && revokedAt instanceof Number number) {
                    revokedTokens.put(token, System.currentTimeMillis() + retentionMillis);
                    maxRevokedAt = Math.max(maxRevokedAt, number.longValue());
                    recorded++;
                }
            }
        }
        Object nextCursor = feed.get("cursor");
        if (nextCursor instanceof Number number) {
            cursor.accumulateAndGet(number.longValue(), Math::max);
        }
        if (recorded > 0) {
            log.info("撤销流增量已入缓存: recorded={}, cursor={}, cacheSize={}", recorded, cursor.get(), revokedTokens.size());
        }
    }
}
