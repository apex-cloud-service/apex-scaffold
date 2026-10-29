package com.apex.scaffold.fileservice.interfaces.security;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.file.application.service.FileAccessContext;
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
 * 文件服务端认证过滤器，独立校验签名及过期时间后从受保护声明构造归属上下文。
 * 任意 X-User-Id、X-Tenant-Id 请求头不作为认证依据；未配置验签密钥时保持拒绝认证。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class IdentityRelayAuthenticationFilter implements WebFilter {
    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_TENANT_ID = "X-Tenant-Id";
    public static final String HEADER_CLIENT_TYPE = "X-Client-Type";
    private static final String ACCESS_TOKEN_TYPE = "ACCESS";
    private static final String TOKEN_TYPE_CLAIM = "tokenType";
    private static final String BEARER_PREFIX = "Bearer ";
    private final PublicKey publicKey;
    private final SecretKey secretKey;

    /**
     * 加载明确配置的验签密钥；RSA 与 HMAC 验证各自限制算法，防止密钥类型混淆。
     * @param publicKeyBase64 X509 DER 编码的 RSA 公钥
     * @param secret HMAC 过渡密钥，至少 256 位
     */
    public IdentityRelayAuthenticationFilter(
            @Value("${file.security.jwt-public-key-base64:}") String publicKeyBase64,
            @Value("${file.security.jwt-secret:}") String secret) {
        try {
            publicKey = publicKeyBase64.isBlank()
                    ? null
                    : KeyFactory.getInstance("RSA")
                            .generatePublic(
                                    new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
            secretKey = secret.isBlank() ? null : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalArgumentException("文件服务 JWT 公钥配置无效", error);
        }
    }

    /**
     * 验证 Bearer JWT，并以订阅上下文传递可信租户与用户。
     * @param exchange 当前请求
     * @param chain 后续过滤链
     * @return 请求完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        Claims claims = verify(authorization);
        if (claims == null) {
            return chain.filter(exchange);
        }
        Long userId;
        String tenant;
        try {
            if (!ACCESS_TOKEN_TYPE.equals(claims.get(TOKEN_TYPE_CLAIM, String.class))) {
                return chain.filter(exchange);
            }
            Object rawUser = claims.get("userId");
            userId = rawUser == null ? null : Long.valueOf(rawUser.toString());
            tenant = claims.get("tenantId", String.class);
        } catch (RuntimeException error) {
            return chain.filter(exchange);
        }
        if (userId == null || userId <= 0 || tenant == null || tenant.isBlank()) {
            return chain.filter(exchange);
        }
        AuthenticatedUser principal = new AuthenticatedUser(userId, null, tenant);
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        return TenantContext.withTenant(chain.filter(exchange))
                .contextWrite(TenantContext.withTenant(tenant))
                .contextWrite(context -> context.put(FileAccessContext.KEY, new FileAccessContext(tenant, userId)))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    /**
     * 使用已配置的密钥验证签名及有效期，拒绝缺失过期声明的凭证。
     *
     * @param authorization Bearer 请求头
     * @return 已验签且带有效过期声明的身份，失败返回空
     */
    private Claims verify(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        Claims claims = null;
        if (publicKey != null) {
            try {
                var signed = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
                if (Jwts.SIG.RS256.getId().equals(signed.getHeader().getAlgorithm())) {
                    claims = signed.getPayload();
                }
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
                /* 继续检查显式启用的过渡密钥。 */
            }
        }
        if (claims == null && secretKey != null) {
            try {
                claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
                return null;
            }
        }
        return claims != null && claims.getExpiration() != null ? claims : null;
    }
}
