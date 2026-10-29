package com.apex.scaffold.workflowservice.interfaces.security;

import com.apex.scaffold.common.context.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 工作流服务独立验签过滤器；身份只取自签名 ACCESS JWT，不信任中继身份头。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.0
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdentityRelayAuthenticationFilter implements WebFilter {
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_CLIENT_TYPE = "X-Client-Type";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private final PublicKey publicKey;
    private final SecretKey secretKey;

    /**
     * 加载验签密钥，配置 RSA 时不启用 HMAC，避免验签失败后降级。
     *
     * @param publicKeyBase64 X509 DER 编码的 RSA 公钥
     * @param secret 至少 256 位的历史 HMAC 密钥
     * @param hmacEnabled 是否明确允许历史 HMAC 签发方
     */
    public IdentityRelayAuthenticationFilter(
            @Value("${workflow.security.jwt-public-key-base64:}") String publicKeyBase64,
            @Value("${workflow.security.jwt-secret:}") String secret,
            @Value("${workflow.security.jwt-hmac-enabled:false}") boolean hmacEnabled) {
        try {
            publicKey = publicKeyBase64.isBlank()
                    ? null
                    : KeyFactory.getInstance("RSA")
                            .generatePublic(
                                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
            secretKey = publicKey == null && hmacEnabled && !secret.isBlank()
                    ? Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))
                    : null;
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalArgumentException("工作流服务 JWT 公钥配置无效", error);
        }
    }

    /**
     * 校验签名及身份声明后桥接用户、租户上下文；失败由安全链返回 401。
     *
     * @param exchange 当前请求
     * @param chain 后续过滤链
     * @return 请求完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Claims claims = verify(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (claims == null) {
            return chain.filter(exchange);
        }
        Long userId;
        String tenant;
        String clientType;
        try {
            if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                return chain.filter(exchange);
            }
            Object rawUser = claims.get("userId");
            userId = rawUser == null ? null : Long.valueOf(rawUser.toString());
            tenant = claims.get("tenantId", String.class);
            clientType = claims.get("clientType", String.class);
        } catch (RuntimeException error) {
            return chain.filter(exchange);
        }
        if (userId == null || userId <= 0 || tenant == null || tenant.isBlank()) {
            return chain.filter(exchange);
        }
        AuthenticatedUser principal = new AuthenticatedUser(userId, null, tenant);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        authentication.setDetails(clientType);
        return TenantContext.withTenant(chain.filter(exchange))
                .contextWrite(TenantContext.withTenant(tenant))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    /**
     * 验证 Bearer 签名及有效期，RSA 仅接受 RS256，拒绝缺失 exp 的凭证。
     *
     * @param authorization Bearer 请求头
     * @return 验签声明，失败返回空
     */
    private Claims verify(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        try {
            Claims claims;
            if (publicKey != null) {
                var signed = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
                if (!Jwts.SIG.RS256.getId().equals(signed.getHeader().getAlgorithm())) {
                    return null;
                }
                claims = signed.getPayload();
            } else if (secretKey != null) {
                claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } else {
                return null;
            }
            return claims.getExpiration() == null ? null : claims;
        } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
            return null;
        }
    }
}
