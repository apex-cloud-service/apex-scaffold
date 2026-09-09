package com.apex.scaffold.application.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.model.entity.BaseEntity;
import com.apex.scaffold.domain.model.entity.OAuth2AccessTokenEntity;
import com.apex.scaffold.domain.model.entity.OAuth2AuthorizationCodeEntity;
import com.apex.scaffold.domain.model.entity.OAuth2AuthorizationConsentEntity;
import com.apex.scaffold.domain.model.entity.OAuth2RegisteredClientEntity;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.repository.OAuth2AuthorizationConsentRepository;
import com.apex.scaffold.domain.repository.OAuth2RegisteredClientRepository;
import com.apex.scaffold.domain.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth2授权服务测试")
class OAuth2AuthorizationServiceImplTest {

    static {
        System.setProperty("jwt.secret", "test-jwt-secret-key-for-unit-testing-only-32chars");
    }

    @Mock
    private OAuth2RegisteredClientRepository clientRepository;
    
    @Mock
    private OAuth2AuthorizationConsentRepository consentRepository;
    
    @Mock
    private UserRepository userRepository;
    
    @Mock
    private RedissonClient redissonClient;
    
    @Mock
    private PasswordEncoder passwordEncoder;
    
    @Mock
    @SuppressWarnings("rawtypes")
    private RMapCache mapCache;
    
    @InjectMocks
    private OAuth2AuthorizationServiceImpl authorizationService;
    
    private OAuth2RegisteredClientEntity testClient;
    private UserEntity testUser;
    
    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        testClient = createTestClient();
        testUser = createTestUser();

