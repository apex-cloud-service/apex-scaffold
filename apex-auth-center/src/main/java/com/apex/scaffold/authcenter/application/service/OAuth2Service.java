package com.apex.scaffold.authcenter.application.service;

import com.apex.scaffold.authcenter.config.OAuth2Properties;
import com.apex.scaffold.authcenter.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.authcenter.domain.repository.AuthConfigRepository;
import com.apex.scaffold.authcenter.domain.repository.UserRepository;
import com.apex.scaffold.authcenter.infrastructure.persistence.repository.OAuth2AuthorizationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
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

    public Mono<Boolean> validateClient(String id, String secret, String method, String tenant) {
        if (secret == null) {
            return fail(INVALID_CLIENT);
        }
        return client(id, secret, tenant)
                .flatMap(config -> allowed(config, CLIENT_AUTHENTICATION_METHODS, method)
                        ? Mono.just(true)
                        : fail(INVALID_CLIENT));
    }

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
        });
    }

    public Mono<Void> revoke(String token, String clientId, String tenant) {
        if (token == null || token.isBlank()) {
            return fail(INVALID_REQUEST);
        }
        return authorizationRepository
                .revokeByHash(tenant, hash(token), clientId)
                .then();
    }

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
                    Map<String, Object> response = tokens(subject, clientId, scope, tenant, true);
                    return authorizationRepository
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
                            .thenReturn(response);
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
                    Map<String, Object> response =
                            tokens((String) row.get("principal_name"), clientId, scope, tenant, true);
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
    }

    private Mono<Map<String, Object>> issue(
            JsonNode config, String clientId, String subject, String requestedScope, String grant, String tenant) {
        String scope = scopes(config, requestedScope);
        Map<String, Object> response = tokens(subject, clientId, scope, tenant, !"client_credentials".equals(grant));
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

    private Map<String, Object> tokens(String subject, String clientId, String scope, String tenant, boolean refresh) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", jwt(subject, clientId, scope, tenant, "access", ACCESS_SECONDS));
        result.put("token_type", "Bearer");
        result.put("expires_in", ACCESS_SECONDS);
        if (refresh) {
            result.put(REFRESH_TOKEN, jwt(subject, clientId, scope, tenant, REFRESH, REFRESH_SECONDS));
        }
        result.put("scope", scope);
        return result;
    }

    private String jwt(String subject, String clientId, String scope, String tenant, String type, long seconds) {
        Instant instant = Instant.now();
        return Jwts.builder()
                .setHeaderParam("kid", properties.getKeyId())
                .setId(UUID.randomUUID().toString())
                .setIssuer(properties.getIssuer())
                .setSubject(subject)
                .setAudience(clientId)
                .claim(CLIENT_ID, clientId)
                .claim("tenant_id", tenant)
                .claim("token_use", type)
                .claim("scope", scope)
                .setIssuedAt(java.util.Date.from(instant))
                .setExpiration(java.util.Date.from(instant.plusSeconds(seconds)))
                .signWith(metadataService.signingKey(), SignatureAlgorithm.RS256)
                .compact();
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

    @SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
    public static final class OAuthException extends RuntimeException {
        public OAuthException(String code) {
            super(code);
        }
    }
}
