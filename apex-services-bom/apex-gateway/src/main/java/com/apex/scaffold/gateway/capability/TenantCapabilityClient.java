package com.apex.scaffold.gateway.capability;

import com.apex.scaffold.common.capability.TenantCapabilitySnapshot;
import com.apex.scaffold.common.capability.TenantCapabilitySnapshotCache;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 租户能力订阅查询客户端（D4 能力订阅拒绝）。
 *
 * <p>经 system 内部端点 {@code GET /internal/v1/tenants/{code}/capabilities} 拉取租户
 * 能力订阅快照（{@code exists/unbounded/features}），本地短 TTL 缓存后交由共享的
 * {@link com.apex.scaffold.common.capability.CapabilityGate} 判定——与业务服务的二次校验共用同一判定实现，保证口径一致。
 *
 * <p>fail-closed 口径：租户编码缺失、租户不存在（{@code exists=false}）一律视为未订阅；
 * 内部端点不可达或响应不可解析时向上发出错误，由调用方决定降级出口（网关按 503 拒绝）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
public class TenantCapabilityClient {

    private final TenantCapabilityProperties properties;
    private final WebClient webClient;

    /** 租户编码 → 能力快照（TTL 内复用，避免内部端点被开放接口流量放大） */
    private final TenantCapabilitySnapshotCache cache = new TenantCapabilitySnapshotCache();

    /**
     * Spring 主构造器：构造能力查询客户端（带内部调用签名）。
     *
     * <p>本类仅此一个构造器，Spring 按唯一构造器注入，无需 {@code @Autowired}。
     * 无签名场景（测试等）显式传入空的 {@link com.apex.scaffold.common.internal.InternalAuthProperties}
     * 与 {@code null} 签名器。
     *
     * @param properties 能力门禁配置
     * @param internalAuthProperties 内部认证配置
     * @param signer 内部签名器；未配置时不签名
     */
    public TenantCapabilityClient(
            TenantCapabilityProperties properties,
            com.apex.scaffold.common.internal.InternalAuthProperties internalAuthProperties,
            com.apex.scaffold.common.internal.InternalServiceSigner signer) {
        this.properties = properties;
        this.webClient = com.apex.scaffold.common.internal.InternalAuthExchangeFilter.apply(
                        WebClient.builder(), internalAuthProperties, signer)
                .build();
    }

    /**
     * 判定租户是否已订阅指定能力（带 TTL 缓存）。
     *
     * @param tenantCode 租户编码（缺失按未订阅处理）
     * @param capabilityCode 能力编码（如 {@code file-storage}）
     * @return 已订阅返回 {@code true}；内部端点不可达时以错误信号终止
     */
    public Mono<Boolean> subscribed(String tenantCode, String capabilityCode) {
        if (!properties.isEnabled()) {
            return Mono.just(Boolean.TRUE);
        }
        if (tenantCode == null || tenantCode.isBlank()) {
            return Mono.just(Boolean.FALSE);
        }
        TenantCapabilitySnapshot snapshot = cache.getFresh(tenantCode);
        if (snapshot != null) {
            return Mono.just(snapshot.subscribed(capabilityCode));
        }
        return fetch(tenantCode)
                .timeout(Duration.ofMillis(Math.max(200, properties.getTimeoutMillis())))
                .map(body -> {
                    TenantCapabilitySnapshot parsed =
                            TenantCapabilitySnapshot.parse(body, properties.getCacheTtlMillis());
                    cache.put(tenantCode, parsed);
                    warnIfAmbiguous(tenantCode, parsed);
                    return parsed.subscribed(capabilityCode);
                });
    }

    /**
     * 提示能力快照语义可能缺失：存在租户、非不限量，但未配置任何特性。
     *
     * <p>收紧后空特性集合表示“未订阅任何能力”，若来源于租户套餐配置缺失，
     * 该租户的全部开放能力都会被拒绝。此处仅告警便于发现数据缺陷，不改变拒绝语义。
     *
     * @param tenantCode 租户编码
     * @param snapshot 解析后的能力快照
     */
    private void warnIfAmbiguous(String tenantCode, TenantCapabilitySnapshot snapshot) {
        if (snapshot.exists() && !snapshot.unbounded() && snapshot.features().isEmpty()) {
            log.warn("租户 {} 能力快照存在但特性集合为空（unbounded=false），将拒绝其全部开放能力；" + "若无配额限制请将 unbounded 置为 true", tenantCode);
        }
    }

    /**
     * 拉取租户能力快照原文（包内可见，测试可覆写注入替身响应）.
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
}
