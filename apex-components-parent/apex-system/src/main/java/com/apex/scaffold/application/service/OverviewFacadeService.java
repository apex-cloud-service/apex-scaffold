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
import java.util.concurrent.Semaphore;
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
 *   <li>三个统计端点返回租户级聚合数据（不含用户个体数据），因此成功结果按租户缓存共享；
 *       若未来任一端点引入用户维度数据，必须同步改造缓存键</li>
 *   <li>仅全源 UP 的结果进入租户缓存；降级结果不入缓存，下一次请求立即重试下游，
 *       避免个别调用者的鉴权/下游故障被 30s TTL 放大到整个租户</li>
 *   <li>同一（租户、调用方凭证）的并发请求合并为一次下游刷新，避免缓存过期瞬间打满三个源；
 *       不同凭证不合并，防止共享彼此的鉴权结果</li>
 *   <li>每个数据源带独立并发舱壁（JDK {@link java.util.concurrent.Semaphore}，等价于
 *       Resilience4j {@code SemaphoreBulkhead} 的饱和即降级语义）：并发上限可按源配置，
 *       饱和时该源立即降级不排队；不引入 Resilience4j 依赖，因其在本项目 basic 档为裁剪依赖，
 *       门面直接引用会导致 basic 运行时类缺失</li>
 *   <li>调用下游时透传调用方的 {@code Authorization} 头：workflow 内部端点要求
 *       已认证并从验签 JWT 建立租户上下文；无凭证时该源自然降级</li>
 *   <li>WebClient 为原生异步调用，不占用阻塞调度器</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
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

    /** 进行中的聚合请求：同租户同凭证的并发请求合并为一次下游刷新。 */
    private final ConcurrentHashMap<InFlightKey, Mono<OverviewResponse>> inFlight = new ConcurrentHashMap<>();

    /** 数据源名称 → 并发隔离舱壁；按需创建，许可数来自配置。 */
    private final ConcurrentHashMap<String, Semaphore> bulkheads = new ConcurrentHashMap<>();

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
     * <p>优先命中租户缓存；未命中时按（租户、调用方凭证）合并进行中的并发刷新，
     * 仅全源 UP 的结果写入租户缓存。
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
        InFlightKey key = new InFlightKey(tenantId, authorization);
        return inFlight.computeIfAbsent(key, k -> fetchAndAssemble(tenantId, authorization)
                .doFinally(signal -> inFlight.remove(k))
                .cache());
    }

    /**
     * 并行拉取三个数据源并组装概览响应；降级结果不写租户缓存。
     *
     * @param tenantId      租户标识
     * @param authorization 透传的 Authorization 头
     * @return 聚合响应
     */
    private Mono<OverviewResponse> fetchAndAssemble(String tenantId, String authorization) {
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
                .doOnNext(response -> {
                    // 仅缓存全源 UP 的结果：降级结果不入缓存，下一次请求立即重试下游
                    if (!response.degraded()) {
                        cache.put(
                                tenantId,
                                new CachedOverview(
                                        response, System.currentTimeMillis() + properties.getCacheTtlSeconds() * 1000));
                    }
                });
    }

    /**
     * 拉取单个数据源并映射为单源结果；任何失败均降级，不向上传播错误。
     *
     * <p>并发隔离：订阅时先获取该源舱壁许可，饱和（并发已达上限）时立即降级不排队；
     * 许可随上游调用终止（完成/错误/超时）释放。并发合并（{@code cache()}）下，
     * 单个调用方取消不会中断共享刷新，许可释放在上游终止时发生（超时为上界）。
     *
     * @param name          数据源名称（日志与舱壁键）
     * @param url           完整统计端点地址；未配置时为 {@code null}
     * @param authorization 透传的 Authorization 头
     * @return 单源结果（永不错误）
     */
    protected Mono<SourceResult> fetchSource(String name, String url, String authorization) {
        if (url == null) {
            return Mono.just(SourceResult.degraded("source not configured"));
        }
        Semaphore bulkhead = bulkheadFor(name);
        return Mono.defer(() -> {
            if (!bulkhead.tryAcquire()) {
                log.warn("聚合门面数据源舱壁饱和: source={}", name);
                return Mono.just(SourceResult.degraded("bulkhead saturated"));
            }
            return fetchBody(url, authorization)
                    .timeout(Duration.ofMillis(properties.getTimeoutMillis()))
                    .map(this::parseEnvelope)
                    .onErrorResume(error -> {
                        log.warn("聚合门面数据源降级: source={}, error={}", name, error.toString());
                        return Mono.just(SourceResult.degraded(error.getClass().getSimpleName()));
                    })
                    .doFinally(signal -> bulkhead.release());
        });
    }

    /**
     * 读取数据源的并发舱壁；按源名惰性创建，许可数取源级配置或全局默认值。
     *
     * @param name 数据源名称
     * @return 该源的舱壁信号量
     */
    private Semaphore bulkheadFor(String name) {
        return bulkheads.computeIfAbsent(
                name,
                source -> new Semaphore(
                        properties.getBulkhead().getOrDefault(source, properties.getDefaultBulkheadMaxConcurrency())));
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

    /**
     * 进行中聚合请求的键：同租户且同调用方凭证才合并并发刷新，
     * 避免不同凭证的请求共享彼此的鉴权结果。
     */
    private record InFlightKey(String tenantId, String authorization) {}
}
