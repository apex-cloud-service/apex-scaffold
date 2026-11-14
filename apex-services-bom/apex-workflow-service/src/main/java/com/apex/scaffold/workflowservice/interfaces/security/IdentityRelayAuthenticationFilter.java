package com.apex.scaffold.workflowservice.interfaces.security;

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
 * <p>统一认证收口（A1/A2）：新增多公钥 kid 验签与共享 JWKS 拉取合并——公钥来源 =
 * 静态配置（{@code workflow.security.jwt-public-keys-base64} 多键或旧版单键，兜底）
 * ∪ 共享 JWKS 拉取快照（{@code apex.jwks.remote-url} 启用时，同 kid 以快照为准）；
 * 多键模式按 JWT header 的 kid 精确选择公钥，单键模式沿用"忽略 kid"的既有语义；
 * HMAC 仅作为"未配置 RSA 且显式开启"时的历史过渡路径。
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

    /** kid → RSA 公钥静态配置 */
    private final Map<String, PublicKey> publicKeys;

    /** 是否为多键模式（按 header kid 精确匹配；单键模式忽略 kid） */
    private final boolean keyedPublicKeys;

    /** HMAC 过渡密钥；仅当未配置 RSA 且显式开启时可用 */
    private final SecretKey secretKey;

    /** 共享 JWKS 拉取客户端；未装配时为 {@code null} */
    private final JwksFetcher jwksFetcher;

    /**
     * 加载验签密钥（唯一 public 构造器，Spring 按唯一构造器注入，无需 {@code @Autowired}）：
     * 多公钥优先，旧版单公钥与 HMAC 开关保持兼容。
     *
     * @param publicKeysBase64 多公钥配置，格式 {@code kid=Base64X509,kid=Base64X509}
     * @param publicKeyBase64 旧版单公钥（X509 DER Base64），多公钥为空时生效
     * @param secret 至少 256 位的历史 HMAC 密钥
     * @param hmacEnabled 是否明确允许历史 HMAC 签发方（未配置 RSA 时生效）
     * @param jwksFetcher 共享 JWKS 拉取客户端（可能未装配）
     */
    public IdentityRelayAuthenticationFilter(
            @Value("${workflow.security.jwt-public-keys-base64:}") String publicKeysBase64,
            @Value("${workflow.security.jwt-public-key-base64:}") String publicKeyBase64,
            @Value("${workflow.security.jwt-secret:}") String secret,
            @Value("${workflow.security.jwt-hmac-enabled:false}") boolean hmacEnabled,
            org.springframework.beans.factory.ObjectProvider<JwksFetcher> jwksFetcher) {
        KeyConfiguration configured = buildKeys(publicKeysBase64, publicKeyBase64);
        this.publicKeys = configured.keys();
        this.keyedPublicKeys = configured.keyed();
        this.secretKey = configured.keys().isEmpty() && hmacEnabled && secret != null && !secret.isBlank()
                ? Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8))
                : null;
        this.jwksFetcher = jwksFetcher.getIfAvailable();
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
                        throw new IllegalArgumentException("工作流服务 JWT 多公钥配置无效");
                    }
                    keys.put(
                            kid,
                            factory.generatePublic(
                                    new X509EncodedKeySpec(Base64.getDecoder().decode(encoded))));
                }
                return new KeyConfiguration(keys, true);
            }
            if (publicKeyBase64 != null && !publicKeyBase64.isBlank()) {
                keys.put(
                        "",
                        factory.generatePublic(
                                new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64))));
            }
        } catch (java.security.GeneralSecurityException | IllegalArgumentException error) {
            throw new IllegalArgumentException("工作流服务 JWT 公钥配置无效", error);
        }
        return new KeyConfiguration(keys, false);
    }

    /** 公钥配置解析结果。 */
    private record KeyConfiguration(Map<String, PublicKey> keys, boolean keyed) {}

    /**
     * 校验签名及身份声明后桥接用户、租户上下文；失败由安全链返回 401。
     *
     * @param exchange 当前请求
     * @param chain 后续过滤链
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Claims claims = verify(exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        if (claims == null) {
            return chain.filter(exchange);
        }
        String path = exchange.getRequest().getPath().value();
        String capability = exchange.getRequest()
                .getHeaders()
                .getFirst(com.apex.scaffold.common.openapi.OpenApiTokenSupport.CAPABILITY_HEADER);
        if (com.apex.scaffold.common.openapi.OpenApiTokenSupport.isOpenApplication(path, capability, claims)) {
            String tenant = com.apex.scaffold.common.openapi.OpenApiTokenSupport.tenantId(claims);
            Long subject = com.apex.scaffold.common.openapi.OpenApiTokenSupport.subjectUserId(claims);
            if (tenant == null || tenant.isBlank()) {
                return chain.filter(exchange);
            }
            AuthenticatedUser application = new AuthenticatedUser(subject, null, tenant);
            var applicationAuthentication = new UsernamePasswordAuthenticationToken(application, null, List.of());
            return TenantContext.withTenant(chain.filter(exchange))
                    .contextWrite(TenantContext.withTenant(tenant))
                    .contextWrite(ReactiveSecurityContextHolder.withAuthentication(applicationAuthentication));
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
     * 验证 Bearer 签名及有效期：RS256 按 kid 选钥（多键模式）或忽略 kid（单键模式），
     * HS 系列仅接受显式配置的 HMAC 过渡密钥；拒绝缺失 exp 的凭证。
     *
     * @param authorization Bearer 请求头
     * @return 验签声明，失败返回空
     */
    private Claims verify(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = authorization.substring(BEARER_PREFIX.length()).trim();
        String algorithm = headerValue(token, "alg");
        Claims claims = null;
        if (Jwts.SIG.RS256.getId().equals(algorithm)) {
            PublicKey verificationKey = resolveRs256Key(token);
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
