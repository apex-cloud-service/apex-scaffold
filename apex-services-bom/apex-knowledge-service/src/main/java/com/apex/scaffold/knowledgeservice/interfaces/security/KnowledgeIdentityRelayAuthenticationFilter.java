package com.apex.scaffold.knowledgeservice.interfaces.security;

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
import java.util.Map;
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
 * 知识库服务独立验签认证，用户与租户只取自签名声明，不信任中继身份头。
 *
 * <p>统一认证收口（A1/A2）口径：公钥来源 = 静态配置
 * （{@code apex.knowledge.security.jwt-public-keys-base64} 多键，或旧版单键兜底）
 * ∪ 共享 JWKS 拉取快照（{@code apex.jwks.remote-url} 启用时，同 kid 以快照为准）；
 * 多键模式按 JWT header 的 kid 精确选择公钥，单键模式沿用「忽略 kid」语义；
 * HS 系列走显式配置的 HMAC 过渡密钥。刷新令牌不得用于业务访问。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class KnowledgeIdentityRelayAuthenticationFilter implements WebFilter {

    /** RSA 签名算法标识，非此算法的公钥令牌一律拒绝。 */
    private static final String ALGORITHM_RS256 = "RS256";

    /** Bearer 认证方案前缀。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 令牌类型声明名。 */
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    /** 访问令牌类型取值，刷新令牌不得用于业务访问。 */
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";

    /** 多公钥配置中条目的分隔符。 */
    private static final String KEY_ENTRY_SEPARATOR = ",";

    /** JWS 紧凑序列化的段数。 */
    private static final int JWT_SEGMENT_COUNT = 3;

    /** kid → RSA 公钥静态配置。 */
    private final Map<String, PublicKey> publicKeys;

    /** 是否为多键模式（按 header kid 精确匹配；单键模式忽略 kid）。 */
    private final boolean keyedPublicKeys;

    /** HMAC 过渡密钥；未配置时为 {@code null}。 */
    private final SecretKey secretKey;

    /** 共享 JWKS 拉取客户端；未装配时为 {@code null}。 */
    private final JwksFetcher jwksFetcher;

    /**
     * Spring 主构造器：加载验签密钥（多公钥优先，旧版单公钥兼容）。
     *
     * @param publicKeysBase64 多公钥配置，格式 {@code kid=Base64X509,kid=Base64X509}
     * @param publicKeyBase64 旧版单公钥的 X509 DER Base64，多公钥为空时生效
     * @param secret HMAC 过渡密钥
     * @param jwksFetcher 共享 JWKS 拉取客户端（可能未装配）
     */
    public KnowledgeIdentityRelayAuthenticationFilter(
            @Value("${apex.knowledge.security.jwt-public-keys-base64:}") String publicKeysBase64,
            @Value("${apex.knowledge.security.jwt-public-key-base64:}") String publicKeyBase64,
            @Value("${apex.knowledge.security.jwt-secret:}") String secret,
            org.springframework.beans.factory.ObjectProvider<JwksFetcher> jwksFetcher) {
        KeyConfiguration configured = buildKeys(publicKeysBase64, publicKeyBase64);
        this.publicKeys = configured.keys();
        this.keyedPublicKeys = configured.keyed();
        this.secretKey = secret.isBlank() ? null : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwksFetcher = jwksFetcher == null ? null : jwksFetcher.getIfAvailable();
    }

    /**
     * 对 HTTP 请求独立验证访问 JWT 并装配身份上下文。
     *
     * <p>验签失败或无令牌时不设置身份，由 Spring Security 过滤链拒绝；
     * 验签成功则写入本地 {@link AuthenticatedUser} 与租户上下文。
     *
     * @param exchange 请求
     * @param chain 后续过滤链
     * @return 完成信号
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        AuthenticatedUser user = verify(exchange.getRequest().getHeaders().getFirst("Authorization"));
        if (user == null) {
            return chain.filter(exchange);
        }
        var authentication = new UsernamePasswordAuthenticationToken(user, null, java.util.List.of());
        return TenantContext.withTenant(chain.filter(exchange))
                .contextWrite(TenantContext.withTenant(user.tenantId()))
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    /**
     * 验证算法、签名、有效期与访问令牌类型并提取身份。
     *
     * @param authorization Bearer 认证头
     * @return 合法用户或空
     */
    private AuthenticatedUser verify(String authorization) {
        Claims claims = verifyClaims(authorization);
        if (claims == null) {
            return null;
        }
        try {
            if (claims.getExpiration() == null
                    || !TOKEN_TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return null;
            }
            Long id = Long.valueOf(claims.get("userId").toString());
            String tenant = claims.get("tenantId", String.class);
            return id > 0 && tenant != null && !tenant.isBlank() ? new AuthenticatedUser(id, tenant) : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 只做签名与有效期校验，不限制令牌类型。
     *
     * @param authorization Bearer 认证头
     * @return 已验签声明；失败返回 null
     */
    private Claims verifyClaims(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        try {
            String token = authorization.substring(BEARER_PREFIX.length());
            String algorithm = headerValue(token, "alg");
            Claims claims = null;
            if (ALGORITHM_RS256.equals(algorithm)) {
                PublicKey verificationKey = resolveRs256Key(token);
                if (verificationKey != null) {
                    claims = Jwts.parser()
                            .verifyWith(verificationKey)
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
                }
            } else if (secretKey != null && isHmacAlgorithm(algorithm)) {
                claims = Jwts.parser()
                        .verifyWith(secretKey)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            }
            return claims != null && claims.getExpiration() != null ? claims : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 解析 RS256 验签公钥：静态配置打底，JWKS 拉取快照覆盖同 kid 条目。
     *
     * @param token 紧凑型 JWT
     * @return 验签公钥；无法定位时返回 {@code null}
     */
    private PublicKey resolveRs256Key(String token) {
        boolean fetcherActive = jwksFetcher != null && jwksFetcher.enabled();
        boolean kidLookup = keyedPublicKeys || (publicKeys.isEmpty() && fetcherActive);
        String keyId = kidLookup ? headerValue(token, "kid") : "";
        if (keyId == null) {
            return null;
        }
        Map<String, PublicKey> keys = publicKeys;
        if (fetcherActive) {
            keys = new HashMap<>(publicKeys);
            keys.putAll(jwksFetcher.snapshot());
        }
        return keys.get(keyId);
    }

    private boolean isHmacAlgorithm(String algorithm) {
        return Jwts.SIG.HS256.getId().equals(algorithm)
                || Jwts.SIG.HS384.getId().equals(algorithm)
                || Jwts.SIG.HS512.getId().equals(algorithm);
    }

    /**
     * 读取 JWT 未受保护头中的字段值（仅 Base64URL 解码与正则提取，不验签）。
     *
     * @param token 紧凑型 JWT
     * @param name 字段名（如 kid / alg）
     * @return 字段值；缺失或解析失败返回 {@code null}
     */
    private String headerValue(String token, String name) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != JWT_SEGMENT_COUNT) {
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

    /**
     * 解析多/单公钥配置。
     *
     * @param publicKeysBase64 多公钥配置（{@code kid=Base64} 逗号分隔）
     * @param publicKeyBase64 单公钥配置
     * @return 键控配置（映射 + 是否多键模式）
     */
    private static KeyConfiguration buildKeys(String publicKeysBase64, String publicKeyBase64) {
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (publicKeysBase64 != null && !publicKeysBase64.isBlank()) {
                String[] entries = publicKeysBase64.split(KEY_ENTRY_SEPARATOR, -1);
                Map<String, PublicKey> keys = HashMap.newHashMap(entries.length);
                for (String entry : entries) {
                    String[] parts = entry.split("=", 2);
                    String kid = parts.length == 2 ? parts[0].trim() : "";
                    String encoded = parts.length == 2 ? parts[1].trim() : "";
                    if (kid.isBlank() || encoded.isBlank() || keys.containsKey(kid)) {
                        throw new IllegalArgumentException("知识库服务 JWT 多公钥配置无效");
                    }
                    keys.put(
                            kid,
                            factory.generatePublic(
                                    new X509EncodedKeySpec(Base64.getDecoder().decode(encoded))));
                }
                return new KeyConfiguration(Map.copyOf(keys), true);
            }
            if (publicKeyBase64 != null && !publicKeyBase64.isBlank()) {
                PublicKey key = factory.generatePublic(
                        new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
                return new KeyConfiguration(Map.of("", key), false);
            }
        } catch (java.security.GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalArgumentException("知识库服务 JWT 公钥无效", error);
        }
        return new KeyConfiguration(Map.of(), false);
    }

    /** 公钥配置解析结果。 */
    private record KeyConfiguration(Map<String, PublicKey> keys, boolean keyed) {}
}
