package com.apex.scaffold.application.service;

import com.apex.scaffold.application.config.OverviewProperties;
import com.apex.scaffold.application.dto.internal.OverviewResponse;
import com.apex.scaffold.application.dto.internal.OverviewResponse.SourceResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 跨域只读聚合门面服务（方案 D5）。
 *
 * <p>并行拉取文件存储用量、工作流进行中实例数与集成设备数三个只读统计端点，
 * 聚合为统一概览负载；任一数据源超时、不可达或返回业务错误时，该源降级为
 * {@code DEGRADED}（结果缺失 + 错误摘要），不影响其他数据源（部分可用）。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>数据源地址走 {@code apex.overview.sources.*} 配置，默认指向本机各服务端口</li>
 *   <li>聚合结果按租户做短 TTL 缓存（默认 30s），降低对下游统计端点的放大</li>
 *   <li>调用下游时透传调用方的 {@code Authorization} 头：workflow 内部端点要求
 *       已认证并从验签 JWT 建立租户上下文；无凭证时该源自然降级</li>
 *   <li>WebClient 为原生异步调用，不占用阻塞调度器</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.2
 */
@Slf4j
@Service
public class OverviewFacadeService {

    private final OverviewProperties properties;

    private final ObjectMapper objectMapper;

    private final WebClient webClient;

    /** 租户维度短 TTL 缓存。 */
    private final ConcurrentHashMap<String, CachedOverview> cache = new ConcurrentHashMap<>();

    /**
     * 构造聚合门面。
     *
     * @param properties 概览聚合配置
     * @param objectMapper JSON 序列化器
     * @param webClientBuilder Boot 管理的客户端构建器
     * @param internalAuthProperties 内部认证配置
     * @param internalServiceSigner 内部签名器
     */
    public OverviewFacadeService(
            OverviewProperties properties,
            ObjectMapper objectMapper,
            WebClient.Builder webClientBuilder,
            com.apex.scaffold.common.internal.InternalAuthProperties internalAuthProperties,
            com.apex.scaffold.common.internal.InternalServiceSigner internalServiceSigner) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.webClient = com.apex.scaffold.common.internal.InternalAuthExchangeFilter.apply(
                        webClientBuilder, internalAuthProperties, internalServiceSigner)
                .build();
    }

    /**
     * 查询租户维度的跨域概览聚合。
     *
     * @param tenantId      租户标识（租户编码）
     * @param authorization 调用方 Authorization 头（原样透传给需要认证的数据源，可为空）
     * @return 聚合响应（部分数据源降级时 {@code degraded=true}）
     */
    public Mono<OverviewResponse> getOverview(String tenantId, String authorization) {
        long now = System.currentTimeMillis();
        CachedOverview cached = cache.get(tenantId);
        if (cached != null && cached.expiresAtMillis() > now) {
            return Mono.just(cached.response());
        }
        Mono<SourceResult> file = fetchSource(
                "file", buildUrl("file", "/internal/v1/file/stats?tenantId=" + encode(tenantId)), authorization);
        Mono<SourceResult> workflow =
                fetchSource("workflow", buildUrl("workflow", "/internal/v1/workflow/stats"), authorization);
        Mono<SourceResult> integration = fetchSource(
                "integration",
                buildUrl("integration", "/internal/v1/iot/devices/stats?tenantId=" + encode(tenantId)),
                authorization);
        return Mono.zip(file, workflow, integration)
                .map(tuple -> {
                    Map<String, SourceResult> sources = new LinkedHashMap<>();
                    sources.put("file", tuple.getT1());
                    sources.put("workflow", tuple.getT2());
                    sources.put("integration", tuple.getT3());
                    boolean degraded = sources.values().stream().anyMatch(source -> !"UP".equals(source.status()));
                    return new OverviewResponse(tenantId, LocalDateTime.now(), degraded, sources);
                })
                .doOnNext(response -> cache.put(
                        tenantId,
                        new CachedOverview(
                                response, System.currentTimeMillis() + properties.getCacheTtlSeconds() * 1000)));
    }

    /**
     * 拉取单个数据源并映射为单源结果；任何失败均降级，不向上传播错误。
     *
     * @param name          数据源名称（日志用）
     * @param url           完整统计端点地址；未配置时为 {@code null}
     * @param authorization 透传的 Authorization 头
     * @return 单源结果（永不错误）
     */
    protected Mono<SourceResult> fetchSource(String name, String url, String authorization) {
        if (url == null) {
            return Mono.just(SourceResult.degraded("source not configured"));
        }
        return fetchBody(url, authorization)
                .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                .map(this::parseEnvelope)
                .onErrorResume(error -> {
                    log.warn("聚合门面数据源降级: source={}, error={}", name, error.toString());
                    return Mono.just(SourceResult.degraded(error.getClass().getSimpleName()));
                });
    }

    /**
     * 执行数据源 HTTP GET；调用方的 {@code Authorization} 头非空时原样透传。
     *
     * <p>可见性为 {@code protected} 仅供测试覆写注入桩响应体；WebClient 原生异步，
     * 不占用阻塞调度器。
     *
     * @param url           完整统计端点地址
     * @param authorization 透传的 Authorization 头
     * @return 响应体字符串
     */
    protected Mono<String> fetchBody(String url, String authorization) {
        WebClient.RequestHeadersSpec<?> spec = webClient.get().uri(url).accept(MediaType.APPLICATION_JSON);
        if (authorization != null && !authorization.isBlank()) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, authorization);
        }
        return spec.retrieve().bodyToMono(String.class);
    }

    /**
     * 解析下游 {@code Result} 信封：成功取 {@code data} 原始负载，失败或解析异常降级。
     *
     * @param body 下游响应体
     * @return 单源结果
     */
    private SourceResult parseEnvelope(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.path("code").asInt(-1) == 0) {
                return SourceResult.up(root.path("data"));
            }
            return SourceResult.degraded(root.path("msg").asText("downstream business error"));
        } catch (Exception ex) {
            return SourceResult.degraded("invalid response body");
        }
    }

    /**
     * 读取数据源基址并去除尾部斜杠；未配置时返回 {@code null}（该源降级）。
     *
     * @param name 数据源名称
     * @return 服务基址或 {@code null}
     */
    private String sourceUri(String name) {
        String base = properties.getSources().get(name);
        if (base == null || base.isBlank()) {
            return null;
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /**
     * 拼接数据源完整地址；基址未配置时返回 {@code null}，避免 {@code null} 与路径
     * 字符串拼接成非空伪地址（那样会绕过 {@link #fetchSource} 的未配置降级分支）。
     *
     * @param name 数据源名称
     * @param path 端点路径（以 {@code /} 开头）
     * @return 完整地址或 {@code null}
     */
    private String buildUrl(String name, String path) {
        String base = sourceUri(name);
        return base == null ? null : base + path;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 缓存条目：聚合响应与过期时间。 */
    private record CachedOverview(OverviewResponse response, long expiresAtMillis) {}
}
