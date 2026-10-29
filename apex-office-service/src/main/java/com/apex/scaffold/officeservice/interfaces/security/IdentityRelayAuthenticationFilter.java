package com.apex.scaffold.officeservice.interfaces.security;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.officeservice.config.MessageWebSocketConfig;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 办公服务独立验签认证，用户与租户只取自签名声明，不信任中继身份头。
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

    /** RSA 签名算法标识，非此算法的公钥令牌一律拒绝。 */
    private static final String ALGORITHM_RS256 = "RS256";

    /** Bearer 认证方案前缀，长度用于截取真实令牌。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 令牌类型声明名。 */
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    /** 访问令牌类型取值，刷新令牌不得用于业务访问。 */
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";

    private final PublicKey publicKey;
    private final SecretKey secretKey;
    private final MessageSocketRegistry sockets;
    private final List<String> origins;

    /**
     * 加载固定验签密钥及浏览器来源白名单，缺省关闭凭据认证。
     * @param rsa RSA 公钥的 X509 DER Base64
     * @param secret HMAC 过渡密钥
     * @param sockets 票据存储
     * @param origins 明确允许的浏览器来源
     */
    public IdentityRelayAuthenticationFilter(
            @Value("${office.security.jwt-public-key-base64:}") String rsa,
            @Value("${office.security.jwt-secret:}") String secret,
            MessageSocketRegistry sockets,
            @Value("${office.messages.allowed-origins:}") List<String> origins) {
        try {
            publicKey = rsa.isBlank()
                    ? null
                    : KeyFactory.getInstance("RSA")
                            .generatePublic(
                                    new X509EncodedKeySpec(Base64.getDecoder().decode(rsa)));
            secretKey = secret.isBlank() ? null : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException error) {
            throw new IllegalArgumentException("办公服务 JWT 公钥无效", error);
        }
        this.sockets = sockets;
        this.origins = origins;
    }

    /**
     * 对握手仅消费同源限制下的一次性票据，对 HTTP 独立验证访问 JWT。
     * @param exchange 请求
     * @param chain 后续过滤链
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        AuthenticatedUser user;
        if (MessageWebSocketConfig.MESSAGE_SOCKET_PATH.equals(
                exchange.getRequest().getPath().value())) {
            String origin = exchange.getRequest().getHeaders().getOrigin();
            if (origin == null
                    || !origins.contains(origin)
                    || exchange.getRequest().getMethod() != org.springframework.http.HttpMethod.GET
                    || !"websocket"
                            .equalsIgnoreCase(exchange.getRequest().getHeaders().getUpgrade())) {
                exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.FORBIDDEN);
                return exchange.getResponse().setComplete();
            }
            user = sockets.consume(exchange.getRequest().getQueryParams().getFirst("ticket"));
        } else {
            user = verify(exchange.getRequest().getHeaders().getFirst("Authorization"));
        }
        if (user == null) {
            return chain.filter(exchange);
        }
        var authentication = new UsernamePasswordAuthenticationToken(user, null, List.of());
        return TenantContext.withTenant(chain.filter(exchange))
                .contextWrite(TenantContext.withTenant(user.tenantId()))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    /**
     * 验证算法、签名、有效期与访问令牌类型。
     * @param authorization Bearer 认证头
     * @return 合法用户或空
     */
    private AuthenticatedUser verify(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        try {
            String token = authorization.substring(BEARER_PREFIX.length());
            Claims claims;
            if (publicKey != null) {
                var jwt = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
                if (!ALGORITHM_RS256.equals(jwt.getHeader().getAlgorithm())) {
                    return null;
                }
                claims = jwt.getPayload();
            } else if (secretKey != null) {
                claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } else {
                return null;
            }
            if (claims.getExpiration() == null
                    || !TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return null;
            }
            Long id = Long.valueOf(claims.get("userId").toString());
            String tenant = claims.get("tenantId", String.class);
            return id > 0 && tenant != null && !tenant.isBlank() ? new AuthenticatedUser(id, null, tenant) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
