package com.apex.scaffold.application.support.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * OIDC（OpenID Connect）SSO 协议适配器。
 *
 * <p>实现 {@link SsoProtocolAdapter} 接口，提供 OIDC 授权码模式的单点登录能力：
 * 发起认证时构建授权端点 URL，并生成随机 state 存入 Redis 做 CSRF 防护
 * （10 分钟有效期、验证后立即删除以防止重放）；回调认证时校验 state 后通过
 * token 端点换取令牌，解析 ID Token 声明并按需调用 UserInfo 端点合并用户信息；
 * 同时支持构造 OIDC 登出 URL、基于 Discovery 文档获取元数据及端点连通性测试。
 *
 * @author Apex Team
 * @version 1.TOKEN_PARTS_COUNT.0
 * @since 1.TOKEN_PARTS_COUNT.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OidcProtocolAdapter implements SsoProtocolAdapter {

    private static final String CLAIM_ATTRIBUTES = "attributes";

    private static final String CLAIM_NAME = "name";

    private static final String CLAIM_EMAIL = "email";

    private static final int TOKEN_PARTS_COUNT = 2;

    /**
     * OIDC State CSRF防护
     */
    private static final String STATE_CACHE_PREFIX = "oidc:state:";

    private static final Duration STATE_TTL = Duration.ofMinutes(10);

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;
    private final ReactiveRedisTemplate<String, String> redisTemplate;

    @Override
    public Mono<Map<String, Object>> initiate(Map<String, Object> config, String callbackUrl) {
        String authEndpoint = (String) config.get("authEndpoint");
        String clientId = (String) config.get("clientId");
        String scope = (String) config.getOrDefault("scope", "openid profile email");

        // 生成安全的state参数
        String state = generateState();

        // 将state存储到Redis用于后续验证（10分钟有效期）
        return storeState(state, callbackUrl).then(Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>(16);

            StringBuilder authUrl = new StringBuilder(authEndpoint)
                    .append("?response_type=code")
                    .append("&client_id=")
                    .append(URLEncoder.encode(clientId, StandardCharsets.UTF_8))
                    .append("&redirect_uri=")
                    .append(URLEncoder.encode(callbackUrl, StandardCharsets.UTF_8))
                    .append("&scope=")
                    .append(URLEncoder.encode(scope, StandardCharsets.UTF_8))
                    .append("&state=")
                    .append(state);

            result.put("redirectUrl", authUrl.toString());
            result.put("state", state);

            log.debug("OIDC认证发起成功: state={}, clientId={}", state, clientId);
            return result;
        }));
    }

    @Override
    public Mono<Map<String, Object>> authenticate(Map<String, Object> config, Map<String, String> callbackParams) {
        String code = callbackParams.get("code");
        String state = callbackParams.get("state");

        if (code == null || code.isBlank()) {
            return Mono.error(new IllegalArgumentException("OIDC 授权码缺失"));
        }

        if (state == null || state.isBlank()) {
            return Mono.error(new SecurityException("OIDC State参数缺失，可能的CSRF攻击"));
        }

        // 验证state参数（CSRF防护）
        return validateState(state).flatMap(isValid -> {
            if (!isValid) {
                log.error("OIDC State验证失败，可能的CSRF攻击: state={}", state);
                return Mono.error(new SecurityException("OIDC State验证失败，可能的CSRF攻击"));
            }

            // State验证通过后删除，防止重放
            return deleteState(state).then(Mono.defer(() -> {
                String tokenEndpoint = (String) config.get("tokenEndpoint");
                String clientId = (String) config.get("clientId");
                String clientSecret = (String) config.get("clientSecret");
                String redirectUri = callbackParams.get("redirect_uri");

                MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                formData.add("grant_type", "authorization_code");
                formData.add("code", code);
                formData.add("redirect_uri", redirectUri);
                formData.add("client_id", clientId);
                formData.add("client_secret", clientSecret);

                return webClientBuilder
                        .build()
                        .post()
                        .uri(tokenEndpoint)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .body(BodyInserters.fromFormData(formData))
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(Duration.ofSeconds(10))
                        .flatMap(tokenResponse -> parseTokenResponse(tokenResponse))
                        .flatMap(tokenData -> {
                            String accessToken = (String) tokenData.get("access_token");
                            String idToken = (String) tokenData.get("id_token");
                            String userInfoEndpoint = (String) config.get("userInfoEndpoint");

                            if (idToken != null) {
                                return parseIdToken(idToken).flatMap(idTokenClaims -> {
                                    if (userInfoEndpoint != null && !userInfoEndpoint.isBlank()) {
                                        return fetchUserInfo(userInfoEndpoint, accessToken)
                                                .map(userInfo -> mergeUserInfo(idTokenClaims, userInfo));
                                    }
                                    return Mono.just(idTokenClaims);
                                });
                            } else if (userInfoEndpoint != null) {
                                return fetchUserInfo(userInfoEndpoint, accessToken);
                            }
                            return Mono.error(new RuntimeException("OIDC 响应缺少 id_token 和 userinfo 端点"));
                        })
                        .doOnSuccess(userInfo -> log.info("OIDC认证成功: userId={}", userInfo.get("userId")))
                        .doOnError(e -> log.error("OIDC 认证失败: code={}, error=", code, e.getMessage()));
            }));
        });
    }

    @Override
    public Mono<Map<String, Object>> logout(Map<String, Object> config, String sessionToken) {
        return Mono.fromCallable(() -> {
            Map<String, Object> result = new HashMap<>(16);
            String logoutEndpoint = (String) config.get("logoutEndpoint");
            if (logoutEndpoint != null && !logoutEndpoint.isBlank()) {
                String postLogoutRedirectUri = (String) config.get("postLogoutRedirectUri");
                StringBuilder logoutUrl = new StringBuilder(logoutEndpoint);
                if (sessionToken != null && !sessionToken.isBlank()) {
                    logoutUrl.append("?id_token_hint=").append(sessionToken);
                }
                if (postLogoutRedirectUri != null && !postLogoutRedirectUri.isBlank()) {
                    logoutUrl
                            .append(sessionToken != null ? "&" : "?")
                            .append("post_logout_redirect_uri=")
                            .append(URLEncoder.encode(postLogoutRedirectUri, StandardCharsets.UTF_8));
                }
                result.put("logoutUrl", logoutUrl.toString());
            }
            result.put("success", true);
            return result;
        });
    }

    @Override
    public Mono<Map<String, Object>> getMetadata(Map<String, Object> config) {
        String discoveryUrl = (String) config.get("discoveryUrl");
        if (discoveryUrl == null || discoveryUrl.isBlank()) {
            return Mono.fromCallable(() -> {
                Map<String, Object> metadata = new HashMap<>(16);
                metadata.put("protocol", "OIDC");
                metadata.put("authEndpoint", config.get("authEndpoint"));
                metadata.put("tokenEndpoint", config.get("tokenEndpoint"));
                metadata.put("userInfoEndpoint", config.get("userInfoEndpoint"));
                metadata.put("logoutEndpoint", config.get("logoutEndpoint"));
                return metadata;
            });
        }

        return webClientBuilder
                .build()
                .get()
                .uri(discoveryUrl)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .flatMap(discoveryJson -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> discovery = objectMapper.readValue(discoveryJson, Map.class);
                        return Mono.just(discovery);
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("解析 OIDC Discovery 失败: " + e.getMessage()));
                    }
                })
                .onErrorResume(e -> {
                    log.warn("获取 OIDC Discovery 失败: {}", e.getMessage());
                    return Mono.fromCallable(() -> {
                        Map<String, Object> metadata = new HashMap<>(16);
                        metadata.put("protocol", "OIDC");
                        metadata.put("error", e.getMessage());
                        return metadata;
                    });
                });
    }

    @Override
    public Mono<Map<String, Object>> testConnection(Map<String, Object> config) {
        String discoveryUrl = (String) config.get("discoveryUrl");
        if (discoveryUrl == null || discoveryUrl.isBlank()) {
            String authEndpoint = (String) config.get("authEndpoint");
            if (authEndpoint == null || authEndpoint.isBlank()) {
                return Mono.fromCallable(() -> {
                    Map<String, Object> result = new HashMap<>(16);
                    result.put("success", false);
                    result.put("message", "OIDC discoveryUrl 或 authEndpoint 配置缺失");
                    return result;
                });
            }
            return testEndpoint(authEndpoint);
        }

        return testEndpoint(discoveryUrl);
    }

    private Mono<Map<String, Object>> testEndpoint(String endpoint) {
        return webClientBuilder
                .build()
                .get()
                .uri(endpoint)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(5))
                .map(response -> {
                    Map<String, Object> result = new HashMap<>(16);
                    result.put("success", response.getStatusCode().is2xxSuccessful());
                    result.put("message", "OIDC 端点可访问");
                    result.put("statusCode", response.getStatusCode().value());
                    return result;
                })
                .onErrorResume(e -> {
                    Map<String, Object> result = new HashMap<>(16);
                    result.put("success", false);
                    result.put("message", "OIDC 连接失败: " + e.getMessage());
                    return Mono.just(result);
                });
    }

    private Mono<Map<String, Object>> parseTokenResponse(String tokenJson) {
        return Mono.fromCallable(() -> {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> tokenData = objectMapper.readValue(tokenJson, Map.class);
                return tokenData;
            } catch (Exception e) {
                throw new RuntimeException("解析 OIDC Token 响应失败: " + e.getMessage());
            }
        });
    }

    private Mono<Map<String, Object>> parseIdToken(String idToken) {
        return Mono.fromCallable(() -> {
            try {
                String[] parts = idToken.split("\\.");
                if (parts.length < TOKEN_PARTS_COUNT) {
                    throw new RuntimeException("ID Token 格式无效");
                }

                String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                @SuppressWarnings("unchecked")
                Map<String, Object> claims = objectMapper.readValue(payloadJson, Map.class);

                Map<String, Object> userInfo = new HashMap<>(16);
                userInfo.put("userId", claims.getOrDefault("sub", ""));
                userInfo.put("username", claims.getOrDefault("preferred_username", claims.get("sub")));

                Map<String, String> attributes = new HashMap<>(16);
                if (claims.containsKey(CLAIM_EMAIL)) {
                    attributes.put(CLAIM_EMAIL, String.valueOf(claims.get(CLAIM_EMAIL)));
                }
                if (claims.containsKey(CLAIM_NAME)) {
                    attributes.put(CLAIM_NAME, String.valueOf(claims.get(CLAIM_NAME)));
                }
                userInfo.put(CLAIM_ATTRIBUTES, attributes);

                return userInfo;
            } catch (Exception e) {
                throw new RuntimeException("解析 ID Token 失败: " + e.getMessage());
            }
        });
    }

    private Mono<Map<String, Object>> fetchUserInfo(String userInfoEndpoint, String accessToken) {
        return webClientBuilder
                .build()
                .get()
                .uri(userInfoEndpoint)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .flatMap(userInfoJson -> {
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> userInfoData = objectMapper.readValue(userInfoJson, Map.class);

                        Map<String, Object> userInfo = new HashMap<>(16);
                        userInfo.put("userId", userInfoData.getOrDefault("sub", ""));
                        userInfo.put(
                                "username", userInfoData.getOrDefault("preferred_username", userInfoData.get("sub")));

                        Map<String, String> attributes = new HashMap<>(16);
                        if (userInfoData.containsKey(CLAIM_EMAIL)) {
                            attributes.put(CLAIM_EMAIL, String.valueOf(userInfoData.get(CLAIM_EMAIL)));
                        }
                        if (userInfoData.containsKey(CLAIM_NAME)) {
                            attributes.put(CLAIM_NAME, String.valueOf(userInfoData.get(CLAIM_NAME)));
                        }
                        userInfo.put(CLAIM_ATTRIBUTES, attributes);

                        return Mono.just(userInfo);
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("解析 UserInfo 响应失败: " + e.getMessage()));
                    }
                });
    }

    private Map<String, Object> mergeUserInfo(Map<String, Object> idTokenClaims, Map<String, Object> userInfo) {
        Map<String, Object> merged = new HashMap<>(idTokenClaims);
        if (userInfo.containsKey(CLAIM_ATTRIBUTES)) {
            @SuppressWarnings("unchecked")
            Map<String, String> idTokenAttrs = (Map<String, String>) merged.get(CLAIM_ATTRIBUTES);
            @SuppressWarnings("unchecked")
            Map<String, String> userInfoAttrs = (Map<String, String>) userInfo.get(CLAIM_ATTRIBUTES);
            idTokenAttrs.putAll(userInfoAttrs);
        }
        return merged;
    }

    private String generateState() {
        byte[] randomBytes = new byte[16];
        new java.security.SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    /**
     * 存储OIDC State到Redis用于后续验证
     *
     * @param state 状态码
     * @param callbackUrl 回调URL（用于审计）
     * @return 存储成功返回Mono.empty()
     */
    private Mono<Void> storeState(String state, String callbackUrl) {
        String key = STATE_CACHE_PREFIX + state;
        return redisTemplate
                .opsForValue()
                .set(key, callbackUrl, STATE_TTL)
                .then()
                .doOnSuccess(v -> log.debug("OIDC State已存储: state={}", state));
    }

    /**
     * 验证OIDC State是否有效
     *
     * @param state 状态码
     * @return true表示有效，false表示无效或已过期
     */
    private Mono<Boolean> validateState(String state) {
        String key = STATE_CACHE_PREFIX + state;
        return redisTemplate.hasKey(key);
    }

    /**
     * 删除已使用的OIDC State（防止重放）
     *
     * @param state 状态码
     * @return 删除成功返回Mono.empty()
     */
    private Mono<Void> deleteState(String state) {
        String key = STATE_CACHE_PREFIX + state;
        return redisTemplate.delete(key).then().doOnSuccess(v -> log.debug("OIDC State已删除: state={}", state));
    }
}
