package com.apex.scaffold.officeservice.interfaces.security;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.security.JwksFetcher;
import com.apex.scaffold.officeservice.config.MessageWebSocketConfig;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 办公服务独立验签认证，用户与租户只取自签名声明，不信任中继身份头。
 *
 * <p>统一认证收口（A1/A2）：新增多公钥 kid 验签与共享 JWKS 拉取合并——公钥来源 =
 * 静态配置（{@code office.security.jwt-public-keys-base64} 多键或旧版单键，兜底）
 * ∪ 共享 JWKS 拉取快照（{@code apex.jwks.remote-url} 启用时，同 kid 以快照为准）；
 * 多键模式按 JWT header 的 kid 精确选择公钥，单键模式沿用"忽略 kid"的既有语义。
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

    /** RSA 签名算法标识，非此算法的公钥令牌一律拒绝。 */
    private static final String ALGORITHM_RS256 = "RS256";

    /** Bearer 认证方案前缀，长度用于截取真实令牌。 */
    private static final String BEARER_PREFIX = "Bearer ";

    /** 令牌类型声明名。 */
    private static final String CLAIM_TOKEN_TYPE = "tokenType";

    /** 访问令牌类型取值，刷新令牌不得用于业务访问。 */
    private static final String TOKEN_TYPE_ACCESS = "ACCESS";

    /** kid → RSA 公钥静态配置 */
    private final Map<String, PublicKey> publicKeys;

    /** 是否为多键模式（按 header kid 精确匹配；单键模式忽略 kid） */
    private final boolean keyedPublicKeys;

    /** HMAC 过渡密钥；未配置时为 {@code null} */
    private final SecretKey secretKey;

    /** 共享 JWKS 拉取客户端；未装配时为 {@code null} */
    private final JwksFetcher jwksFetcher;

    private final MessageSocketRegistry sockets;
    private final List<String> origins;

    /**
     * 加载验签密钥及浏览器来源白名单（Spring 主构造器）：多公钥优先，旧版单公钥保持兼容。
     *
     * @param publicKeysBase64 多公钥配置，格式 {@code kid=Base64X509,kid=Base64X509}
     * @param publicKeyBase64 旧版单公钥的 X509 DER Base64，多公钥为空时生效
     * @param secret HMAC 过渡密钥
     * @param sockets 票据存储
     * @param origins 明确允许的浏览器来源
     * @param jwksFetcher 共享 JWKS 拉取客户端（可能未装配）
     */
    @Autowired
    public IdentityRelayAuthenticationFilter(
            @Value("${office.security.jwt-public-keys-base64:}") String publicKeysBase64,
            @Value("${office.security.jwt-public-key-base64:}") String publicKeyBase64,
            @Value("${office.security.jwt-secret:}") String secret,
            MessageSocketRegistry sockets,
            @Value("${office.messages.allowed-origins:}") List<String> origins,
            org.springframework.beans.factory.ObjectProvider<JwksFetcher> jwksFetcher) {
        KeyConfiguration configured = buildKeys(publicKeysBase64, publicKeyBase64);
        this.publicKeys = configured.keys();
        this.keyedPublicKeys = configured.keyed();
        this.secretKey = secret.isBlank() ? null : Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.sockets = sockets;
        this.origins = origins;
        this.jwksFetcher = jwksFetcher == null ? null : jwksFetcher.getIfAvailable();
    }

    /**
     * 兼容旧版四参构造（单公钥 + HMAC 密钥 + 票据 + 来源），供既有测试使用。
     *
     * @param rsa RSA 公钥的 X509 DER Base64
     * @param secret HMAC 过渡密钥
     * @param sockets 票据存储
     * @param origins 明确允许的浏览器来源
     */
    public IdentityRelayAuthenticationFilter(
            String rsa, String secret, MessageSocketRegistry sockets, List<String> origins) {
        this("", rsa, secret, sockets, origins, null);
    }

    private IdentityRelayAuthenticationFilter(
            Map<String, PublicKey> publicKeys,
            boolean keyedPublicKeys,
            SecretKey secretKey,
            MessageSocketRegistry sockets,
            List<String> origins,
            JwksFetcher jwksFetcher) {
        this.publicKeys = publicKeys;
        this.keyedPublicKeys = keyedPublicKeys;
        this.secretKey = secretKey;
        this.sockets = sockets;
        this.origins = origins;
        this.jwksFetcher = jwksFetcher;
    }

    /**
     * 解析多/单公钥配置。
     *
     * @param publicKeysBase64 多公钥配置（{@code kid=Base64} 逗号分隔）
     * @param publicKeyBase64 单公钥配置
     * @return 键控配置（映射 + 是否多键模式）
     */
    private static KeyConfiguration buildKeys(String publicKeysBase64, String publicKeyBase64) {
        Map<String, PublicKey> keys = new HashMap<>();
        try {
            KeyFactory factory = KeyFactory.getInstance("RSA");
            if (publicKeysBase64 != null && !publicKeysBase64.isBlank()) {
                for (String entry : publicKeysBase64.split(",", -1)) {
                    String[] parts = entry.split("=", 2);
                    String kid = parts.length == 2 ? parts[0].trim() : "";
                    String encoded = parts.length == 2 ? parts[1].trim() : "";
                    if (kid.isBlank() || encoded.isBlank() || keys.containsKey(kid)) {
                        throw new IllegalArgumentException("办公服务 JWT 多公钥配置无效");
                    }
                    keys.put(
                            kid,
                            factory.generatePublic(
                                    new X509EncodedKeySpec(Base64.getDecoder().decode(encoded))));
                }
                return new KeyConfiguration(Map.copyOf(keys), true);
            }
            if (publicKeyBase64 != null && !publicKeyBase64.isBlank()) {
                keys.put(
                        "",
                        factory.generatePublic(
                                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))));
            }
        } catch (java.security.GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalArgumentException("办公服务 JWT 公钥无效", error);
        }
        return new KeyConfiguration(Map.copyOf(keys), false);
    }

    /** 公钥配置解析结果。 */
    private record KeyConfiguration(Map<String, PublicKey> keys, boolean keyed) {}

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
     * 验证算法、签名、有效期与访问令牌类型：RS256 按 kid 选钥（多键模式）或忽略 kid（单键模式），
     * HS 系列走显式配置的 HMAC 过渡密钥。
     * @param authorization Bearer 认证头
     * @return 合法用户或空
     */
    private AuthenticatedUser verify(String authorization) {
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
            if (claims == null
                    || claims.getExpiration() == null
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

    /**
     * 解析 RS256 验签公钥：静态配置打底，JWKS 拉取快照覆盖同 kid 条目；
     * 多键（或仅配置拉取）模式按 header kid 精确匹配，单键模式忽略 kid。
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
