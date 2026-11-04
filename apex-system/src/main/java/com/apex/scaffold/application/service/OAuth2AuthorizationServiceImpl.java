package com.apex.scaffold.application.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.JwtUtil;
import com.apex.scaffold.domain.model.entity.OAuth2AccessTokenEntity;
import com.apex.scaffold.domain.model.entity.OAuth2AuthorizationCodeEntity;
import com.apex.scaffold.domain.model.entity.OAuth2AuthorizationConsentEntity;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.repository.OAuth2AuthorizationConsentRepository;
import com.apex.scaffold.domain.repository.OAuth2RegisteredClientRepository;
import com.apex.scaffold.domain.repository.UserRepository;
import com.apex.scaffold.domain.service.OAuth2AuthorizationService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * OAuth2授权服务实现
 *
 * <p>实现OAuth2标准协议的核心授权逻辑，支持以下授权模式：
 * <ul>
 *   <li>授权码模式(Authorization Code + PKCE)</li>
 *   <li>客户端凭证模式(Client Credentials)</li>
 *   <li>密码模式(Resource Owner Password)</li>
 *   <li>刷新令牌(Refresh Token)</li>
 * </ul>
 *
 * <p>数据存储:
 * <ul>
 *   <li>授权码: Redis缓存, 5分钟过期</li>
 *   <li>访问令牌: Redis缓存, 2小时过期</li>
 *   <li>刷新令牌: Redis缓存, 7天过期</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2AuthorizationServiceImpl implements OAuth2AuthorizationService {

    private static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";
    private static final String GRANT_TYPE_CLIENT_CREDENTIALS = "client_credentials";
    private static final String GRANT_TYPE_PASSWORD = "password";
    private static final String PKCE_METHOD_S256 = "S256";
    private static final String PKCE_METHOD_PLAIN = "plain";
    private static final String TOKEN_TYPE_BEARER = "Bearer";
    private static final String TOKEN_TYPE_OAUTH2 = "oauth2";
    private static final int CODE_VERIFIER_MIN_LENGTH = 43;
    private static final int CODE_VERIFIER_MAX_LENGTH = 128;
    private static final int DEFAULT_MAP_CAPACITY = 16;

    private final OAuth2RegisteredClientRepository clientRepository;
    private final OAuth2AuthorizationConsentRepository consentRepository;
    private final UserRepository userRepository;
    private final RedissonClient redissonClient;
    private final PasswordEncoder passwordEncoder;

    private static final String REDIS_KEY_PREFIX_AUTH_CODE = "oauth2:code:";
    private static final String REDIS_KEY_PREFIX_ACCESS_TOKEN = "oauth2:access:";
    private static final String REDIS_KEY_PREFIX_REFRESH_TOKEN = "oauth2:refresh:";

    private static final Duration AUTH_CODE_TTL = Duration.ofMinutes(5);
    private static final Duration ACCESS_TOKEN_TTL = Duration.ofHours(2);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    @Override
    public Mono<OAuth2AuthorizationCodeEntity> generateAuthorizationCode(
            String clientId,
            String userId,
            String redirectUri,
            String scope,
            String state,
            String codeChallenge,
            String codeChallengeMethod) {

        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "无效的客户端ID")))
                .flatMap(client -> {
                    if (client.isDisabled()) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "客户端已停用"));
                    }
                    if (!client.supportsGrantType(GRANT_TYPE_AUTHORIZATION_CODE)) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "客户端不支持授权码模式"));
                    }

                    if (!client.getRedirectUris().contains(redirectUri)) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "无效的重定向URI"));
                    }

                    if (codeChallenge != null && codeChallengeMethod != null) {
                        if (!PKCE_METHOD_S256.equals(codeChallengeMethod)
                                && !PKCE_METHOD_PLAIN.equals(codeChallengeMethod)) {
                            return Mono.error(
                                    new BusinessException(ResultCode.PARAM_INVALID, "不支持的 code_challenge_method"));
                        }
                    }

                    String code = generateRandomCode();
                    OAuth2AuthorizationCodeEntity entity = new OAuth2AuthorizationCodeEntity();
                    entity.setCode(code);
                    entity.setClientId(clientId);
                    entity.setUserId(userId);
                    entity.setRedirectUri(redirectUri);
                    entity.setScope(scope);
                    entity.setState(state);
                    entity.setCodeChallenge(codeChallenge);
                    entity.setCodeChallengeMethod(codeChallengeMethod);
                    entity.setIssuedAt(Instant.now());
                    entity.setExpiresAt(Instant.now().plus(AUTH_CODE_TTL));

                    return storeAuthorizationCode(entity).thenReturn(entity);
                });
    }

    @Override
    public Mono<OAuth2AccessTokenEntity> exchangeCodeForToken(
            String code, String clientId, String clientSecret, String redirectUri, String codeVerifier) {

        return validateClient(clientId, clientSecret)
                .flatMap(client -> retrieveAndRemoveAuthorizationCode(code))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "无效或已过期的授权码")))
                .flatMap(authCode -> {
                    if (!authCode.getClientId().equals(clientId)) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "授权码与客户端不匹配"));
                    }

                    if (!authCode.getRedirectUri().equals(redirectUri)) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "重定向URI不匹配"));
                    }

                    if (authCode.isExpired()) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "授权码已过期"));
                    }

                    if (authCode.getCodeChallenge() != null) {
                        if (!verifyPKCE(codeVerifier, authCode.getCodeChallenge(), authCode.getCodeChallengeMethod())) {
                            return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "PKCE验证失败"));
                        }
                    }

                    return generateAccessToken(clientId, authCode.getUserId(), authCode.getScope());
                });
    }

    @Override
    public Mono<OAuth2AccessTokenEntity> clientCredentialsGrant(String clientId, String clientSecret, String scope) {

        return validateClient(clientId, clientSecret).flatMap(client -> {
            if (!client.supportsGrantType(GRANT_TYPE_CLIENT_CREDENTIALS)) {
                return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "客户端不支持客户端凭证模式"));
            }

            return generateAccessToken(clientId, null, scope);
        });
    }

    @Override
    public Mono<OAuth2AccessTokenEntity> passwordGrant(
            String clientId, String clientSecret, String username, String password, String scope) {

        return validateClient(clientId, clientSecret).flatMap(client -> {
            if (!client.supportsGrantType(GRANT_TYPE_PASSWORD)) {
                return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "客户端不支持密码模式"));
            }

            return userRepository
                    .findByUsername(username)
                    .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误")))
                    .flatMap(user -> {
                        if (!passwordEncoder.matches(password, user.getPassword())) {
                            return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误"));
                        }

                        if (!user.isEnabled()) {
                            return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "用户已被禁用"));
                        }

                        return generateAccessToken(clientId, user.getOpenId(), scope);
                    });
        });
    }

    @Override
    public Mono<OAuth2AccessTokenEntity> refreshToken(String refreshToken, String clientId, String clientSecret) {

        return validateClient(clientId, clientSecret)
                .flatMap(client -> retrieveRefreshToken(refreshToken))
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "无效的刷新令牌")))
                .flatMap(oldToken -> {
                    if (!oldToken.getClientId().equals(clientId)) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌与客户端不匹配"));
                    }

                    if (oldToken.isExpired()) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "刷新令牌已过期"));
                    }

                    return removeRefreshToken(refreshToken)
                            .then(generateAccessToken(clientId, oldToken.getUserId(), oldToken.getScope()));
                });
    }

    @Override
    public Mono<Void> revokeToken(String token, String clientId) {
        return removeAccessToken(token)
                .then(removeRefreshToken(token))
                .doOnSuccess(v -> log.info("撤销令牌成功: clientId={}", clientId));
    }

    @Override
    public Mono<OAuth2AccessTokenEntity> validateToken(String token) {
        return retrieveAccessToken(token).filter(tokenEntity -> !tokenEntity.isExpired());
    }

    private Mono<OAuth2RegisteredClientEntity> validateClient(String clientId, String clientSecret) {
        return clientRepository
                .findByClientId(clientId)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "无效的客户端ID")))
                .flatMap(client -> {
                    if (client.isDisabled()) {
                        return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "客户端已停用"));
                    }
                    if (client.getClientSecret() != null
                            && !client.getClientSecret().isEmpty()) {
                        if (clientSecret == null || !passwordEncoder.matches(clientSecret, client.getClientSecret())) {
                            return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "客户端密钥错误"));
                        }
                    }
                    return Mono.just(client);
                });
    }

    private Mono<OAuth2AccessTokenEntity> generateAccessToken(String clientId, String userId, String scope) {
        String accessToken = generateJwtToken(clientId, userId, scope, ACCESS_TOKEN_TTL);
        String refreshToken = generateJwtToken(clientId, userId, scope, REFRESH_TOKEN_TTL);

        OAuth2AccessTokenEntity entity = new OAuth2AccessTokenEntity();
        entity.setTokenValue(accessToken);
        entity.setTokenType(TOKEN_TYPE_BEARER);
        entity.setClientId(clientId);
        entity.setUserId(userId);
        entity.setScope(scope);
        entity.setIssuedAt(Instant.now());
        entity.setExpiresAt(Instant.now().plus(ACCESS_TOKEN_TTL));
        entity.setRefreshToken(refreshToken);

        return storeAccessToken(entity).then(storeRefreshToken(entity)).thenReturn(entity);
    }

    private String generateJwtToken(String clientId, String userId, String scope, Duration ttl) {
        Map<String, Object> claims = new HashMap<>(DEFAULT_MAP_CAPACITY);
        claims.put("client_id", clientId);
        if (userId != null) {
            claims.put("user_id", userId);
        }
        if (scope != null) {
            claims.put("scope", scope);
        }
        claims.put("type", TOKEN_TYPE_OAUTH2);

        String subject = userId != null ? userId : clientId;
        return JwtUtil.generateToken(subject, claims, ttl.toMillis());
    }

    private Mono<Void> storeAuthorizationCode(OAuth2AuthorizationCodeEntity entity) {
        return Mono.fromRunnable(() -> {
            RMapCache<String, OAuth2AuthorizationCodeEntity> map =
                    redissonClient.getMapCache(REDIS_KEY_PREFIX_AUTH_CODE);
            map.put(entity.getCode(), entity, AUTH_CODE_TTL.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        });
    }

    private Mono<OAuth2AuthorizationCodeEntity> retrieveAndRemoveAuthorizationCode(String code) {
        return Mono.fromCallable(() -> {
            RMapCache<String, OAuth2AuthorizationCodeEntity> map =
                    redissonClient.getMapCache(REDIS_KEY_PREFIX_AUTH_CODE);
            return map.remove(code);
        });
    }

    private Mono<Void> storeAccessToken(OAuth2AccessTokenEntity entity) {
        return Mono.fromRunnable(() -> {
            RMapCache<String, OAuth2AccessTokenEntity> map = redissonClient.getMapCache(REDIS_KEY_PREFIX_ACCESS_TOKEN);
            map.put(
                    entity.getTokenValue(),
                    entity,
                    ACCESS_TOKEN_TTL.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        });
    }

    private Mono<OAuth2AccessTokenEntity> retrieveAccessToken(String token) {
        return Mono.fromCallable(() -> {
            RMapCache<String, OAuth2AccessTokenEntity> map = redissonClient.getMapCache(REDIS_KEY_PREFIX_ACCESS_TOKEN);
            return map.get(token);
        });
    }

    private Mono<Void> removeAccessToken(String token) {
        return Mono.fromRunnable(() -> {
            RMapCache<String, OAuth2AccessTokenEntity> map = redissonClient.getMapCache(REDIS_KEY_PREFIX_ACCESS_TOKEN);
            map.remove(token);
        });
    }

    private Mono<Void> storeRefreshToken(OAuth2AccessTokenEntity entity) {
        return Mono.fromRunnable(() -> {
            RMapCache<String, OAuth2AccessTokenEntity> map = redissonClient.getMapCache(REDIS_KEY_PREFIX_REFRESH_TOKEN);
            map.put(
                    entity.getRefreshToken(),
                    entity,
                    REFRESH_TOKEN_TTL.toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
        });
    }

    private Mono<OAuth2AccessTokenEntity> retrieveRefreshToken(String refreshToken) {
        return Mono.fromCallable(() -> {
            RMapCache<String, OAuth2AccessTokenEntity> map = redissonClient.getMapCache(REDIS_KEY_PREFIX_REFRESH_TOKEN);
            return map.get(refreshToken);
        });
    }

    private Mono<Void> removeRefreshToken(String refreshToken) {
        return Mono.fromRunnable(() -> {
            RMapCache<String, OAuth2AccessTokenEntity> map = redissonClient.getMapCache(REDIS_KEY_PREFIX_REFRESH_TOKEN);
            map.remove(refreshToken);
        });
    }

    private String generateRandomCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    @SuppressWarnings("PMD.LowerCamelCaseVariableNamingRule")
    private boolean verifyPKCE(String codeVerifier, String codeChallenge, String method) {
        if (codeVerifier == null || codeChallenge == null) {
            return false;
        }

        if (!isValidCodeVerifier(codeVerifier)) {
            log.warn("code_verifier 格式不符合 RFC 7636 规范: 长度={}", codeVerifier.length());
            return false;
        }

        try {
            String computed;
            if (PKCE_METHOD_S256.equals(method)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
                computed = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
            } else if (PKCE_METHOD_PLAIN.equals(method)) {
                computed = codeVerifier;
            } else {
                log.warn("不支持的 code_challenge_method: {}", method);
                return false;
            }

            return computed.equals(codeChallenge);
        } catch (Exception e) {
            log.error("PKCE验证失败", e);
            return false;
        }
    }

    private boolean isValidCodeVerifier(String codeVerifier) {
        if (codeVerifier == null) {
            return false;
        }

        int length = codeVerifier.length();
        if (length < CODE_VERIFIER_MIN_LENGTH || length > CODE_VERIFIER_MAX_LENGTH) {
            return false;
        }

        return codeVerifier.matches("^[A-Za-z0-9\\-._~]+$");
    }

    @Override
    public Mono<Boolean> hasUserConsented(String clientId, String userId, String requestedScopes) {
        if (requestedScopes == null || requestedScopes.isEmpty()) {
            return Mono.just(true);
        }

        return consentRepository
                .findByClientIdAndUserId(clientId, userId)
                .map(consent -> {
                    Set<String> authorizedScopes = consent.getAuthorizedScopes();
                    Set<String> requested = new HashSet<>(Arrays.asList(requestedScopes.split("\\s+")));
                    return authorizedScopes.containsAll(requested);
                })
                .defaultIfEmpty(false);
    }

    @Override
    public Mono<OAuth2AuthorizationConsentEntity> saveConsent(String clientId, String userId, String authorizedScopes) {
        OAuth2AuthorizationConsentEntity consent = new OAuth2AuthorizationConsentEntity();
        consent.setClientId(clientId);
        consent.setUserId(userId);

        if (authorizedScopes != null && !authorizedScopes.isEmpty()) {
            Set<String> scopes = new HashSet<>(Arrays.asList(authorizedScopes.split("\\s+")));
            consent.setAuthorizedScopes(scopes);
        } else {
            consent.setAuthorizedScopes(new HashSet<>());
        }

        consent.setGrantedAt(Instant.now());
        consent.setLastModifiedAt(Instant.now());

        return consentRepository
                .save(consent)
                .doOnSuccess(c -> log.info(
                        "保存OAuth2授权同意: clientId={}, userId={}, scopes={}", clientId, userId, authorizedScopes));
    }

    @Override
    public Mono<Void> revokeConsent(String clientId, String userId) {
        return consentRepository
                .deleteByClientIdAndUserId(clientId, userId)
                .doOnSuccess(v -> log.info("撤销OAuth2授权同意: clientId={}, userId={}", clientId, userId));
    }
}
