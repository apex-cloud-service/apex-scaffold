package com.apex.scaffold.authcenter.infrastructure.filter;

import com.apex.scaffold.authcenter.domain.service.IpAccessControlService;
import com.apex.scaffold.common.audit.AuditLogService;
import com.apex.scaffold.common.util.LogSafetyUtil;
import java.net.InetSocketAddress;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * IP访问控制过滤器
 *
 * 在所有请求处理之前执行IP访问控制检查
 * Order = -100 确保在认证过滤器之前执行
 * @author Apex Team
 * @since 1.2.0
 */
@Slf4j
@Order(-100)
@RequiredArgsConstructor
public class IpAccessControlFilter implements WebFilter {

    private final IpAccessControlService ipAccessControlService;
    private final AuditLogService auditLogService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        // 获取客户端IP
        String clientIp = getClientIp(exchange);

        // 获取租户ID（从请求头或JWT中获取）
        String tenantId = getTenantId(exchange);

        // 白名单路径（健康检查、监控端点等不需要IP控制）
        if (isWhitelistedPath(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        return ipAccessControlService
                .isAccessAllowed(clientIp, tenantId)
                .onErrorResume(error -> {
                    // 发生错误时默认拒绝访问
                    exchange.getAttributes()
                            .put(
                                    IpAccessControlFilter.class.getName() + ".failure",
                                    error.getClass().getSimpleName());
                    return Mono.just(false);
                })
                .flatMap(allowed -> {
                    if (allowed) {
                        // 允许访问，继续过滤链
                        return chain.filter(exchange);
                    }

                    // 拒绝访问，先完成审计再返回403
                    log.warn(
                            "IP访问被拒绝: ip={}, tenantId={}, path={}, error={}",
                            LogSafetyUtil.text(clientIp, 64),
                            LogSafetyUtil.text(tenantId, 64),
                            LogSafetyUtil.uri(exchange.getRequest().getURI().getRawPath(), 256),
                            exchange.getAttributeOrDefault(IpAccessControlFilter.class.getName() + ".failure", "none"));

                    return auditLogService
                            .log(
                                    "system",
                                    tenantId,
                                    "IP_ACCESS_DENIED",
                                    LogSafetyUtil.uri(
                                            exchange.getRequest().getURI().getRawPath(), 256),
                                    "DENIED",
                                    null)
                            .then(Mono.defer(() -> {
                                exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                                return exchange.getResponse().setComplete();
                            }));
                });
    }

    /**
     * 获取客户端真实IP地址
     *
     * 优先级：
     * 1. X-Forwarded-For（反向代理）
     * 2. X-Real-IP（Nginx）
     * 3. RemoteAddress（直连）
     */
    private String getClientIp(ServerWebExchange exchange) {
        // 1. 检查 X-Forwarded-For
        String xff = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) {
            // X-Forwarded-For 可能包含多个IP，取第一个
            return xff.split(",")[0].trim();
        }

        // 2. 检查 X-Real-IP
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }

        // 3. 使用 RemoteAddress
        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress != null) {
            return remoteAddress.getAddress().getHostAddress();
        }

        return "unknown";
    }

    /**
     * 获取租户ID
     *
     * 优先级：
     * 1. X-Tenant-Id 请求头
     * 2. JWT Token中的租户信息（后续可扩展）
     * 3. 默认租户
     */
    private String getTenantId(ServerWebExchange exchange) {
        // 1. 从请求头获取
        String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
        if (tenantId != null && !tenantId.isEmpty()) {
            return tenantId;
        }

        // 2. 从JWT Token中解析（后续可扩展）
        // String token = extractToken(exchange);
        // if (token != null) {
        //     return parsetenantIdFromToken(token);
        // }

        // 3. 使用默认租户
        return "default";
    }

    /**
     * 判断是否为白名单路径（不需要IP控制）
     */
    private boolean isWhitelistedPath(String path) {
        return path.startsWith("/actuator/")
                || path.startsWith("/health")
                || path.startsWith("/metrics")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }
}
