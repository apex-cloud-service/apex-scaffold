package com.apex.scaffold.authservice.config;

import com.apex.scaffold.auth.domain.service.TenantStatusPort;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.contract.dto.internal.TenantStatusSnapshotDTO;
import com.fasterxml.jackson.core.type.TypeReference;
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
 * 远程租户状态校验适配器（统一认证收口 A6）。
 *
 * <p>消费主应用内部端点 {@code GET /internal/v1/tenants/{code}/status}，对登录请求的
 * 租户编码做前置校验：未知、非 ACTIVE、过期一律拒绝；判定结果（通过/拒绝）缓存
 * {@code cache-seconds}。降级口径：
 * <ul>
 *   <li>{@code base-url} 未配置（basic/dev 档）→ 校验整体跳过；</li>
 *   <li>租户中心不可达 → fail-closed 拒绝，仅平台默认租户（{@code default}）白名单放行，
 *       保证平台自身可登录自愈。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
public class RemoteTenantStatusAdapter implements TenantStatusPort {

    /** 平台默认租户编码：租户中心不可达时的白名单兜底 */
    private static final String PLATFORM_TENANT = "default";

    /** 活跃状态（唯一可登录状态机取值） */
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final String baseUrl;
    private final long cacheMillis;
    private final long timeoutMillis;
    private final WebClient webClient;

    /** 校验决策缓存：code → (allowed, 过期时间) */
    private final Map<String, Decision> decisions = new ConcurrentHashMap<>();

    /** 校验决策（带过期）。 */
    private record Decision(boolean allowed, long expiresAt) {}

    /**
     * 构造适配器。
     *
     * @param baseUrl 租户中心内部基址（主应用）；空 = 校验跳过
     * @param cacheSeconds 判定缓存秒数
     * @param timeoutMillis 单次拉取超时
     * @param internalAuthProperties 内部认证配置
     * @param internalServiceSigner 内部签名器；未配置时不签名
     */
    public RemoteTenantStatusAdapter(
            @Value("${apex.tenant-check.base-url:}") String baseUrl,
            @Value("${apex.tenant-check.cache-seconds:60}") long cacheSeconds,
            @Value("${apex.tenant-check.timeout-millis:3000}") long timeoutMillis,
            com.apex.scaffold.common.internal.InternalAuthProperties internalAuthProperties,
            com.apex.scaffold.common.internal.InternalServiceSigner internalServiceSigner) {
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim();
        this.cacheMillis = Math.max(5, cacheSeconds) * 1000L;
        this.timeoutMillis = Math.max(500, timeoutMillis);
        this.webClient = com.apex.scaffold.common.internal.InternalAuthExchangeFilter.apply(
                        WebClient.builder(), internalAuthProperties, internalServiceSigner)
                .build();
        if (this.baseUrl.isBlank()) {
            log.info("租户认证期校验未配置租户中心地址（apex.tenant-check.base-url 为空），登录前置校验跳过");
        }
    }

    @Override
    public Mono<Void> validateLoginTenant(String tenantCode) {
        if (baseUrl.isBlank() || tenantCode == null || tenantCode.isBlank()) {
            return Mono.empty();
        }
        Decision cached = decisions.get(tenantCode);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAt() > now) {
            return cached.allowed() ? Mono.empty() : reject(tenantCode, "租户不可用");
        }
        return fetch(tenantCode)
                .flatMap(snapshot -> {
                    boolean allowed =
                            snapshot.exists() && STATUS_ACTIVE.equals(snapshot.status()) && !snapshot.expired();
                    decisions.put(tenantCode, new Decision(allowed, now + cacheMillis));
                    if (allowed) {
                        return Mono.empty();
                    }
                    if (!snapshot.exists()) {
                        return reject(tenantCode, "租户不存在");
                    }
                    if (snapshot.expired()) {
                        return reject(tenantCode, "租户已过期");
                    }
                    return reject(tenantCode, "租户不可用（状态: " + snapshot.status() + "）");
                })
                // fail-closed 兜底只针对回源失败：业务拒绝（租户不存在/过期/不可用）原样传播，
                // 不被兜底改写文案（否则审计无法区分"查不到"与"确认拒绝"）
                .onErrorResume(e -> {
                    if (e instanceof BusinessException) {
                        return Mono.error(e);
                    }
                    log.warn("租户状态查询失败，fail-closed（平台租户白名单除外）: tenant={}, reason={}", tenantCode, e.getMessage());
                    if (PLATFORM_TENANT.equals(tenantCode)) {
                        return Mono.empty();
                    }
                    return reject(tenantCode, "租户状态暂不可用");
                });
    }

    /**
     * 拉取租户状态快照（测试可覆写注入替身）。
     *
     * @param tenantCode 租户编码
     * @return exists / status / expired 三元快照
     */
    Mono<Snapshot> fetch(String tenantCode) {
        return webClient
                .get()
                .uri(baseUrl + "/internal/v1/tenants/" + tenantCode + "/status")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofMillis(timeoutMillis))
                .map(this::parse);
    }

    /**
     * 解析 {@code Result} 封装的状态快照。
     *
     * <p>data 节点为 {@code apex-api-contract} 的 {@link TenantStatusSnapshotDTO} 契约类型，
     * 与 system 提供方共用同一类型，避免手写 JSON 解析随提供方字段演进而漂移
     * （2026-11-07 模块评审 F3）。
     */
    Snapshot parse(String body) {
        Result<TenantStatusSnapshotDTO> root =
                JsonUtil.fromJson(body, new TypeReference<Result<TenantStatusSnapshotDTO>>() {});
        if (root == null || root.getData() == null) {
            throw new IllegalStateException("租户状态响应缺少 data 节点");
        }
        TenantStatusSnapshotDTO feed = root.getData();
        return new Snapshot(feed.isExists(), feed.getStatus(), feed.isExpired());
    }

    /**
     * 失效指定租户的判定缓存（主数据事件驱动，方案 D5）。
     *
     * <p>租户禁用/过期等状态变更经 {@code TENANT_CHANGED} 事件通知后立即失效本地
     * 判定缓存，下一次登录校验强制回源——租户状态变更最迟在一个事件投递周期内生效，
     * 而不必等待 {@code cache-seconds} TTL 自然过期。消费天然幂等（重复失效同键）。
     *
     * @param tenantCode 租户编码；空白时清空全部缓存
     */
    public void invalidate(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            decisions.clear();
            return;
        }
        decisions.remove(tenantCode);
    }

    private Mono<Void> reject(String tenantCode, String reason) {
        log.debug("登录拒绝（租户校验）: tenant={}, reason={}", tenantCode, reason);
        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, reason));
    }

    /** 租户状态快照。 */
    record Snapshot(boolean exists, String status, boolean expired) {}
}
