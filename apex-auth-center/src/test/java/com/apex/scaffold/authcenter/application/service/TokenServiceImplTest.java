package com.apex.scaffold.authcenter.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.authcenter.domain.model.entity.TokenEntity;
import com.apex.scaffold.authcenter.domain.model.entity.UserEntity;
import com.apex.scaffold.authcenter.domain.model.vo.TokenPair;
import com.apex.scaffold.authcenter.domain.repository.TokenRepository;
import com.apex.scaffold.authcenter.domain.repository.UserRepository;
import com.apex.scaffold.authcenter.domain.service.SecurityAuditService;
import com.apex.scaffold.authcenter.domain.service.TokenEncryptionService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.impl.DefaultClaims;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * TokenServiceImpl 单元测试
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TokenServiceImpl 单元测试")
class TokenServiceImplTest {

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenEncryptionService encryptionService;

    @Mock
    private SecurityAuditService auditService;

    @InjectMocks
    private TokenServiceImpl tokenService;

    private UserEntity mockUser;
    private TokenEntity mockAccessToken;
    private TokenEntity mockRefreshToken;

    @BeforeEach
    void setUp() {
        mockUser = UserEntity.builder()
                .id(100L)
                .username("testuser")
                .tenantId("test-tenant")
                .passwordHash("hashed-password")
                .email("test@example.com")
                .phone("13800138000")
                .status(1) // 1 = 正常状态
                .failedLoginAttempts(0)
                .build();

        mockAccessToken = TokenEntity.builder()
                .id(1L)
                .tenantId("test-tenant")
                .userId(100L)
                .tokenType("ACCESS")
                .tokenValue("access-token-value")
                .jti("access-jti-123")
                .expiresAt(LocalDateTime.now().plusHours(2))
                .deviceId("device-001")
                .clientIp("192.168.1.100")
                .revoked(0)
                .build();

        mockRefreshToken = TokenEntity.builder()
                .id(2L)
                .tenantId("test-tenant")
                .userId(100L)
                .tokenType("REFRESH")
                .tokenValue("refresh-token-value")
                .jti("refresh-jti-456")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .deviceId("device-001")
                .clientIp("192.168.1.100")
                .revoked(0)
                .build();
    }

    @Nested
    @DisplayName("issueToken() 方法测试")
    class IssueTokenTest {

