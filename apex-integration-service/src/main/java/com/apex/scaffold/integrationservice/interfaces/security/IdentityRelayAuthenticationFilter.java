package com.apex.scaffold.integrationservice.interfaces.security;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.security.JwksFetcher;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
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
 * 集成服务端认证过滤器，独立校验签名及过期时间后从受保护声明构造归属上下文。
 * 任意 X-User-Id、X-Tenant-Id 请求头不作为认证依据；未配置验签密钥时保持拒绝认证。
 *
 * <p>与文件服务同构（批次 E4 沿用中继过滤器模式）：验签通过后把可信租户写入
 * Reactor 上下文（{@link TenantContext}），集成域持久化的租户行级隔离据此生效。</p>
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
    private final Map<String, PublicKey> publicKeys;
    private final boolean keyedPublicKeys;
    private final SecretKey secretKey;

    /** 共享 JWKS 拉取客户端；未装配时为 {@code null}（统一认证收口 A2）。 */
    private final JwksFetcher jwksFetcher;

    /**
     * 加载明确配置的验签密钥；RSA 与 HMAC 验证各自限制算法，防止密钥类型混淆。
     * @param publicKeysBase64 多公钥配置，格式 {@code kid=Base64X509,kid=Base64X509}
     * @param publicKeyBase64 旧版单公钥（X509 DER Base64），多公钥为空时生效
     * @param secret HMAC 过渡密钥，至少 256 位
     * @param jwksFetcher 共享 JWKS 拉取客户端（可能未装配）
     */
    @Autowired
    public IdentityRelayAuthenticationFilter(
            @Value("${integration.security.jwt-public-keys-base64:}") String publicKeysBase64,
            @Value("${integration.security.jwt-public-key-base64:}") String publicKeyBase64,
            @Value("${integration.security.jwt-secret:}") String secret,
            org.springframework.beans.factory.ObjectProvider<JwksFetcher> jwksFetcher) {
        this(buildKeys(publicKeysBase64, publicKeyBase64), secret, jwksFetcher.getIfAvailable());
    }

    /**
     * 保留旧版程序化构造方式，供测试及程序化装配使用。
     * @param publicKeyConfiguration X509 DER 编码的 RSA 公钥，或 kid=Base64X509 配置
     * @param secret HMAC 过渡密钥
     */
    public IdentityRelayAuthenticationFilter(String publicKeyConfiguration, String secret) {
        this(buildLegacyKeys(publicKeyConfiguration), secret, null);
    }

    private IdentityRelayAuthenticationFilter(
            KeyConfiguration keyConfiguration, String secret, JwksFetcher jwksFetcher) {
        publicKeys = keyConfiguration.keys();
        keyedPublicKeys = keyConfiguration.keyed();
        secretKey = secret.isBlank() ? null : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwksFetcher = jwksFetcher;
    }

    private static KeyConfiguration buildKeys(String publicKeysBase64, String publicKeyBase64) {
        try {
            if (!publicKeysBase64.isBlank()) {
                return parseKeyedKeys(publicKeysBase64);
            }
            if (!publicKeyBase64.isBlank()) {
                return new KeyConfiguration(Map.of("", parsePublicKey(publicKeyBase64)), false);
            }
            return new KeyConfiguration(Map.of(), false);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalArgumentException("集成服务 JWT 公钥配置无效", error);
        }
    }

    private static KeyConfiguration buildLegacyKeys(String configuration) {
        if (configuration.isBlank()) {
            return new KeyConfiguration(Map.of(), false);
        }
        return configuration.contains("=") ? parseKeyedKeys(configuration) : buildKeys("", configuration);
    }

    private static KeyConfiguration parseKeyedKeys(String configuration) {
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            Map<String, PublicKey> keys = new HashMap<>();
            for (String entry : configuration.split(",", -1)) {
                String[] parts = entry.split("=", 2);
                String keyId = parts.length == 2 ? parts[0].trim() : "";
                String encodedKey = parts.length == 2 ? parts[1].trim() : "";
                if (keyId.isBlank() || encodedKey.isBlank() || keys.containsKey(keyId)) {
                    throw new IllegalArgumentException("集成服务 JWT 公钥配置无效");
                }
                keys.put(
                        keyId,
                        keyFactory.generatePublic(
                                new X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey))));
            }
            return new KeyConfiguration(Map.copyOf(keys), true);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalArgumentException("集成服务 JWT 公钥配置无效", error);
        }
    }

    private static PublicKey parsePublicKey(String encodedKey) throws java.security.GeneralSecurityException {
        return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey)));
    }

    private record KeyConfiguration(Map<String, PublicKey> keys, boolean keyed) {}

    /**
     * 验证 Bearer JWT，并以订阅上下文传递可信租户与用户。
     * @param exchange 当前请求
     * @param chain 后续过滤链
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
        String algorithm = headerValue(token, "alg");
        Claims claims = null;
        if (Jwts.SIG.RS256.getId().equals(algorithm)) {
            String keyId = keyedPublicKeys ? headerValue(token, "kid") : "";
            PublicKey verificationKey = keyId == null ? null : effectiveKeys().get(keyId);
            if (verificationKey != null) {
                try {
                    claims = Jwts.parser()
                            .verifyWith(verificationKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
                } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
                    /* 签名、结构或声明无效时拒绝认证。 */
                }
            }
        } else if (secretKey != null && isHmacAlgorithm(algorithm)) {
            try {
                claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (io.jsonwebtoken.JwtException | IllegalArgumentException ignored) {
                /* HMAC 仅作为显式配置的历史过渡路径。 */
            }
        }
        return claims != null && claims.getExpiration() != null ? claims : null;
    }

    /**
     * 合并当前生效公钥：静态配置打底，JWKS 拉取快照覆盖同 kid 条目
     * （{@code apex.jwks.remote-url} 启用时认证中心轮换公钥最迟一个刷新周期内生效）。
     *
     * @return 当前生效的公钥映射
     */
    private Map<String, PublicKey> effectiveKeys() {
        if (jwksFetcher == null
                || !jwksFetcher.enabled()
                || jwksFetcher.snapshot().isEmpty()) {
            return publicKeys;
        }
        Map<String, PublicKey> merged = new java.util.HashMap<>(publicKeys);
        merged.putAll(jwksFetcher.snapshot());
        return Map.copyOf(merged);
    }

    private boolean isHmacAlgorithm(String algorithm) {
        return Jwts.SIG.HS256.getId().equals(algorithm)
                || Jwts.SIG.HS384.getId().equals(algorithm)
                || Jwts.SIG.HS512.getId().equals(algorithm);
    }

    private String headerValue(String token, String name) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) {
                return null;
            }
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                            "\\\"" + name + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
                    .matcher(header);
            return matcher.find() ? matcher.group(1) : null;
        } catch (IllegalArgumentException error) {
            return null;
        }
    }
}