        lenient().when(redissonClient.getMapCache(anyString())).thenReturn(mapCache);
    }
    
    @Test
    @DisplayName("生成授权码 - 成功")
    void generateAuthorizationCode_Success() {
        String clientId = "test-client";
        String userId = "user-123";
        String redirectUri = "http://localhost:3000/callback";
        String scope = "read write";
        String state = "xyz";
        String codeChallenge = "challenge";
        String codeChallengeMethod = "S256";
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        
        Mono<OAuth2AuthorizationCodeEntity> result = authorizationService.generateAuthorizationCode(
                clientId, userId, redirectUri, scope, state, codeChallenge, codeChallengeMethod);
        
        StepVerifier.create(result)
                .assertNext(authCode -> {
                    assertThat(authCode.getCode()).isNotNull();
                    assertThat(authCode.getClientId()).isEqualTo(clientId);
                    assertThat(authCode.getUserId()).isEqualTo(userId);
                    assertThat(authCode.getRedirectUri()).isEqualTo(redirectUri);
                    assertThat(authCode.getScope()).isEqualTo(scope);
                    assertThat(authCode.getCodeChallenge()).isEqualTo(codeChallenge);
                    assertThat(authCode.getCodeChallengeMethod()).isEqualTo(codeChallengeMethod);
                })
                .verifyComplete();
        
        verify(clientRepository).findByClientId(clientId);
    }
    
    @Test
    @DisplayName("生成授权码 - 客户端不存在")
    void generateAuthorizationCode_ClientNotFound() {
        String clientId = "non-existent-client";
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.empty());
        
        Mono<OAuth2AuthorizationCodeEntity> result = authorizationService.generateAuthorizationCode(
                clientId, "user-123", "http://localhost/callback", "read", "xyz", null, null);
        
        StepVerifier.create(result)
                .expectError(BusinessException.class)
                .verify();
    }
    
    @Test
    @DisplayName("生成授权码 - 不支持授权码模式")
    void generateAuthorizationCode_UnsupportedGrantType() {
        String clientId = "test-client";
        OAuth2RegisteredClientEntity client = createTestClient();
        client.setAuthorizationGrantTypes(Set.of("client_credentials"));
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(client));
        
        Mono<OAuth2AuthorizationCodeEntity> result = authorizationService.generateAuthorizationCode(
                clientId, "user-123", "http://localhost/callback", "read", "xyz", null, null);
        
        StepVerifier.create(result)
                .expectError(BusinessException.class)
                .verify();
    }
    
    @Test
    @DisplayName("生成授权码 - 无效的重定向URI")
    void generateAuthorizationCode_InvalidRedirectUri() {
        String clientId = "test-client";
        String invalidRedirectUri = "http://evil.com/callback";
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        
        Mono<OAuth2AuthorizationCodeEntity> result = authorizationService.generateAuthorizationCode(
                clientId, "user-123", invalidRedirectUri, "read", "xyz", null, null);
        
        StepVerifier.create(result)
                .expectError(BusinessException.class)
                .verify();
    }
    
    @Test
    @DisplayName("客户端凭证模式 - 成功")
    void clientCredentialsGrant_Success() {
        String clientId = "test-client";
        String clientSecret = "test-secret";
        String scope = "api:read api:write";
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
                .thenReturn(true);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.clientCredentialsGrant(
                clientId, clientSecret, scope);
        
        StepVerifier.create(result)
                .assertNext(token -> {
                    assertThat(token.getTokenValue()).isNotNull();
                    assertThat(token.getTokenType()).isEqualTo("Bearer");
                    assertThat(token.getClientId()).isEqualTo(clientId);
                    assertThat(token.getScope()).isEqualTo(scope);
                    assertThat(token.getUserId()).isNull();
                })
                .verifyComplete();
    }
    
    @Test
    @DisplayName("客户端凭证模式 - 密钥错误")
    void clientCredentialsGrant_InvalidSecret() {
        String clientId = "test-client";
        String wrongSecret = "wrong-secret";
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(wrongSecret, testClient.getClientSecret()))
                .thenReturn(false);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.clientCredentialsGrant(
                clientId, wrongSecret, "read");
        
        StepVerifier.create(result)
                .expectError(BusinessException.class)
                .verify();
    }
    
    @Test
    @DisplayName("密码模式 - 成功")
    void passwordGrant_Success() {
        String clientId = "test-client";
        String clientSecret = "test-secret";
        String username = "testuser";
        String password = "password123";
        String scope = "read write";
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
                .thenReturn(true);
        when(userRepository.findByUsername(username))
                .thenReturn(Mono.just(testUser));
        when(passwordEncoder.matches(eq(password), anyString()))
                .thenReturn(true);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.passwordGrant(
                clientId, clientSecret, username, password, scope);
        
        StepVerifier.create(result)
                .assertNext(token -> {
                    assertThat(token.getTokenValue()).isNotNull();
                    assertThat(token.getTokenType()).isEqualTo("Bearer");
                    assertThat(token.getClientId()).isEqualTo(clientId);
                    assertThat(token.getUserId()).isEqualTo(testUser.getOpenId());
                    assertThat(token.getScope()).isEqualTo(scope);
                })
                .verifyComplete();
    }
    
    @Test
    @DisplayName("密码模式 - 用户名或密码错误")
    void passwordGrant_InvalidCredentials() {
        String clientId = "test-client";
        String clientSecret = "test-secret";
        String username = "testuser";
        String wrongPassword = "wrong-password";
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
                .thenReturn(true);
        when(userRepository.findByUsername(username))
                .thenReturn(Mono.just(testUser));
        when(passwordEncoder.matches(eq(wrongPassword), anyString()))
                .thenReturn(false);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.passwordGrant(
                clientId, clientSecret, username, wrongPassword, "read");
        
        StepVerifier.create(result)
                .expectError(BusinessException.class)
                .verify();
    }
    
    @Test
    @DisplayName("检查用户授权同意 - 已授权")
    void hasUserConsented_AlreadyConsented() {
        String clientId = "test-client";
        String userId = "user-123";
        String requestedScopes = "read write";
        
        OAuth2AuthorizationConsentEntity consent = new OAuth2AuthorizationConsentEntity();
        consent.setClientId(clientId);
        consent.setUserId(userId);
        consent.setAuthorizedScopes(new HashSet<>(Arrays.asList("read", "write", "user:profile")));
        
        when(consentRepository.findByClientIdAndUserId(clientId, userId))
                .thenReturn(Mono.just(consent));
        
        Mono<Boolean> result = authorizationService.hasUserConsented(clientId, userId, requestedScopes);
        
        StepVerifier.create(result)
                .expectNext(true)
                .verifyComplete();
    }
    
    @Test
    @DisplayName("检查用户授权同意 - 未授权")
    void hasUserConsented_NotConsented() {
        String clientId = "test-client";
        String userId = "user-123";
        String requestedScopes = "read write";
        
        when(consentRepository.findByClientIdAndUserId(clientId, userId))
                .thenReturn(Mono.empty());
        
        Mono<Boolean> result = authorizationService.hasUserConsented(clientId, userId, requestedScopes);
        
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }
    
    @Test
    @DisplayName("检查用户授权同意 - 部分授权")
    void hasUserConsented_PartiallyConsented() {
        String clientId = "test-client";
        String userId = "user-123";
        String requestedScopes = "read write admin:users";
        
        OAuth2AuthorizationConsentEntity consent = new OAuth2AuthorizationConsentEntity();
        consent.setClientId(clientId);
        consent.setUserId(userId);
        consent.setAuthorizedScopes(new HashSet<>(Arrays.asList("read", "write")));
        
        when(consentRepository.findByClientIdAndUserId(clientId, userId))
                .thenReturn(Mono.just(consent));
        
        Mono<Boolean> result = authorizationService.hasUserConsented(clientId, userId, requestedScopes);
        
        StepVerifier.create(result)
                .expectNext(false)
                .verifyComplete();
    }
    
    @Test
    @DisplayName("保存授权同意 - 成功")
    void saveConsent_Success() {
        String clientId = "test-client";
        String userId = "user-123";
        String authorizedScopes = "read write user:profile";
        
        when(consentRepository.save(any(OAuth2AuthorizationConsentEntity.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        
        Mono<OAuth2AuthorizationConsentEntity> result = authorizationService.saveConsent(
                clientId, userId, authorizedScopes);
        
        StepVerifier.create(result)
                .assertNext(consent -> {
                    assertThat(consent.getClientId()).isEqualTo(clientId);
                    assertThat(consent.getUserId()).isEqualTo(userId);
                    assertThat(consent.getAuthorizedScopes())
                            .containsExactlyInAnyOrder("read", "write", "user:profile");
                })
                .verifyComplete();
        
        verify(consentRepository).save(any(OAuth2AuthorizationConsentEntity.class));
    }
    
    @Test
    @DisplayName("撤销授权同意 - 成功")
    void revokeConsent_Success() {
        String clientId = "test-client";
        String userId = "user-123";
        
        when(consentRepository.deleteByClientIdAndUserId(clientId, userId))
                .thenReturn(Mono.empty());
        
        Mono<Void> result = authorizationService.revokeConsent(clientId, userId);
        
        StepVerifier.create(result)
                .verifyComplete();
        
        verify(consentRepository).deleteByClientIdAndUserId(clientId, userId);
    }
    
    private OAuth2RegisteredClientEntity createTestClient() {
        OAuth2RegisteredClientEntity client = new OAuth2RegisteredClientEntity();
        client.setId("client-id-123");
        client.setClientId("test-client");
        client.setClientSecret("$2a$10$encrypted-secret");
        client.setClientName("Test Client");
        client.setClientAuthenticationMethods(Set.of("client_secret_basic"));
        client.setAuthorizationGrantTypes(Set.of("authorization_code", "client_credentials", "password", "refresh_token"));
        client.setRedirectUris(Set.of("http://localhost:3000/callback", "http://localhost:5173/callback"));
        client.setScopes(Set.of("read", "write", "user:profile"));
        
        client.setClientSettings("{\"requireProofKey\":false,\"requireAuthorizationConsent\":true}");
        client.setTokenSettings("{\"accessTokenTimeToLive\":7200,\"refreshTokenTimeToLive\":604800}");
        return client;
    }
    
    @Test
    @DisplayName("Token刷新 - 成功")
    void refreshToken_Success() {
        String refreshToken = "valid-refresh-token";
        String clientId = "test-client";
        String clientSecret = "test-secret";
        
        OAuth2AccessTokenEntity oldToken = new OAuth2AccessTokenEntity();
        oldToken.setClientId(clientId);
        oldToken.setUserId("user-123");
        oldToken.setScope("read write");
        oldToken.setRefreshToken(refreshToken);
        oldToken.setExpiresAt(java.time.Instant.now().plusSeconds(3600));
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
                .thenReturn(true);
        when(mapCache.get(refreshToken))
                .thenReturn(oldToken);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.refreshToken(
                refreshToken, clientId, clientSecret);
        
        StepVerifier.create(result)
                .assertNext(token -> {
                    assertThat(token.getTokenValue()).isNotNull();
                    assertThat(token.getTokenType()).isEqualTo("Bearer");
                    assertThat(token.getClientId()).isEqualTo(clientId);
                    assertThat(token.getUserId()).isEqualTo("user-123");
                })
                .verifyComplete();
    }
    
    @Test
    @DisplayName("Token刷新 - 刷新令牌无效")
    void refreshToken_InvalidToken() {
        String refreshToken = "invalid-refresh-token";
        String clientId = "test-client";
        String clientSecret = "test-secret";
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
                .thenReturn(true);
        when(mapCache.get(refreshToken))
                .thenReturn(null);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.refreshToken(
                refreshToken, clientId, clientSecret);
        
        StepVerifier.create(result)
                .expectError(BusinessException.class)
                .verify();
    }
    
    @Test
    @DisplayName("Token刷新 - 刷新令牌已过期")
    void refreshToken_Expired() {
        String refreshToken = "expired-refresh-token";
        String clientId = "test-client";
        String clientSecret = "test-secret";
        
        OAuth2AccessTokenEntity expiredToken = new OAuth2AccessTokenEntity();
        expiredToken.setClientId(clientId);
        expiredToken.setExpiresAt(java.time.Instant.now().minusSeconds(3600));
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(clientSecret, testClient.getClientSecret()))
                .thenReturn(true);
        when(mapCache.get(refreshToken))
                .thenReturn(expiredToken);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.refreshToken(
                refreshToken, clientId, clientSecret);
        
        StepVerifier.create(result)
                .expectError(BusinessException.class)
                .verify();
    }
    
    @Test
    @DisplayName("撤销Token - 成功")
    void revokeToken_Success() {
        String token = "access-token-to-revoke";
        String clientId = "test-client";
        
        Mono<Void> result = authorizationService.revokeToken(token, clientId);
        
        StepVerifier.create(result)
                .verifyComplete();
    }
    
    @Test
    @DisplayName("撤销Token - Token不存在（应该成功，符合RFC规范）")
    void revokeToken_TokenNotExists() {
        String token = "non-existent-token";
        String clientId = "test-client";
        
        Mono<Void> result = authorizationService.revokeToken(token, clientId);
        
        StepVerifier.create(result)
                .verifyComplete();
    }
    
    @Test
    @DisplayName("PKCE流程 - S256方法成功")
    void pkceFlow_S256_Success() {
        String codeVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
        String codeChallenge = computeS256Challenge(codeVerifier);
        String code = "test-auth-code-with-pkce";
        String clientId = "test-client";
        
        OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
        authCode.setCode(code);
        authCode.setClientId(clientId);
        authCode.setUserId("user-123");
        authCode.setRedirectUri("http://localhost:3000/callback");
        authCode.setScope("read write");
        authCode.setCodeChallenge(codeChallenge);
        authCode.setCodeChallengeMethod("S256");
        authCode.setIssuedAt(java.time.Instant.now());
        authCode.setExpiresAt(java.time.Instant.now().plusSeconds(300));
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
        when(mapCache.remove(code))
                .thenReturn(authCode);
        
        Mono<OAuth2AccessTokenEntity> result =
                authorizationService.exchangeCodeForToken(
                        code,
                        clientId,
                        "test-secret",
                        "http://localhost:3000/callback",
                        codeVerifier);
        
        StepVerifier.create(result)
                .assertNext(token -> {
                    assertThat(token.getTokenValue()).isNotNull();
                    assertThat(token.getTokenType()).isEqualTo("Bearer");
                    assertThat(token.getClientId()).isEqualTo(clientId);
                    assertThat(token.getUserId()).isEqualTo("user-123");
                })
                .verifyComplete();
        
        verify(mapCache).remove(code);
    }
    
    @Test
    @DisplayName("PKCE流程 - code_verifier验证失败")
    void pkceFlow_VerifierMismatch() {
        String correctVerifier = "correct-verifier-value";
        String wrongVerifier = "wrong-verifier-value";
        String codeChallenge = computeS256Challenge(correctVerifier);
        String code = "test-auth-code-pkce-fail";
        String clientId = "test-client";
        
        OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
        authCode.setCode(code);
        authCode.setClientId(clientId);
        authCode.setUserId("user-123");
        authCode.setRedirectUri("http://localhost:3000/callback");
        authCode.setScope("read write");
        authCode.setCodeChallenge(codeChallenge);
        authCode.setCodeChallengeMethod("S256");
        authCode.setIssuedAt(java.time.Instant.now());
        authCode.setExpiresAt(java.time.Instant.now().plusSeconds(300));
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
        when(mapCache.remove(code))
                .thenReturn(authCode);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.exchangeCodeForToken(
                code,
                clientId,
                "test-secret",
                "http://localhost:3000/callback",
                wrongVerifier);
        
        StepVerifier.create(result)
                .expectErrorMatches(ex ->
                        ex instanceof BusinessException &&
                        ex.getMessage().contains("PKCE验证失败"))
                .verify();
    }
    
    @Test
    @DisplayName("PKCE流程 - plain方法")
    void pkceFlow_Plain_Success() {
        String codeVerifier = "plain-code-verifier-value-with-enough-length-for-rfc7636-compliance";
        String codeChallenge = codeVerifier;
        String code = "test-auth-code-plain";
        String clientId = "test-client";
        
        OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
        authCode.setCode(code);
        authCode.setClientId(clientId);
        authCode.setUserId("user-123");
        authCode.setRedirectUri("http://localhost:3000/callback");
        authCode.setScope("read write");
        authCode.setCodeChallenge(codeChallenge);
        authCode.setCodeChallengeMethod("plain");
        authCode.setIssuedAt(java.time.Instant.now());
        authCode.setExpiresAt(java.time.Instant.now().plusSeconds(300));
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
        when(mapCache.remove(code))
                .thenReturn(authCode);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.exchangeCodeForToken(
                code,
                clientId,
                "test-secret",
                "http://localhost:3000/callback",
                codeVerifier);
        
        StepVerifier.create(result)
                .assertNext(token -> {
                    assertThat(token.getTokenValue()).isNotNull();
                    assertThat(token.getTokenType()).isEqualTo("Bearer");
                    assertThat(token.getClientId()).isEqualTo(clientId);
                    assertThat(token.getUserId()).isEqualTo("user-123");
                })
                .verifyComplete();
        
        verify(mapCache).remove(code);
    }
    
    @Test
    @DisplayName("PKCE流程 - code_verifier 长度不足（小于43字符）")
    void pkceFlow_VerifierTooShort() {
        String shortVerifier = "too-short-verifier";
        String codeChallenge = computeS256Challenge("valid-verifier-with-correct-length-for-testing");
        String code = "test-auth-code-short";
        String clientId = "test-client";
        
        OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
        authCode.setCode(code);
        authCode.setClientId(clientId);
        authCode.setUserId("user-123");
        authCode.setRedirectUri("http://localhost:3000/callback");
        authCode.setScope("read write");
        authCode.setCodeChallenge(codeChallenge);
        authCode.setCodeChallengeMethod("S256");
        authCode.setIssuedAt(java.time.Instant.now());
        authCode.setExpiresAt(java.time.Instant.now().plusSeconds(300));
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
        when(mapCache.remove(code))
                .thenReturn(authCode);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.exchangeCodeForToken(
                code,
                clientId,
                "test-secret",
                "http://localhost:3000/callback",
                shortVerifier);
        
        StepVerifier.create(result)
                .expectErrorMatches(ex ->
                        ex instanceof BusinessException &&
                        ex.getMessage().contains("PKCE验证失败"))
                .verify();
    }
    
    @Test
    @DisplayName("PKCE流程 - code_verifier 长度超限（大于128字符）")
    void pkceFlow_VerifierTooLong() {
        String longVerifier = "a".repeat(129);
        String codeChallenge = computeS256Challenge("valid-verifier-with-correct-length-for-testing");
        String code = "test-auth-code-long";
        String clientId = "test-client";
        
        OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
        authCode.setCode(code);
        authCode.setClientId(clientId);
        authCode.setUserId("user-123");
        authCode.setRedirectUri("http://localhost:3000/callback");
        authCode.setScope("read write");
        authCode.setCodeChallenge(codeChallenge);
        authCode.setCodeChallengeMethod("S256");
        authCode.setIssuedAt(java.time.Instant.now());
        authCode.setExpiresAt(java.time.Instant.now().plusSeconds(300));
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
        when(mapCache.remove(code))
                .thenReturn(authCode);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.exchangeCodeForToken(
                code,
                clientId,
                "test-secret",
                "http://localhost:3000/callback",
                longVerifier);
        
        StepVerifier.create(result)
                .expectErrorMatches(ex ->
                        ex instanceof BusinessException &&
                        ex.getMessage().contains("PKCE验证失败"))
                .verify();
    }
    
    @Test
    @DisplayName("PKCE流程 - code_verifier 包含非法字符")
    void pkceFlow_VerifierInvalidChars() {
        String invalidVerifier = "invalid@verifier#with$special%chars&and*symbols";
        String codeChallenge = computeS256Challenge("valid-verifier-with-correct-length-for-testing");
        String code = "test-auth-code-invalid";
        String clientId = "test-client";
        
        OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
        authCode.setCode(code);
        authCode.setClientId(clientId);
        authCode.setUserId("user-123");
        authCode.setRedirectUri("http://localhost:3000/callback");
        authCode.setScope("read write");
        authCode.setCodeChallenge(codeChallenge);
        authCode.setCodeChallengeMethod("S256");
        authCode.setIssuedAt(java.time.Instant.now());
        authCode.setExpiresAt(java.time.Instant.now().plusSeconds(300));
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
        when(mapCache.remove(code))
                .thenReturn(authCode);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.exchangeCodeForToken(
                code,
                clientId,
                "test-secret",
                "http://localhost:3000/callback",
                invalidVerifier);
        
        StepVerifier.create(result)
                .expectErrorMatches(ex ->
                        ex instanceof BusinessException &&
                        ex.getMessage().contains("PKCE验证失败"))
                .verify();
    }
    
    @Test
    @DisplayName("PKCE流程 - 不支持的 code_challenge_method")
    void pkceFlow_UnsupportedMethod() {
        String codeVerifier = "valid-code-verifier-with-correct-length-for-testing";
        String codeChallenge = "some-challenge-value";
        String code = "test-auth-code-unsupported";
        String clientId = "test-client";
        
        OAuth2AuthorizationCodeEntity authCode = new OAuth2AuthorizationCodeEntity();
        authCode.setCode(code);
        authCode.setClientId(clientId);
        authCode.setUserId("user-123");
        authCode.setRedirectUri("http://localhost:3000/callback");
        authCode.setScope("read write");
        authCode.setCodeChallenge(codeChallenge);
        authCode.setCodeChallengeMethod("MD5");
        authCode.setIssuedAt(java.time.Instant.now());
        authCode.setExpiresAt(java.time.Instant.now().plusSeconds(300));
        
        when(clientRepository.findByClientId(clientId))
                .thenReturn(Mono.just(testClient));
        when(passwordEncoder.matches(anyString(), anyString()))
                .thenReturn(true);
        when(mapCache.remove(code))
                .thenReturn(authCode);
        
        Mono<OAuth2AccessTokenEntity> result = authorizationService.exchangeCodeForToken(
                code,
                clientId,
                "test-secret",
                "http://localhost:3000/callback",
                codeVerifier);
        
        StepVerifier.create(result)
                .expectErrorMatches(ex ->
                        ex instanceof BusinessException &&
                        ex.getMessage().contains("PKCE验证失败"))
                .verify();
    }
    
    private String computeS256Challenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    private UserEntity createTestUser() {
        UserEntity user = new UserEntity();
        try {
            java.lang.reflect.Field idField = BaseEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(user, 1L);
            
            java.lang.reflect.Field openIdField = UserEntity.class.getDeclaredField("openId");
            openIdField.setAccessible(true);
            openIdField.set(user, "user-openid-123");
            
            java.lang.reflect.Field usernameField = UserEntity.class.getDeclaredField("username");
            usernameField.setAccessible(true);
            usernameField.set(user, "testuser");
            
            java.lang.reflect.Field passwordField = UserEntity.class.getDeclaredField("password");
            passwordField.setAccessible(true);
            passwordField.set(user, "$2a$10$encrypted-password");
            
            java.lang.reflect.Field statusField = UserEntity.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(user, 1);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test user", e);
        }
        return user;
    }
}
