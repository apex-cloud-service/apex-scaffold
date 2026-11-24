package com.apex.scaffold.common.openapi;

import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JsonUtil;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 服务侧开放接口能力二次校验过滤器（方案 D4，共享组件）。
 *
 * <p>触发条件：请求携带网关注入的可信标记头 {@code X-Open-Api-Capability}
 * （外部伪造值由网关剥离；未携带该头的普通业务流量完全不受影响）。校验链：
 *
 * <ol>
 *   <li>可信租户头缺失 → 403（开放流量必须由网关完成租户中继）；</li>
 *   <li>经 {@link TenantCapabilityVerifier}（TTL 缓存）+ 共享
 *       {@link com.apex.scaffold.common.capability.CapabilityGate} 判定租户订阅，
 *       未订阅 → 403 {@link ResultCode#CAPABILITY_NOT_SUBSCRIBED}；</li>
 *   <li>订阅查询失败 → 503（fail-closed，宁可拒绝也不放行未校验的开放流量）。</li>
 * </ol>
 *
 * <p>这是网关能力门禁的<b>纵深防御</b>：绕过网关直连服务的开放流量同样受
 * 同一判定约束。过滤器顺序取最高优先级段，先于业务安全链拒绝。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
public class OpenApiCapabilityGuardFilter implements WebFilter, Ordered {

    /** 过滤器顺序：先于业务安全链做开放流量的能力断言 */
    public static final int ORDER = -200;

    private final OpenApiGuardProperties properties;

    private final TenantCapabilityVerifier verifier;

    /**
     * 构造守卫过滤器。
     *
     * @param properties 守卫配置
     * @param verifier 租户能力校验客户端
     */
    public OpenApiCapabilityGuardFilter(OpenApiGuardProperties properties, TenantCapabilityVerifier verifier) {
        this.properties = properties;
        this.verifier = verifier;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        boolean openPath = path.startsWith(properties.getPathPrefix());
        String marker = exchange.getRequest().getHeaders().getFirst(properties.getMarkerHeader());
        String routeCapability = resolveRouteCapability(path);
        if (isUnmarkedNonOpenPath(openPath, marker)) {
            // 非开放流量：不受本过滤器约束
            return chain.filter(exchange);
        }
        String capability = openPath ? routeCapability : marker;
        if (capability == null || capability.isBlank()) {
            log.warn("开放路径缺少能力映射，拒绝: path={}", path);
            return reject(exchange, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN);
        }
        if (marker != null && !marker.isBlank() && !capability.equals(marker)) {
            log.warn("开放路径能力与标记不一致，拒绝: path={}, route={}, marker={}", path, capability, marker);
            return reject(exchange, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN);
        }
        String tenant = exchange.getRequest().getHeaders().getFirst(properties.getTenantHeader());
        if (tenant == null || tenant.isBlank()) {
            log.warn(
                    "开放流量缺少可信租户头，拒绝: capability={}, path={}",
                    capability,
                    exchange.getRequest().getPath());
            return reject(exchange, HttpStatus.FORBIDDEN, ResultCode.FORBIDDEN);
        }
        return verifier.subscribed(tenant, capability)
                .flatMap(subscribed -> {
                    if (Boolean.TRUE.equals(subscribed)) {
                        return chain.filter(exchange);
                    }
                    log.info(
                            "服务侧能力二次校验拒绝: tenant={}, capability={}, path={}",
                            tenant,
                            capability,
                            exchange.getRequest().getPath());
                    return reject(exchange, HttpStatus.FORBIDDEN, ResultCode.CAPABILITY_NOT_SUBSCRIBED);
                })
                .onErrorResume(error -> {
                    log.warn(
                            "服务侧能力订阅查询失败，fail-closed 拒绝: tenant={}, capability={}, reason={}",
                            tenant,
                            capability,
                            error.getMessage());
                    return reject(exchange, HttpStatus.SERVICE_UNAVAILABLE, ResultCode.SERVICE_UNAVAILABLE);
                });
    }

    /**
     * 按最长前缀从实际请求路径解析能力编码.
     *
     * @param path 请求路径
     * @return 能力编码；未配置映射时返回 {@code null}
     */
    private String resolveRouteCapability(String path) {
        String matched = null;
        String capability = null;
        for (Map.Entry<String, String> entry : properties.getPathCapabilities().entrySet()) {
            if (isPreferredCapabilityPrefix(path, matched, entry.getKey())) {
                matched = entry.getKey();
                capability = entry.getValue();
            }
        }
        return capability;
    }

    private boolean isUnmarkedNonOpenPath(boolean openPath, String marker) {
        return !openPath && (marker == null || marker.isBlank());
    }

    private boolean isPreferredCapabilityPrefix(String path, String matched, String candidate) {
        return path.startsWith(candidate) && (matched == null || candidate.length() > matched.length());
    }

    /**
     * 写出 {@code Result} 封装的 JSON 错误响应.
     *
     * @param exchange 当前交换
     * @param status HTTP 状态码
     * @param resultCode 业务错误码
     */
    private Mono<Void> reject(ServerWebExchange exchange, HttpStatus status, ResultCode resultCode) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = JsonUtil.toJson(Result.error(resultCode)).getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(body);
        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
