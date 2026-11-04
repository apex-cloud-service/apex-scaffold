package com.apex.scaffold.gateway.ratelimit;

import com.apex.scaffold.common.util.JsonUtil;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 开放应用限流档查询客户端（D2 按应用分档限流）。
 *
 * <p>经 system 内部端点按 {@code clientId} 查询开放应用注册时声明的限流档编码，
 * 本地短 TTL 缓存。<b>fail-open 口径</b>：内部端点不可达、响应不可解析或客户端未知时
 * 一律返回 {@code default} 档——限流是保护性手段，档位查询失败只允许退化为默认阈值，
 * 不允许阻断开放调用。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
public class ClientTierClient {

    /** 默认限流档编码（与 system 侧 {@code OpenAppAdminService} 兜底口径一致） */
    public static final String DEFAULT_TIER = "default";

    private final OpenAppTierProperties properties;
    private final WebClient webClient;

    /** clientId → 限流档（TTL 内复用，避免内部端点被开放接口流量放大） */
    private final ConcurrentHashMap<String, TierSnapshot> cache = new ConcurrentHashMap<>();

    /**
     * 构造限流档查询客户端。
     *
     * @param properties 限流档查询配置
     */
    public ClientTierClient(OpenAppTierProperties properties) {
        this(properties, new com.apex.scaffold.common.internal.InternalAuthProperties(), null);
    }

    /**
     * 构造限流档查询客户端（带内部调用签名）。
     *
     * <p>标注 {@code @Autowired}：本类有两个构造器，Spring 无法自动选择；
     * 单参构造器仅供测试/无签名场景直接 new 使用。
     *
     * @param properties 限流档查询配置
     * @param internalAuthProperties 内部认证配置
     * @param signer 内部签名器；未配置时不签名
     */
    @org.springframework.beans.factory.annotation.Autowired
    public ClientTierClient(
            OpenAppTierProperties properties,
            com.apex.scaffold.common.internal.InternalAuthProperties internalAuthProperties,
            com.apex.scaffold.common.internal.InternalServiceSigner signer) {
        this.properties = properties;
        this.webClient = com.apex.scaffold.common.internal.InternalAuthExchangeFilter.apply(
                        WebClient.builder(), internalAuthProperties, signer)
                .build();
    }

    /**
     * 查询开放应用限流档（带 TTL 缓存，fail-open）。
     *
     * @param clientId 客户端 ID（空视为未声明）
     * @return 限流档编码；查询失败/客户端未知返回 {@code default}
     */
    public Mono<String> tierOf(String clientId) {
        if (!properties.isEnabled() || clientId == null || clientId.isBlank()) {
            return Mono.just(DEFAULT_TIER);
        }
        TierSnapshot snapshot = cache.get(clientId);
        if (snapshot != null && snapshot.fresh()) {
            return Mono.just(snapshot.tier());
        }
        return fetch(clientId)
                .timeout(Duration.ofMillis(Math.max(200, properties.getTimeoutMillis())))
                .map(body -> {
                    String tier = parseTier(body);
                    cache.put(clientId, new TierSnapshot(tier, System.currentTimeMillis() + cacheTtl()));
                    return tier;
                })
                // fail-open：档位查询失败退回默认档，不阻断开放调用
                .onErrorResume(error -> {
                    log.warn("开放应用限流档查询失败，按默认档判定: clientId={}, reason={}", clientId, error.getMessage());
                    return Mono.just(DEFAULT_TIER);
                });
    }

    /** 缓存 TTL（毫秒），下限 1s。 */
    private long cacheTtl() {
        return Math.max(1000, properties.getCacheTtlMillis());
    }

    /**
     * 拉取限流档响应原文（包内可见，测试可覆写注入替身响应）。
     *
     * @param clientId 客户端 ID
     * @return {@code Result} 封装的限流档 JSON
     */
    Mono<String> fetch(String clientId) {
        String uri = properties.getBaseUrl()
                + properties.getPath().replace("{clientId}", URLEncoder.encode(clientId, StandardCharsets.UTF_8));
        return webClient
                .get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 解析 {@code Result} 封装的 {@code rateLimitTier}；缺失/非法一律 {@code default}。
     *
     * @param body 响应体 JSON
     * @return 限流档编码
     */
    private String parseTier(String body) {
        try {
            Map<?, ?> root = JsonUtil.fromJson(body, Map.class);
            if (root != null && root.get("data") instanceof Map<?, ?> data) {
                Object tier = data.get("rateLimitTier");
                if (tier != null && !tier.toString().isBlank()) {
                    return tier.toString();
                }
            }
        } catch (RuntimeException e) {
            log.warn("开放应用限流档响应解析失败，按默认档判定: reason={}", e.getMessage());
        }
        return DEFAULT_TIER;
    }

    /**
     * 限流档缓存条目.
     *
     * @param tier 限流档编码
     * @param expireAt 缓存过期时间（epoch 毫秒）
     */
    private record TierSnapshot(String tier, long expireAt) {

        boolean fresh() {
            return System.currentTimeMillis() < expireAt;
        }
    }
}