        @Test
        @DisplayName("成功签发 Token 对")
        void testIssueTokenSuccess() {
            // Given
            when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));
            when(tokenRepository.save(any(TokenEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(new TokenEncryptionService.EncryptedToken("encrypted-data", "test-iv"));
            when(encryptionService.hashToken(anyString())).thenReturn("token-hash");
            when(auditService.logTokenIssued(anyLong(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.generateToken(anyString(), anyMap(), anyLong()))
                        .thenReturn("mocked-jwt-token");

                // When
                Mono<TokenPair> result = tokenService.issueToken(100L, "test-tenant", "device-001", "192.168.1.100");

                // Then
                StepVerifier.create(result)
                        .assertNext(pair -> {
                            assertThat(pair.getAccessToken()).isEqualTo("mocked-jwt-token");
                            assertThat(pair.getRefreshToken()).isEqualTo("mocked-jwt-token");
                            assertThat(pair.getExpiresIn()).isEqualTo(7200); // 2小时
                            assertThat(pair.getTokenType()).isEqualTo("Bearer");
                        })
                        .verifyComplete();

                verify(userRepository).findById(100L);
                verify(tokenRepository, times(2)).save(any(TokenEntity.class));
            }
        }

        @Test
        @DisplayName("用户不存在时抛出异常")
        void testIssueTokenUserNotFound() {
            // Given
            when(userRepository.findById(999L)).thenReturn(Mono.empty());

            // When
            Mono<TokenPair> result = tokenService.issueToken(999L, "test-tenant", "device-001", "192.168.1.100");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("用户不存在"))
                    .verify();

            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("租户不匹配时抛出异常")
        void testIssueTokenTenantMismatch() {
            // Given
            when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));

            // When
            Mono<TokenPair> result = tokenService.issueToken(100L, "different-tenant", "device-001", "192.168.1.100");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("租户不匹配"))
                    .verify();

            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("账户已禁用时抛出异常")
        void testIssueTokenUserDisabled() {
            // Given - 重新创建 status=0 的禁用用户
            UserEntity disabledUser = UserEntity.builder()
                    .id(100L)
                    .username("testuser")
                    .tenantId("test-tenant")
                    .passwordHash("hashed-password")
                    .status(0) // 0 = 禁用状态
                    .failedLoginAttempts(0)
                    .build();

            when(userRepository.findById(100L)).thenReturn(Mono.just(disabledUser));

            // When
            Mono<TokenPair> result = tokenService.issueToken(100L, "test-tenant", "device-001", "192.168.1.100");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("账户已禁用"))
                    .verify();

            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("账户已锁定时抛出异常")
        void testIssueTokenUserLocked() {
            // Given - 创建锁定用户（通过 lockedUntil 判断，status 仍为 1）
            UserEntity lockedUser = UserEntity.builder()
                    .id(100L)
                    .username("testuser")
                    .tenantId("test-tenant")
                    .passwordHash("hashed-password")
                    .status(1) // status=1 保持正常，通过 lockedUntil 触发锁定
                    .failedLoginAttempts(5)
                    .lockedUntil(LocalDateTime.now().plusMinutes(30))
                    .build();

            when(userRepository.findById(100L)).thenReturn(Mono.just(lockedUser));

            // When
            Mono<TokenPair> result = tokenService.issueToken(100L, "test-tenant", "device-001", "192.168.1.100");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("账户已锁定"))
                    .verify();

            verify(tokenRepository, never()).save(any());
        }

