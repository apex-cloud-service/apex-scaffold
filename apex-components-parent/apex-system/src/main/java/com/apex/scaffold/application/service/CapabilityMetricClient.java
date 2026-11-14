package com.apex.scaffold.application.service;

import com.apex.scaffold.application.config.CapabilityUsageProperties;
import com.apex.scaffold.application.config.OverviewProperties;
import com.apex.scaffold.common.util.JsonUtil;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 能力存量指标客户端（方案 D4 四维度计量）。
 *
 * <p>按能力编码拉取对应域的内部统计端点（存储用量/设备数/消息量），短 TTL 缓存。
 * 基址复用 {@code apex.overview.sources}（与聚合门面同一数据源配置）。
 *
 * <p><b>降级口径</b>：数据源未配置、不可达、超时或响应不可解析时返回空 Mono——
 * 计量为只读增值信息，任何失败都不得阻断用量查询，对应 DTO 字段保持 {@code null}。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Service
public class CapabilityMetricClient {

    private final CapabilityUsageProperties properties;
    private final OverviewProperties overviewProperties;
    private final WebClient webClient;

    /** {@code capability:tenantId} → 存量指标（TTL 内复用） */
    private final ConcurrentHashMap<String, MetricSnapshot> cache = new ConcurrentHashMap<>();

    /**
     * 构造存量指标客户端。
     *
     * @param properties 计量配置（指标来源映射与超时/缓存）
     * @param overviewProperties 聚合门面数据源配置（复用基址）
     */
    public CapabilityMetricClient(
            CapabilityUsageProperties properties,
            OverviewProperties overviewProperties,
            com.apex.scaffold.common.internal.InternalAuthProperties internalAuthProperties,
            com.apex.scaffold.common.internal.InternalServiceSigner internalServiceSigner) {
        this.properties = properties;
        this.overviewProperties = overviewProperties;
        this.webClient = com.apex.scaffold.common.internal.InternalAuthExchangeFilter.apply(
                        WebClient.builder(), internalAuthProperties, internalServiceSigner)
                .build();
    }

    /**
     * 拉取指定能力在指定租户下的存量指标（带 TTL 缓存，按源降级）。
     *
     * @param capability 能力编码
     * @param tenantId 租户标识（空视为不填充，返回空 Mono）
     * @return 指标数值；能力无存量维度或数据源失败时为空 Mono
     */
    public Mono<Long> fetch(String capability, String tenantId) {
        CapabilityUsageProperties.MetricSource source =
                capability == null ? null : properties.getMetrics().get(capability);
        if (source == null || tenantId == null || tenantId.isBlank()) {
            return Mono.empty();
        }
        String cacheKey = capability + ":" + tenantId;
        MetricSnapshot snapshot = cache.get(cacheKey);
        if (snapshot != null && snapshot.fresh()) {
            return Mono.just(snapshot.value());
        }
        return fetchRaw(source, tenantId)
                .timeout(Duration.ofMillis(Math.max(200, properties.getMetricTimeoutMillis())))
                .map(body -> parseValue(body, source.field()))
                .doOnNext(value ->
                        cache.put(cacheKey, new MetricSnapshot(value, System.currentTimeMillis() + metricCacheTtl())))
                // 计量为增值信息：任何失败按源降级为空，不阻断用量查询
                .onErrorResume(error -> {
                    log.info(
                            "能力存量指标拉取失败，按源降级: capability={}, tenant={}, reason={}",
                            capability,
                            tenantId,
                            error.getMessage());
                    return Mono.empty();
                });
    }

    /** 缓存 TTL（毫秒），下限 1s。 */
    private long metricCacheTtl() {
        return Math.max(1000, properties.getMetricCacheTtlMillis());
    }

    /**
     * 拉取统计端点响应原文（包内可见，测试可覆写注入替身响应）。
     *
     * @param source 指标来源定义
     * @param tenantId 租户标识
     * @return {@code Result} 封装的统计 JSON
     */
    Mono<String> fetchRaw(CapabilityUsageProperties.MetricSource source, String tenantId) {
        String baseUrl = overviewProperties.getSources().get(source.source());
        if (baseUrl == null || baseUrl.isBlank()) {
            return Mono.error(new IllegalStateException("数据源未配置: " + source.source()));
        }
        String uri = baseUrl
                + source.uriTemplate().replace("{tenantId}", URLEncoder.encode(tenantId, StandardCharsets.UTF_8));
        return webClient
                .get()
                .uri(uri)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class);
    }

    /**
     * 解析 {@code Result} 封装的 {@code data.field} 数值。
     *
     * @param body 响应体 JSON
     * @param field 取值字段名
     * @return 指标数值；缺失/非法时抛错（由上层降级）
     */
    private long parseValue(String body, String field) {
        Map<?, ?> root = JsonUtil.fromJson(body, Map.class);
        if (root == null || !(root.get("data") instanceof Map<?, ?> data)) {
            throw new IllegalStateException("统计响应缺少 data 节点");
        }
        Object value = data.get(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("统计响应缺少字段: " + field);
    }

    /**
     * 存量指标缓存条目.
     *
     * @param value 指标数值
     * @param expireAt 缓存过期时间（epoch 毫秒）
     */
    private record MetricSnapshot(long value, long expireAt) {

        boolean fresh() {
            return System.currentTimeMillis() < expireAt;
        }
    }
}
