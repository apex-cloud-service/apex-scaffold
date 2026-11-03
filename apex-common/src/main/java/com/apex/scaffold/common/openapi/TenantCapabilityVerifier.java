package com.apex.scaffold.common.openapi;

import com.apex.scaffold.common.capability.CapabilityGate;
import com.apex.scaffold.common.capability.TenantFeatures;
import com.apex.scaffold.common.util.JsonUtil;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 租户能力订阅校验客户端（方案 D4 服务侧二次校验）。
 *
 * <p>经 system 内部端点 {@code GET /internal/v1/tenants/{code}/capabilities} 拉取租户
 * 能力订阅快照（{@code exists/unbounded/features}），本地短 TTL 缓存后交由共享的
 * {@link CapabilityGate} 判定——与网关能力门禁共用同一判定实现，保证两端口径一致。
 *
 * <p><b>fail-closed 口径</b>：租户编码缺失、租户不存在（{@code exists=false}）一律视为
 * 未订阅；内部端点不可达或响应不可解析时向上发出错误，由调用方决定降级出口
 * （服务侧守卫按 503 拒绝）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
public class TenantCapabilityVerifier {

    private final OpenApiGuardProperties properties;
    private final WebClient webClient;

    /** 租户编码 → 能力快照（TTL 内复用，避免内部端点被开放流量放大） */
    private final ConcurrentHashMap<String, Snapshot> cache = new ConcurrentHashMap<>();

    /**
     * 构造能力校验客户端。
     *
     * @param properties 守卫配置
     */
    public TenantCapabilityVerifier(OpenApiGuardProperties properties) {
        this.properties = properties;
        this.webClient = WebClient.builder().build();
    }

    /**
     * 判定租户是否已订阅指定能力（带 TTL 缓存）。
     *
     * @param tenantCode 租户编码（缺失按未订阅处理）
     * @param capabilityCode 能力编码（如 {@code file-storage}）
     * @return 已订阅返回 {@code true}；内部端点不可达时以错误信号终止
     */
    public Mono<Boolean> subscribed(String tenantCode, String capabilityCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            return Mono.just(Boolean.FALSE);
        }
        Snapshot snapshot = cache.get(tenantCode);
        if (snapshot != null && snapshot.fresh()) {
            return Mono.just(snapshot.subscribed(capabilityCode));
        }
        return fetch(tenantCode)
                .timeout(Duration.ofMillis(Math.max(200, properties.getTimeoutMillis())))
                .map(body -> {
                    Snapshot parsed = Snapshot.parse(body, properties.getCacheTtlMillis());
                    cache.put(tenantCode, parsed);
                    return parsed.subscribed(capabilityCode);
                });
    }

    /**
     * 拉取租户能力快照原文（包内可见，测试可覆写注入替身响应）。
     *
     * @param tenantCode 租户编码
     * @return {@code Result} 封装的能力快照 JSON
     */
    Mono<String> fetch(String tenantCode) {
        String uri = properties.getBaseUrl()
                + properties.getPath().replace("{code}", URLEncoder.encode(tenantCode, StandardCharsets.UTF_8));
        return webClient
                .get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 租户能力快照：解析 {@code Result} 封装的 {@code exists/unbounded/features}。
     *
     * @param exists 租户是否存在
     * @param unbounded 是否不限量租户
     * @param features 已订阅特性编码
     * @param expireAt 缓存过期时间（epoch 毫秒）
     */
    record Snapshot(boolean exists, boolean unbounded, Set<String> features, long expireAt) {

        static Snapshot parse(String body, long ttlMillis) {
            Map<?, ?> root;
            try {
                root = JsonUtil.fromJson(body, Map.class);
            } catch (RuntimeException e) {
                throw new IllegalStateException("租户能力响应解析失败: " + e.getMessage(), e);
            }
            if (!((root == null ? null : root.get("data")) instanceof Map<?, ?> feed)) {
                throw new IllegalStateException("租户能力响应缺少 data 节点");
            }
            boolean exists = Boolean.TRUE.equals(feed.get("exists"));
            boolean unbounded = Boolean.TRUE.equals(feed.get("unbounded"));
            Set<String> features = new LinkedHashSet<>();
            if (feed.get("features") instanceof Iterable<?> iterable) {
                for (Object item : iterable) {
                    if (item != null && !item.toString().isBlank()) {
                        features.add(item.toString());
                    }
                }
            }
            return new Snapshot(exists, unbounded, features, System.currentTimeMillis() + Math.max(1000, ttlMillis));
        }

        boolean fresh() {
            return System.currentTimeMillis() < expireAt;
        }

        boolean subscribed(String capabilityCode) {
            if (!exists) {
                return false;
            }
            return CapabilityGate.subscribed(new TenantFeatures(unbounded, features), capabilityCode);
        }
    }
}