        @Test
        @DisplayName("保存的 Token 实体包含正确的字段")
        void testIssueTokenSavesCorrectEntities() {
            // Given
            ArgumentCaptor<TokenEntity> entityCaptor = ArgumentCaptor.forClass(TokenEntity.class);
            when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));
            when(tokenRepository.save(entityCaptor.capture()))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(new TokenEncryptionService.EncryptedToken("encrypted-data", "test-iv"));
            when(encryptionService.hashToken(anyString())).thenReturn("token-hash");
            when(auditService.logTokenIssued(anyLong(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.generateToken(anyString(), anyMap(), anyLong()))
                        .thenReturn("mocked-jwt-token");

                // When
                tokenService
                        .issueToken(100L, "test-tenant", "device-001", "192.168.1.100")
                        .block();

                // Then
                var savedTokens = entityCaptor.getAllValues();
                assertThat(savedTokens).hasSize(2);

                TokenEntity accessToken = savedTokens.get(0);
                assertThat(accessToken.getTokenType()).isEqualTo("ACCESS");
                assertThat(accessToken.getUserId()).isEqualTo(100L);
                assertThat(accessToken.getTenantId()).isEqualTo("test-tenant");
                assertThat(accessToken.getDeviceId()).isEqualTo("device-001");
                assertThat(accessToken.getClientIp()).isEqualTo("192.168.1.100");
                assertThat(accessToken.getJti()).isNotNull();

                TokenEntity refreshToken = savedTokens.get(1);
                assertThat(refreshToken.getTokenType()).isEqualTo("REFRESH");
                assertThat(refreshToken.getUserId()).isEqualTo(100L);
                assertThat(refreshToken.getTenantId()).isEqualTo("test-tenant");
            }
        }
    }

    @Nested
    @DisplayName("validateToken() 方法测试")
    class ValidateTokenTest {

        @Test
        @DisplayName("有效 Token 验证通过")
        void testValidateTokenSuccess() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", "access-jti-123");
            claims.put("userId", 100L);
            claims.put("tenantId", "test-tenant");
            claims.put("tokenType", "ACCESS");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("valid-token")).thenReturn(new DefaultClaims(claims));

                when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));

                // When
                Mono<TokenEntity> result = tokenService.validateToken("valid-token");

                // Then
                StepVerifier.create(result)
                        .assertNext(token -> {
                            assertThat(token.getJti()).isEqualTo("access-jti-123");
                            assertThat(token.getUserId()).isEqualTo(100L);
                        })
                        .verifyComplete();
            }
        }

        @Test
        @DisplayName("Token 声明不完整时抛出异常")
        void testValidateTokenIncompleteClaims() {
            // Given - 缺少 jti
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 100L);
            claims.put("tenantId", "test-tenant");
            claims.put("tokenType", "ACCESS");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("incomplete-token")).thenReturn(new DefaultClaims(claims));

                // When
                Mono<TokenEntity> result = tokenService.validateToken("incomplete-token");

                // Then
                StepVerifier.create(result)
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token声明不完整"))
                        .verify();

                verify(tokenRepository, never()).findByJti(anyString());
            }
        }

        @Test
        @DisplayName("Token 不存在时抛出异常")
        void testValidateTokenNotFound() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", "non-existent-jti");
            claims.put("userId", 100L);
            claims.put("tenantId", "test-tenant");
            claims.put("tokenType", "ACCESS");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("non-existent-token")).thenReturn(new DefaultClaims(claims));

                when(tokenRepository.findByJti("non-existent-jti")).thenReturn(Mono.empty());

                // When
                Mono<TokenEntity> result = tokenService.validateToken("non-existent-token");

                // Then
                StepVerifier.create(result)
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token不存在"))
                        .verify();
            }
        }

        @Test
        @DisplayName("Token 已撤销时抛出异常")
        void testValidateTokenRevoked() {
            // Given
            mockAccessToken.revoke();
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", "access-jti-123");
            claims.put("userId", 100L);
            claims.put("tenantId", "test-tenant");
            claims.put("tokenType", "ACCESS");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("revoked-token")).thenReturn(new DefaultClaims(claims));

                when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));

                // When
                Mono<TokenEntity> result = tokenService.validateToken("revoked-token");

                // Then
                StepVerifier.create(result)
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token已撤销"))
                        .verify();
            }
        }

        @Test
        @DisplayName("Token 已过期时抛出异常（数据库检查）")
        void testValidateTokenExpiredInDb() {
            // Given
            mockAccessToken.setExpiresAt(LocalDateTime.now().minusHours(1));
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", "access-jti-123");
            claims.put("userId", 100L);
            claims.put("tenantId", "test-tenant");
            claims.put("tokenType", "ACCESS");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("expired-token")).thenReturn(new DefaultClaims(claims));

                when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));

                // When
                Mono<TokenEntity> result = tokenService.validateToken("expired-token");

                // Then
                StepVerifier.create(result)
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token已过期"))
                        .verify();
            }
        }

        @Test
        @DisplayName("JWT 解析失败时抛出异常")
        void testValidateTokenJwtException() {
            // Given
            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("malformed-token")).thenThrow(new JwtException("Malformed JWT"));

                // When
                Mono<TokenEntity> result = tokenService.validateToken("malformed-token");

                // Then
                StepVerifier.create(result)
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token无效"))
                        .verify();

                verify(tokenRepository, never()).findByJti(anyString());
            }
        }

        @Test
        @DisplayName("JWT 过期异常时抛出特定错误")
        void testValidateTokenExpiredJwtException() {
            // Given
            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("expired-jwt-token"))
                        .thenThrow(new ExpiredJwtException(null, null, "JWT expired"));

                // When
                Mono<TokenEntity> result = tokenService.validateToken("expired-jwt-token");

                // Then
                StepVerifier.create(result)
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token已过期"))
                        .verify();
            }
        }
    }

    @Nested
    @DisplayName("refreshToken() 方法测试")
    class RefreshTokenTest {

        @Test
        @DisplayName("使用有效刷新令牌成功刷新")
        void testRefreshTokenSuccess() {
            // Given
            Map<String, Object> refreshClaims = new HashMap<>();
            refreshClaims.put("jti", "refresh-jti-456");
            refreshClaims.put("userId", 100L);
            refreshClaims.put("tenantId", "test-tenant");
            refreshClaims.put("tokenType", "REFRESH");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("valid-refresh-token"))
                        .thenReturn(new DefaultClaims(refreshClaims));
                jwtUtil.when(() -> JwtUtil.generateToken(anyString(), anyMap(), anyLong()))
                        .thenReturn("new-mocked-jwt-token");

                when(tokenRepository.findByJti("refresh-jti-456")).thenReturn(Mono.just(mockRefreshToken));
                when(tokenRepository.revokeByJti(eq("refresh-jti-456"), anyString(), anyString()))
                        .thenReturn(Mono.just(true));
                when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));
                when(tokenRepository.save(any(TokenEntity.class)))
                        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
                when(encryptionService.encrypt(anyString()))
                        .thenReturn(new TokenEncryptionService.EncryptedToken("encrypted-data", "test-iv"));
                when(encryptionService.hashToken(anyString())).thenReturn("token-hash");
                when(auditService.logTokenIssued(anyLong(), anyString(), anyString(), anyString(), anyString()))
                        .thenReturn(Mono.empty());
                when(auditService.logTokenRefreshed(anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Mono.empty());

                // When
                Mono<TokenPair> result = tokenService.refreshToken("valid-refresh-token");

                // Then
                StepVerifier.create(result)
                        .assertNext(pair -> {
                            assertThat(pair.getAccessToken()).isNotNull();
                            assertThat(pair.getRefreshToken()).isNotNull();
                            assertThat(pair.getTokenType()).isEqualTo("Bearer");
                        })
                        .verifyComplete();

                verify(tokenRepository).findByJti("refresh-jti-456");
                verify(userRepository).findById(100L);
                verify(tokenRepository, times(2)).save(any(TokenEntity.class));
            }
        }

        @Test
        @DisplayName("使用访问令牌刷新时抛出异常")
        void testRefreshTokenWithAccessToken() {
            // Given
            Map<String, Object> accessClaims = new HashMap<>();
            accessClaims.put("jti", "access-jti-123");
            accessClaims.put("userId", 100L);
            accessClaims.put("tenantId", "test-tenant");
            accessClaims.put("tokenType", "ACCESS");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("access-token")).thenReturn(new DefaultClaims(accessClaims));

                when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));

                // When
                Mono<TokenPair> result = tokenService.refreshToken("access-token");

                // Then
                StepVerifier.create(result)
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("必须使用刷新令牌"))
                        .verify();

                verify(userRepository, never()).findById(anyLong());
            }
        }

        @Test
        @DisplayName("刷新令牌无效时抛出异常")
        void testRefreshTokenInvalid() {
            // Given
            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("invalid-refresh-token"))
                        .thenThrow(new JwtException("Invalid token"));

                // When
                Mono<TokenPair> result = tokenService.refreshToken("invalid-refresh-token");

                // Then
                StepVerifier.create(result)
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token无效"))
                        .verify();

                verify(userRepository, never()).findById(anyLong());
                verify(tokenRepository, never()).save(any());
            }
        }
    }

    @Nested
    @DisplayName("revokeToken() 方法测试")
    class RevokeTokenTest {

        @Test
        @DisplayName("成功撤销 Token")
        void testRevokeTokenSuccess() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", "access-jti-123");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("token-to-revoke")).thenReturn(new DefaultClaims(claims));

                when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));
                when(tokenRepository.update(any(TokenEntity.class)))
                        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
                when(auditService.logTokenRevoked(
                                anyLong(), isNull(), anyString(), anyString(), anyString(), anyString()))
                        .thenReturn(Mono.empty());

                // When
                Mono<Void> result = tokenService.revokeToken("token-to-revoke");

                // Then
                StepVerifier.create(result).verifyComplete();

                verify(tokenRepository).findByJti("access-jti-123");
                verify(tokenRepository).update(argThat(token -> token.isRevoked()));
            }
        }

        @Test
        @DisplayName("Token 不存在时抛出异常")
        void testRevokeTokenNotFound() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", "non-existent-jti");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("non-existent-token")).thenReturn(new DefaultClaims(claims));

                when(tokenRepository.findByJti("non-existent-jti")).thenReturn(Mono.empty());

                // When
                Mono<Void> result = tokenService.revokeToken("non-existent-token");

                // Then
                StepVerifier.create(result)
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token不存在"))
                        .verify();

                verify(tokenRepository, never()).update(any());
            }
        }

        @Test
        @DisplayName("撤销 Token 时调用实体的 revoke 方法")
        void testRevokeTokenCallsEntityMethod() {
            // Given
            ArgumentCaptor<TokenEntity> entityCaptor = ArgumentCaptor.forClass(TokenEntity.class);
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", "access-jti-123");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("token-to-revoke")).thenReturn(new DefaultClaims(claims));

                when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));
                when(tokenRepository.update(entityCaptor.capture()))
                        .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
                when(auditService.logTokenRevoked(
                                anyLong(), isNull(), anyString(), anyString(), anyString(), anyString()))
                        .thenReturn(Mono.empty());

                // When
                tokenService.revokeToken("token-to-revoke").block();

                // Then
                TokenEntity revokedToken = entityCaptor.getValue();
                assertThat(revokedToken.isRevoked()).isTrue();
                assertThat(revokedToken.getRevokedAt()).isNotNull();
            }
        }
    }

    @Nested
    @DisplayName("revokeAllUserTokens() 方法测试")
    class RevokeAllUserTokensTest {

        @Test
        @DisplayName("成功撤销用户的所有 Token")
        void testRevokeAllUserTokensSuccess() {
            // Given
            when(tokenRepository.revokeByUserId(100L, "test-tenant")).thenReturn(Mono.just(true));

            // When
            Mono<Void> result = tokenService.revokeAllUserTokens(100L, "test-tenant");

            // Then
            StepVerifier.create(result).verifyComplete();

            verify(tokenRepository).revokeByUserId(100L, "test-tenant");
        }

        @Test
        @DisplayName("用户无 Token 时也能正常执行")
        void testRevokeAllUserTokensNoTokens() {
            // Given
            when(tokenRepository.revokeByUserId(100L, "test-tenant")).thenReturn(Mono.just(false));

            // When
            Mono<Void> result = tokenService.revokeAllUserTokens(100L, "test-tenant");

            // Then
            StepVerifier.create(result).verifyComplete();

            verify(tokenRepository).revokeByUserId(100L, "test-tenant");
        }
    }

    @Nested
    @DisplayName("extractUserId() 方法测试")
    class ExtractUserIdTest {

        @Test
        @DisplayName("成功从 Token 中提取用户 ID")
        void testExtractUserIdSuccess() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 100L);
            claims.put("jti", "access-jti-123");
            claims.put("tenantId", "test-tenant");
            claims.put("tokenType", "ACCESS");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("valid-token")).thenReturn(new DefaultClaims(claims));

                when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));

                // When
                Mono<Long> result = tokenService.extractUserId("valid-token");

                // Then
                StepVerifier.create(result).expectNext(100L).verifyComplete();
            }
        }

        @Test
        @DisplayName("Token 中缺少用户 ID 时抛出异常")
        void testExtractUserIdMissing() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("tenantId", "test-tenant");
            claims.put("tokenType", "ACCESS");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("token-without-userId")).thenReturn(new DefaultClaims(claims));

                // When
                Mono<Long> result = tokenService.extractUserId("token-without-userId");

                // Then
                StepVerifier.create(result)
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token声明不完整"))
                        .verify();
            }
        }

        @Test
        @DisplayName("Token 解析失败时抛出异常")
        void testExtractUserIdParseFailed() {
            // Given
            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("malformed-token")).thenThrow(new JwtException("Malformed JWT"));

                // When
                Mono<Long> result = tokenService.extractUserId("malformed-token");

                // Then
                StepVerifier.create(result)
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token无效"))
                        .verify();
            }
        }
    }
}
