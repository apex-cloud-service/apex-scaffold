package com.apex.scaffold.file.infrastructure.tenant;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.file.domain.repository.DocumentRepository;
import com.apex.scaffold.file.domain.service.StorageQuotaPort;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 远程存储配额适配器（方案批次 B5）。
 *
 * <p>消费主应用内部端点 {@code GET /internal/v1/tenants/{code}/status}（A6）获取
 * {@code maxStorageMb}，与本租户已用字节聚合（{@link DocumentRepository#sumSizeByTenant}，
 * 结果缓存 {@code usage-cache-seconds}）比对后断言。降级口径（软上限语义）：
 * <ul>
 *   <li>{@code base-url} 未配置或配额上限为 NULL（不限制）→ 放行；</li>
 *   <li>租户中心不可达 → 放行并告警（不因配额设施故障阻断业务上传）。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
public class RemoteStorageQuotaAdapter implements StorageQuotaPort {

    private final String baseUrl;
    private final long cacheMillis;
    private final DocumentRepository documentRepository;
    private final WebClient webClient;

    /** 已用字节缓存：tenantCode → (bytes, 过期时间) */
    private final Map<String, CachedUsage> usageCache = new ConcurrentHashMap<>();

    /** 已用字节缓存条目。 */
    private record CachedUsage(long bytes, long expiresAt) {}

    /**
     * 构造适配器。
     *
     * @param baseUrl           租户中心内部基址；空 = 配额校验跳过
     * @param usageCacheSeconds 已用字节缓存秒数
     * @param documentRepository 文档仓储（本租户已用聚合）
     */
    public RemoteStorageQuotaAdapter(
            @Value("${apex.tenant-check.base-url:}") String baseUrl,
            @Value("${apex.storage-quota.usage-cache-seconds:60}") long usageCacheSeconds,
            DocumentRepository documentRepository) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.cacheMillis = Math.max(5, usageCacheSeconds) * 1000L;
        this.documentRepository = documentRepository;
        this.webClient = WebClient.builder().build();
    }

    @Override
    public Mono<Void> assertStorageQuota(String tenantCode, long incrementBytes) {
        if (baseUrl.isBlank() || tenantCode == null || tenantCode.isBlank()) {
            return Mono.empty();
        }
        return Mono.defer(() -> currentUsage(tenantCode).flatMap(usedBytes -> fetchLimitMb(tenantCode)
                .doOnNext(limitMb -> assertWithinQuota(tenantCode, usedBytes, incrementBytes, limitMb))
                .then()));
    }

    /** 本租户已用字节（缓存窗口内直读缓存）。 */
    private Mono<Long> currentUsage(String tenantCode) {
        CachedUsage cached = usageCache.get(tenantCode);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt() > now) {
            return Mono.just(cached.bytes());
        }
        return documentRepository
                .sumSizeByTenant(tenantCode)
                .doOnNext(bytes -> usageCache.put(tenantCode, new CachedUsage(bytes, now + cacheMillis)));
    }

    /**
     * 拉取租户存储上限（MB）；NULL = 不限制。租户中心不可达时返回 NULL 放行（软上限口径）。
     */
    private Mono<Long> fetchLimitMb(String tenantCode) {
        return webClient
                .get()
                .uri(baseUrl + "/internal/v1/tenants/" + tenantCode + "/status")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(3))
                .map(this::parseLimitMb)
                .onErrorResume(e -> {
                    log.warn("存储配额查询失败，软上限放行: tenant={}, reason={}", tenantCode, e.getMessage());
                    return Mono.empty();
                });
    }

    /** 解析 {@code Result} 封装的状态快照中的存储上限；NULL = 不限制。 */
    Long parseLimitMb(String body) {
        Map<?, ?> root = JsonUtil.fromJson(body, Map.class);
        Object data = root == null ? null : root.get("data");
        if (data instanceof Map<?, ?> feed) {
            Object limit = feed.get("maxStorageMb");
            if (limit instanceof Number number) {
                return number.longValue();
            }
        }
        return null;
    }

    private void assertWithinQuota(String tenantCode, long usedBytes, long incrementBytes, Long limitMb) {
        if (limitMb == null) {
            return;
        }
        long limitBytes = limitMb * 1024L * 1024L;
        if (usedBytes + incrementBytes > limitBytes) {
            log.warn(
                    "存储配额超限: tenant={}, used={}, increment={}, limit={}MB",
                    tenantCode,
                    usedBytes,
                    incrementBytes,
                    limitMb);
            throw new BusinessException(ResultCode.TENANT_QUOTA_EXCEEDED, "存储配额超限，请清理后重试或联系管理员扩容");
        }
    }
}
