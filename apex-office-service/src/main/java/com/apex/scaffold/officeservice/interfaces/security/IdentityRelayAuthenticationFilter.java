package com.apex.scaffold.officeservice.interfaces.security;

import com.apex.scaffold.common.context.TenantContext;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 身份中继认证过滤器。
 *
 * <p>办公服务位于网关之后，请求的身份信息由网关（验签并剥离伪造头）与
 * 用户上下文中继（注入可信头）共同保障。本过滤器消费网关注入的可信请求头
 * {@code X-User-Id} / {@code X-Tenant-Id}，构建 Spring Security 认证上下文，
 * 并以响应式 Context 的方式注入租户上下文 {@link TenantContext}。
 *
 * <h2>安全约束</h2>
 * <ul>
 *   <li>仅信任网关注入的请求头；伪造头已在网关侧剥离</li>
 *   <li>当 {@code X-User-Id} 缺失或非法时，不写入任何认证信息，
 *       后续由 Spring Security 的 {@code anyExchange().authenticated()} 统一返回 401</li>
 *   <li>不重复校验 JWT（网关已完成验签），避免服务侧重复解密开销</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdentityRelayAuthenticationFilter implements WebFilter {

    /** 请求头：用户 ID（网关注入，服务侧信任） */
    public static final String HEADER_USER_ID = "X-User-Id";

    /** 请求头：租户 ID（网关注入，服务侧信任） */
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";

    /** 请求头：客户端类型（网关注入） */
    public static final String HEADER_CLIENT_TYPE = "X-Client-Type";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String userIdHeader = request.getHeaders().getFirst(HEADER_USER_ID);
        if (!StringUtils.hasText(userIdHeader)) {
            return chain.filter(exchange);
        }

        Long userId;
        try {
            userId = Long.valueOf(userIdHeader.trim());
        } catch (NumberFormatException ex) {
            return chain.filter(exchange);
        }

        String tenantId = request.getHeaders().getFirst(HEADER_TENANT_ID);
        String clientType = request.getHeaders().getFirst(HEADER_CLIENT_TYPE);

        AuthenticatedUser principal = new AuthenticatedUser(userId, null, tenantId);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, List.<SimpleGrantedAuthority>of());
        authentication.setDetails(clientType);

        Mono<Void> result =
                chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));

        if (StringUtils.hasText(tenantId)) {
            return TenantContext.withTenant(result).contextWrite(TenantContext.withTenant(tenantId));
        }
        return result;
    }
}
