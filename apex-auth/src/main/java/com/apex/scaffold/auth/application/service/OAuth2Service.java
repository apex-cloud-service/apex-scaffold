package com.apex.scaffold.auth.application.service;

import com.apex.scaffold.auth.config.OAuth2Properties;
import com.apex.scaffold.auth.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.auth.domain.repository.AuthConfigRepository;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import com.apex.scaffold.auth.domain.service.JwtKeyMaterialService;
import com.apex.scaffold.auth.infrastructure.persistence.repository.OAuth2AuthorizationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * OAuth2 客户端校验、令牌签发/撤销/内省服务。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since  1.3.0
 */
@Service
@RequiredArgsConstructor
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class OAuth2Service {
    private static final String SCOPE_SEPARATOR = " ";
    private static final String AUTHORIZATION_GRANT_TYPES = "authorizationGrantTypes";
    private static final String AUTHORIZATION_CODE = "authorization_code";
    private static final String CLIENT_AUTHENTICATION_METHODS = "clientAuthenticationMethods";
    private static final String CLIENT_ID = "client_id";
    private static final String CODE = "code";
    private static final String INVALID_CLIENT = "invalid_client";
    private static final String INVALID_REQUEST = "invalid_request";
    private static final String PASSWORD = "password";
    private static final String REDIRECT_URIS = "redirectUris";
    private static final String REDIRECT_URI = "redirect_uri";
    private static final String REFRESH = "refresh";
    private static final String REFRESH_TOKEN = "refresh_token";
    private static final String RESPONSE_TYPE = "response_type";
    private static final String UNAUTHORIZED_CLIENT = "unauthorized_client";
    private static final String UNSUPPORTED_RESPONSE_TYPE = "unsupported_response_type";
    private static final String USERNAME = "username";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long ACCESS_SECONDS = 3600;
    private static final long REFRESH_SECONDS = 2592000;
    private final AuthConfigRepository configRepository;
    private final UserRepository userRepository;
    private final OAuth2AuthorizationRepository authorizationRepository;
    private final OAuth2MetadataService metadataService;
    private final OAuth2Properties properties;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final JwtKeyMaterialService keyMaterialService;
    private final com.apex.scaffold.auth.infrastructure.persistence.repository.OAuth2ConsentRepository
            consentRepository;

    /** RFC 6749：用户尚未对客户端授权时的错误码。 */
    private static final String CONSENT_REQUIRED = "consent_required";

    /**
     * 校验OAuth2客户端凭证与认证方式
     *
     * @param id 客户端ID
     * @param secret 客户端密钥
     * @param method 客户端认证方式
     * @param tenant 租户ID
     * @return 客户端有效时为true，否则发出{@link OAuthException}
     */
    public Mono<Boolean> validateClient(String id, String secret, String method, String tenant) {
        if (secret == null) {
            return fail(INVALID_CLIENT);
        }
        return client(id, secret, tenant)
                .flatMap(config -> allowed(config, CLIENT_AUTHENTICATION_METHODS, method)
                        ? Mono.just(true)
                        : fail(INVALID_CLIENT));
    }

    /**
     * 令牌端点：按 {@code grant_type} 分派到客户端凭证、密码、授权码或刷新令牌流程。
     *
     * <p>先校验客户端身份（{@code secret} 为空直接返回 {@code invalid_client}），
     * 再校验该客户端允许的授权类型与认证方式，任一不通过即返回 OAuth2 错误响应。
     *
     * @param form 令牌请求表单，需包含 {@code grant_type}，按流程读取 {@code scope}、{@code code} 等字段
     * @param clientId 客户端标识
     * @param secret 客户端密钥，{@code null} 视为非法客户端
     * @param method 客户端认证方式（如 {@code client_secret_basic}）
     * @param tenant 租户标识
     * @return OAuth2 令牌响应体；校验失败时为对应的错误响应体
     */
    public Mono<Map<String, Object>> token(
            Map<String, String> form, String clientId, String secret, String method, String tenant) {
        if (secret == null) {
            return fail(INVALID_CLIENT);
        }
        return client(clientId, secret, tenant).flatMap(config -> {
            String grant = form.get("grant_type");
            if (!allowed(config, AUTHORIZATION_GRANT_TYPES, grant)) {
                return fail(UNAUTHORIZED_CLIENT);
            }
            if (!allowed(config, CLIENT_AUTHENTICATION_METHODS, method)) {
                return fail(INVALID_CLIENT);
            }
            return switch (grant) {
                case "client_credentials" -> issue(config, clientId, clientId, form.get("scope"), grant, tenant);
                case PASSWORD -> password(config, form, clientId, tenant);
                case AUTHORIZATION_CODE -> authorizationCode(config, form, clientId, tenant);
                case REFRESH_TOKEN -> refresh(config, form, clientId, tenant);
                default -> fail("unsupported_grant_type");
            };
        });
    }

    /**
     * 授权端点：校验客户端、重定向地址与 PKCE 参数后签发授权码。
     *
     * <p>仅支持 {@code response_type=code}，且强制 PKCE（{@code code_challenge_method=S256}，
     * 挑战值须为 43 位 Base64URL 字符）。{@code principal} 为已认证的用户标识，
     * 由上层认证流程传入，本方法不再校验其身份有效性。
     *
     * @param form 授权请求参数，需含 {@code redirect_uri}、{@code response_type}、{@code code_challenge} 等
     * @param clientId 客户端标识
     * @param principal 已认证用户标识
     * @param tenant 租户标识
     * @return 授权响应体；参数不合法时为对应的 OAuth2 错误响应体
     */
    public Mono<Map<String, Object>> authorize(
            Map<String, String> form, String clientId, String principal, String tenant) {
        return client(clientId, null, tenant).flatMap(config -> {
            String redirect = form.get(REDIRECT_URI);
            if (!CODE.equals(form.get(RESPONSE_TYPE))) {
                return fail(UNSUPPORTED_RESPONSE_TYPE);
            }
            if (!allowed(config, AUTHORIZATION_GRANT_TYPES, AUTHORIZATION_CODE)) {
                return fail(UNAUTHORIZED_CLIENT);
            }
            if (!allowed(config, REDIRECT_URIS, redirect)) {
                return fail(INVALID_REQUEST);
            }
            String challenge = form.get("code_challenge");
            if (challenge == null
                    || !challenge.matches("[A-Za-z0-9_-]{43}")
                    || !"S256".equals(form.get("code_challenge_method"))) {
                return fail(INVALID_REQUEST);
            }
            String scope = scopes(config, form.get("scope"));
            // 统一认证收口（A5）：用户同意门控——无有效同意记录，或请求 scope 超出已同意
            // 范围时返回 consent_required，由前端 consent 页（方案 §12）引导用户完成
            // POST /oauth2/consent 后重试授权。
            return consentRepository
                    .findActiveScope(tenant, principal, clientId)
                    .map(granted -> scopeSubset(scope, granted))
                    .defaultIfEmpty(false)
                    .flatMap(consented -> Boolean.TRUE.equals(consented)
                            ? issueAuthorizationCode(form, clientId, principal, tenant, scope, redirect, challenge)
                            : fail(CONSENT_REQUIRED));
        });
    }

    /**
     * 记录或撤销用户对客户端的授权同意。
     *
     * @param tenant    租户
     * @param principal 已认证主体
     * @param clientId  客户端 ID
     * @param scope     授权范围
     * @param approved  true=同意入库；false=撤销既有同意
     * @return 完成信号
     */
    public Mono<Void> recordConsent(String tenant, String principal, String clientId, String scope, boolean approved) {
        return approved
                ? consentRepository.save(tenant, principal, clientId, scope)
                : consentRepository.revoke(tenant, principal, clientId);
    }

    private Mono<Map<String, Object>> issueAuthorizationCode(
            Map<String, String> form,
            String clientId,
            String principal,
            String tenant,
            String scope,
            String redirect,
            String challenge) {
        String code = randomToken();
        return authorizationRepository
                .insert(
                        tenant,
                        UUID.randomUUID().toString(),
                        clientId,
                        principal,
                        AUTHORIZATION_CODE,
                        scope,
                        redirect,
                        challenge,
                        "S256",
                        hash(code),
                        now().plusSeconds(300),
                        null,
                        null,
                        null,
                        null)
                .thenReturn(Map.of(CODE, code, "state", form.getOrDefault("state", ""), REDIRECT_URI, redirect));
    }

    /**
     * 撤销指定令牌
     *
     * @param token 待撤销的令牌
     * @param clientId 客户端ID
     * @param tenant 租户ID
     */
    public Mono<Void> revoke(String token, String clientId, String tenant) {
        if (token == null || token.isBlank()) {
            return fail(INVALID_REQUEST);
        }
        return authorizationRepository
                .revokeByHash(tenant, hash(token), clientId)
                .then();
    }

    /**
     * 令牌内省端点（RFC 7662）：返回令牌的存活状态与元数据。
     *
     * <p>先按 access token 哈希查找，未命中再按 refresh token 哈希查找；令牌不属于
     * {@code clientId}、已撤销或已过期时返回 {@code active=false}，不泄露具体失效原因。
     *
     * @param token 待内省的令牌原文，空白时返回 {@code invalid_request}
     * @param clientId 调用方客户端标识，须与令牌归属一致
     * @param tenant 租户标识
     * @return 内省响应体，至少包含 {@code active} 字段
     */
    public Mono<Map<String, Object>> introspect(String token, String clientId, String tenant) {
        if (token == null || token.isBlank()) {
            return fail(INVALID_REQUEST);
        }
        return authorizationRepository
                .findByHash(tenant, "access_token_hash", hash(token))
                .switchIfEmpty(authorizationRepository.findByHash(tenant, "refresh_token_hash", hash(token)))
                .map(row -> {
                    boolean access = hash(token).equals(row.get("access_token_hash"));
                    LocalDateTime expiry =
                            timestamp(row.get(access ? "access_token_expires_at" : "refresh_token_expires_at"));
                    if (!clientId.equals(row.get(CLIENT_ID))
                            || revoked(row)
                            || expiry == null
                            || !expiry.isAfter(now())) {
                        return Map.<String, Object>of("active", false);
                    }
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("active", true);
                    result.put(CLIENT_ID, row.get(CLIENT_ID));
                    result.put("sub", row.get("principal_name"));
                    result.put("scope", row.get("authorized_scopes"));
                    result.put("token_type", "Bearer");
                    result.put("exp", expiry.toEpochSecond(ZoneOffset.UTC));
                    return result;
                })
                .defaultIfEmpty(Map.of("active", false));
    }

    private Mono<Map<String, Object>> password(
            JsonNode config, Map<String, String> form, String clientId, String tenant) {
        if (form.get(USERNAME) == null || form.get(PASSWORD) == null) {
            return fail(INVALID_REQUEST);
        }
        return userRepository
                .findByUsername(form.get(USERNAME), tenant)
                .filter(user -> user.isActive()
                        && !user.isLocked()
                        && passwordEncoder.matches(form.get(PASSWORD), user.getPasswordHash()))
                .switchIfEmpty(fail("invalid_grant"))
                .flatMap(user -> issue(config, clientId, user.getId().toString(), form.get("scope"), PASSWORD, tenant));
    }

    private Mono<Map<String, Object>> authorizationCode(
            JsonNode config, Map<String, String> form, String clientId, String tenant) {
        if (form.get(CODE) == null || form.get(REDIRECT_URI) == null) {
            return fail(INVALID_REQUEST);
        }
        return authorizationRepository
                .findByHash(tenant, "authorization_code_hash", hash(form.get(CODE)))
                .filter(row -> !revoked(row)
                        && clientId.equals(row.get(CLIENT_ID))
                        && form.get(REDIRECT_URI).equals(row.get(REDIRECT_URI))
                        && verifyPkce(row, form.get("code_verifier")))
                .switchIfEmpty(fail("invalid_grant"))
                .flatMap(row -> {
                    String subject = (String) row.get("principal_name");
                    String scope = (String) row.get("authorized_scopes");
                    return tokens(subject, clientId, scope, tenant, true, AUTHORIZATION_CODE)
                            .flatMap(response -> authorizationRepository
                                    .exchangeCode(
                                            tenant,
                                            hash(form.get(CODE)),
                                            clientId,
                                            form.get(REDIRECT_URI),
                                            hash(response.get("access_token").toString()),
                                            now().plusSeconds(ACCESS_SECONDS),
                                            hash(response.get(REFRESH_TOKEN).toString()),
                                            now().plusSeconds(REFRESH_SECONDS))
                                    .filter(Boolean::booleanValue)
                                    .switchIfEmpty(fail("invalid_grant"))
                                    .thenReturn(response));
                });
    }

    private boolean verifyPkce(Map<String, Object> row, String verifier) {
        if (verifier == null
                || !verifier.matches("[A-Za-z0-9._~-]{43,128}")
                || !"S256".equals(row.get("code_challenge_method"))) {
            return false;
        }
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest(verifier));
        return MessageDigest.isEqual(
                challenge.getBytes(StandardCharsets.US_ASCII),
                String.valueOf(row.get("code_challenge")).getBytes(StandardCharsets.US_ASCII));
    }

    private Mono<Map<String, Object>> refresh(
            JsonNode config, Map<String, String> form, String clientId, String tenant) {
        if (form.get(REFRESH_TOKEN) == null) {
            return fail(INVALID_REQUEST);
        }
        return authorizationRepository
                .findByHash(tenant, "refresh_token_hash", hash(form.get(REFRESH_TOKEN)))
                .filter(row -> clientId.equals(row.get(CLIENT_ID)))
                .switchIfEmpty(fail("invalid_grant"))
                .flatMap(row -> {
                    String originalScope = (String) row.get("authorized_scopes");
                    String scope = scopes(config, form.getOrDefault("scope", originalScope));
                    if (!Arrays.asList(originalScope.split(SCOPE_SEPARATOR))
                            .containsAll(Arrays.asList(scope.split(SCOPE_SEPARATOR)))) {
                        return fail("invalid_scope");
                    }
                    return tokens((String) row.get("principal_name"), clientId, scope, tenant, true, (String)
                                    row.get("authorization_grant_type"))
                            .flatMap(response -> {
                                String access = response.get("access_token").toString();
                                String refresh = response.get(REFRESH_TOKEN).toString();
                                return authorizationRepository
                                        .rotateRefresh(
                                                tenant,
                                                clientId,
                                                hash(form.get(REFRESH_TOKEN)),
                                                hash(refresh),
                                                now().plusSeconds(REFRESH_SECONDS),
                                                hash(access),
                                                now().plusSeconds(ACCESS_SECONDS),
                                                scope)
                                        .filter(Boolean::booleanValue)
                                        .switchIfEmpty(fail("invalid_grant"))
                                        .thenReturn(response);
                            });
                });
    }

    private Mono<Map<String, Object>> issue(
            JsonNode config, String clientId, String subject, String requestedScope, String grant, String tenant) {
        String scope = scopes(config, requestedScope);
        boolean withRefresh = !"client_credentials".equals(grant);
        return tokens(subject, clientId, scope, tenant, withRefresh, grant).flatMap(response -> {
            String access = response.get("access_token").toString();
            String refresh = (String) response.get(REFRESH_TOKEN);
            return authorizationRepository
                    .insert(
                            tenant,
                            UUID.randomUUID().toString(),
                            clientId,
                            subject,
                            grant,
                            scope,
                            null,
                            null,
                            null,
                            null,
                            null,
                            hash(access),
                            now().plusSeconds(ACCESS_SECONDS),
                            refresh == null ? null : hash(refresh),
                            refresh == null ? null : now().plusSeconds(REFRESH_SECONDS))
                    .thenReturn(response);
        });
    }

    private Mono<JsonNode> client(String id, String secret, String tenant) {
        return configRepository
                .findEnabledByType("OAUTH2_CLIENT", tenant)
                .filter(config -> config.getConfigKey().equals(id))
                .next()
                .switchIfEmpty(fail(INVALID_CLIENT))
                .map(config -> parse(config, secret));
    }

    private JsonNode parse(AuthConfigEntity entity, String secret) {
        try {
            JsonNode node = objectMapper.readTree(entity.getConfigValue());
            if (secret != null && !validSecret(node, secret)) {
                throw new OAuthException(INVALID_CLIENT);
            }
            return node;
        } catch (Exception exception) {
            throw new OAuthException(INVALID_CLIENT);
        }
    }

    private boolean validSecret(JsonNode node, String secret) {
        return node.has("clientSecret")
                && passwordEncoder.matches(secret, node.get("clientSecret").asText());
    }

    private String scopes(JsonNode config, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = String.join(
                SCOPE_SEPARATOR,
                Arrays.stream(value.trim().split("\\s+")).distinct().toList());
        for (String scope : normalized.split(SCOPE_SEPARATOR)) {
            if (!allowed(config, "scopes", scope)) {
                throw new OAuthException("invalid_scope");
            }
        }
        return normalized;
    }

    /**
     * 判断请求 scope 是否为已同意 scope 的子集。
     *
     * <p>已同意 scope 为空串时表示同意记录未限定范围，视为全量同意；
     * 请求 scope 为空串时无需扩权，恒为子集。
     *
     * @param requested 本次请求的 scope（空格分隔）
     * @param granted   已同意记录的 scope（空格分隔；空串表示未限定）
     * @return 请求 scope 均在已同意范围内返回 true
     */
    private boolean scopeSubset(String requested, String granted) {
        if (requested == null || requested.isBlank()) {
            return true;
        }
        if (granted == null || granted.isBlank()) {
            return true;
        }
        java.util.Set<String> grantedSet =
                new java.util.HashSet<>(Arrays.asList(granted.trim().split("\\s+")));
        return Arrays.stream(requested.trim().split("\\s+")).allMatch(grantedSet::contains);
    }

    private boolean allowed(JsonNode config, String field, String value) {
        if (value == null || !config.path(field).isArray()) {
            return false;
        }
        for (JsonNode item : config.path(field)) {
            if (value.equals(item.asText())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 组装令牌响应体：access（必发）与 refresh（按需）经统一密钥服务串行签发。
     *
     * <p>统一认证收口（A2）：签名密钥由 {@link JwtKeyMaterialService} 提供（Redis 轮换
     * RSA，header 携带 {@code kid}），与登录令牌共用同一密钥家族；配置注入的
     * {@code auth-center.oauth2.*} 静态密钥仅在过渡期用于回退验签。
     *
     * @param subject 令牌主体
     * @param clientId 客户端标识
     * @param scope 授权范围
     * @param tenant 租户标识
     * @param refresh 是否同时签发刷新令牌
     * @param grant 授权类型（写入令牌 {@code grant_type} claim，供网关区分开放接口令牌来源）
     * @return OAuth2 令牌响应体
     */
    private Mono<Map<String, Object>> tokens(
            String subject, String clientId, String scope, String tenant, boolean refresh, String grant) {
        Mono<String> access = jwt(subject, clientId, scope, tenant, "access", ACCESS_SECONDS, grant);
        Mono<String> refreshToken =
                refresh ? jwt(subject, clientId, scope, tenant, REFRESH, REFRESH_SECONDS, grant) : Mono.just("");
        return Mono.zip(access, refreshToken).map(tuple -> {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("access_token", tuple.getT1());
            result.put("token_type", "Bearer");
            result.put("expires_in", ACCESS_SECONDS);
            if (refresh) {
                result.put(REFRESH_TOKEN, tuple.getT2());
            }
            result.put("scope", scope);
            return result;
        });
    }

    /**
     * 签发 OAuth2 令牌：claims 携带 {@code iss}/{@code aud}/{@code jti} 与业务声明，
     * {@code kid} 头与标准时间声明由统一密钥服务写入。
     *
     * @param subject 令牌主体
     * @param clientId 客户端标识
     * @param scope 授权范围
     * @param tenant 租户标识
     * @param type 令牌用途（access / refresh）
     * @param seconds 有效期（秒）
     * @param grant 授权类型；历史数据可能为空，为空时不写入该 claim（向后兼容）
     * @return 紧凑型 JWT
     */
    private Mono<String> jwt(
            String subject, String clientId, String scope, String tenant, String type, long seconds, String grant) {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("iss", properties.getIssuer());
        claims.put("aud", clientId);
        claims.put("jti", UUID.randomUUID().toString());
        claims.put(CLIENT_ID, clientId);
        claims.put("tenant_id", tenant);
        claims.put("token_use", type);
        claims.put("scope", scope);
        if (grant != null && !grant.isBlank()) {
            claims.put("grant_type", grant);
        }
        return keyMaterialService.sign(subject, claims, seconds * 1000);
    }

    private static boolean revoked(Map<String, Object> row) {
        return Boolean.TRUE.equals(row.get("revoked"))
                || row.get("revoked") instanceof Number number && number.intValue() != 0;
    }

    private static LocalDateTime timestamp(Object value) {
        if (value instanceof LocalDateTime date) {
            return date;
        }
        if (value instanceof Timestamp date) {
            return date.toLocalDateTime();
        }
        return null;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String hash(String token) {
        return HexFormat.of().formatHex(digest(token));
    }

    private static <T> Mono<T> fail(String code) {
        return Mono.error(new OAuthException(code));
    }

    /**
     * 携带 OAuth2 协议错误码的异常，错误码直接作为响应体 {@code error} 字段。
     */
    @SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
    public static final class OAuthException extends RuntimeException {
        /**
         * 构造携带OAuth2错误码的异常。
         *
         * @param code OAuth2错误码
         */
        public OAuthException(String code) {
            super(code);
        }
    }
}
