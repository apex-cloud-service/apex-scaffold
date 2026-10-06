package com.apex.scaffold.authcenter.application.service;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.authcenter.domain.model.entity.TokenEntity;
import com.apex.scaffold.authcenter.domain.model.entity.UserEntity;
import com.apex.scaffold.authcenter.domain.repository.TokenRepository;
import com.apex.scaffold.authcenter.domain.repository.UserRepository;
import com.apex.scaffold.authcenter.domain.service.TokenEncryptionService;
import com.apex.scaffold.common.audit.SecurityAuditService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.util.JwtUtil;
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
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

// 说明（2026-09-30）：SecurityAuditService 已由 authcenter 内部下沉至 apex-common，
// 本类仅同步更新 import 路径为 com.apex.scaffold.common.audit.SecurityAuditService，
// 其余测试逻辑与断言保持不变。
@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenRotationService 单元测试")
class RefreshTokenRotationServiceTest {

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenEncryptionService encryptionService;

    @Mock
    private SecurityAuditService auditService;

    @Mock
    private ReactiveRedisTemplate<String, String> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOperations;

    private RefreshTokenRotationService service;

    private UserEntity mockUser;
    private TokenEntity oldRefreshTokenEntity;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenRotationService(
                tokenRepository, userRepository, encryptionService, auditService, redisTemplate);

        mockUser = UserEntity.builder()
                .id(100L)
                .username("testuser")
                .tenantId("test-tenant")
                .build();

        oldRefreshTokenEntity = TokenEntity.builder()
                .id(2L)
                .tenantId("test-tenant")
                .userId(100L)
                .tokenType("REFRESH")
                .jti("old-refresh-jti")
                .tokenFamily("family-1")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .deviceId("device-001")
                .clientIp("192.168.1.100")
                .revoked(0)
                .rotationCount(0)
                .build();
    }

    private Map<String, Object> refreshClaims(String jti) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("jti", jti);
        claims.put("userId", 100L);
        claims.put("tenantId", "test-tenant");
        claims.put("tokenType", "REFRESH");
        return claims;
    }

    @Test
    void invalidTokenLogsExcludeCredentialAndExceptionText() {
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(RefreshTokenRotationService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
            jwtUtil.when(() -> JwtUtil.parseToken("secret-token"))
                    .thenThrow(new JwtException("secret-token untrusted-message"));
            StepVerifier.create(service.rotateRefreshToken("secret-token"))
                    .expectError(BusinessException.class)
                    .verify();
            org.assertj.core.api.Assertions.assertThat(appender.list).isNotEmpty();
            org.assertj.core.api.Assertions.assertThat(appender.list).allSatisfy(event -> {
                org.assertj.core.api.Assertions.assertThat(event.getFormattedMessage())
                        .doesNotContain("secret-token", "untrusted-message");
                org.assertj.core.api.Assertions.assertThat(event.getThrowableProxy())
                        .isNull();
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Nested
    @DisplayName("rotateRefreshToken() 方法测试")
    class RotateRefreshTokenTest {

        @Test
        @DisplayName("非Refresh Token类型直接报错")
        void testRotate_WrongTokenType() {
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", "some-jti");
            claims.put("userId", 100L);
            claims.put("tenantId", "test-tenant");
            claims.put("tokenType", "ACCESS");

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("access-token")).thenReturn(new DefaultClaims(claims));

                StepVerifier.create(service.rotateRefreshToken("access-token"))
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("必须使用Refresh Token"))
                        .verify();
            }
        }

        @Test
        @DisplayName("Token解析失败返回Token无效")
        void testRotate_ParseFailure() {
            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("bad-token")).thenThrow(new JwtException("解析失败"));

                StepVerifier.create(service.rotateRefreshToken("bad-token"))
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token无效"))
                        .verify();
            }
        }

        @Test
        @DisplayName("Token不存在于数据库中")
        void testRotate_TokenNotFound() {
            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("old-refresh-token"))
                        .thenReturn(new DefaultClaims(refreshClaims("missing-jti")));
                when(tokenRepository.findByJti("missing-jti")).thenReturn(Mono.empty());

                StepVerifier.create(service.rotateRefreshToken("old-refresh-token"))
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token不存在"))
                        .verify();
            }
        }

        @Test
        @DisplayName("Token已被撤销")
        void testRotate_TokenRevoked() {
            oldRefreshTokenEntity.setRevoked(1);

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("old-refresh-token"))
                        .thenReturn(new DefaultClaims(refreshClaims("old-refresh-jti")));
                when(tokenRepository.findByJti("old-refresh-jti")).thenReturn(Mono.just(oldRefreshTokenEntity));

                StepVerifier.create(service.rotateRefreshToken("old-refresh-token"))
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token已撤销"))
                        .verify();
            }
        }

        @Test
        @DisplayName("Token已过期")
        void testRotate_TokenExpired() {
            oldRefreshTokenEntity.setExpiresAt(LocalDateTime.now().minusDays(1));

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("old-refresh-token"))
                        .thenReturn(new DefaultClaims(refreshClaims("old-refresh-jti")));
                when(tokenRepository.findByJti("old-refresh-jti")).thenReturn(Mono.just(oldRefreshTokenEntity));

                StepVerifier.create(service.rotateRefreshToken("old-refresh-token"))
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("Token已过期"))
                        .verify();
            }
        }

        @Test
        @DisplayName("检测到Token Family重放攻击")
        void testRotate_FamilyReplayAttack() {
            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("old-refresh-token"))
                        .thenReturn(new DefaultClaims(refreshClaims("old-refresh-jti")));
                when(tokenRepository.findByJti("old-refresh-jti")).thenReturn(Mono.just(oldRefreshTokenEntity));
                when(redisTemplate.hasKey("token:family:revoked:family-1")).thenReturn(Mono.just(true));
                when(auditService.logSecurityEvent(
                                eq("TOKEN_FAMILY_REUSE"), anyString(), anyLong(), isNull(), anyString(), anyString()))
                        .thenReturn(Mono.empty());

                StepVerifier.create(service.rotateRefreshToken("old-refresh-token"))
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("检测到安全威胁"))
                        .verify();

                verify(auditService)
                        .logSecurityEvent(
                                eq("TOKEN_FAMILY_REUSE"), anyString(), anyLong(), isNull(), anyString(), anyString());
            }
        }

        @Test
        @DisplayName("检测到Token重用，撤销整个Family")
        void testRotate_TokenReuseDetected() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("old-refresh-token"))
                        .thenReturn(new DefaultClaims(refreshClaims("old-refresh-jti")));
                when(tokenRepository.findByJti("old-refresh-jti")).thenReturn(Mono.just(oldRefreshTokenEntity));
                when(redisTemplate.hasKey("token:family:revoked:family-1")).thenReturn(Mono.just(false));
                when(valueOperations.setIfAbsent(eq("token:used:old-refresh-jti"), eq("used"), any()))
                        .thenReturn(Mono.just(false));
                when(valueOperations.set(eq("token:family:revoked:family-1"), eq("revoked"), any()))
                        .thenReturn(Mono.empty());
                when(tokenRepository.revokeByTokenFamily("family-1", "100", "token_reuse_detected"))
                        .thenReturn(Mono.just(1));
                when(auditService.logSecurityEvent(
                                eq("TOKEN_REUSE"), anyString(), anyLong(), isNull(), anyString(), anyString()))
                        .thenReturn(Mono.empty());

                StepVerifier.create(service.rotateRefreshToken("old-refresh-token"))
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("检测到Token重用"))
                        .verify();

                verify(tokenRepository).revokeByTokenFamily("family-1", "100", "token_reuse_detected");
            }
        }

        @Test
        @DisplayName("旧Family为空时创建新Family并成功轮换")
        void testRotate_Success_NoExistingFamily() {
            oldRefreshTokenEntity.setTokenFamily(null);

            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq("used"), any())).thenReturn(Mono.just(true));
            when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));
            when(tokenRepository.save(any(TokenEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(new TokenEncryptionService.EncryptedToken("encrypted-data", "test-iv"));
            when(encryptionService.hashToken(anyString())).thenReturn("token-hash");
            when(tokenRepository.update(any(TokenEntity.class))).thenReturn(Mono.just(oldRefreshTokenEntity));
            when(auditService.logTokenRotation(anyLong(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("old-refresh-token"))
                        .thenReturn(new DefaultClaims(refreshClaims("old-refresh-jti")));
                jwtUtil.when(() -> JwtUtil.generateToken(anyString(), anyMap(), anyLong()))
                        .thenReturn("new-jwt-token");
                when(tokenRepository.findByJti("old-refresh-jti")).thenReturn(Mono.just(oldRefreshTokenEntity));
                when(redisTemplate.hasKey(startsWith("token:family:revoked:"))).thenReturn(Mono.just(false));

                StepVerifier.create(service.rotateRefreshToken("old-refresh-token"))
                        .assertNext(pair -> {
                            org.assertj.core.api.Assertions.assertThat(pair.getAccessToken())
                                    .isEqualTo("new-jwt-token");
                            org.assertj.core.api.Assertions.assertThat(pair.getRefreshToken())
                                    .isEqualTo("new-jwt-token");
                            org.assertj.core.api.Assertions.assertThat(pair.getTokenType())
                                    .isEqualTo("Bearer");
                        })
                        .verifyComplete();

                verify(tokenRepository, times(2)).save(any(TokenEntity.class));
                verify(tokenRepository).update(any(TokenEntity.class));
            }
        }

        @Test
        @DisplayName("成功轮换，继承已有Family")
        void testRotate_Success_ExistingFamily() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq("used"), any())).thenReturn(Mono.just(true));
            when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));
            when(tokenRepository.save(any(TokenEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(new TokenEncryptionService.EncryptedToken("encrypted-data", "test-iv"));
            when(encryptionService.hashToken(anyString())).thenReturn("token-hash");
            when(tokenRepository.update(any(TokenEntity.class))).thenReturn(Mono.just(oldRefreshTokenEntity));
            when(auditService.logTokenRotation(anyLong(), anyString(), any(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("old-refresh-token"))
                        .thenReturn(new DefaultClaims(refreshClaims("old-refresh-jti")));
                jwtUtil.when(() -> JwtUtil.generateToken(anyString(), anyMap(), anyLong()))
                        .thenReturn("new-jwt-token");
                when(redisTemplate.hasKey("token:family:revoked:family-1")).thenReturn(Mono.just(false));
                when(tokenRepository.findByJti("old-refresh-jti")).thenReturn(Mono.just(oldRefreshTokenEntity));

                StepVerifier.create(service.rotateRefreshToken("old-refresh-token"))
                        .assertNext(pair -> org.assertj.core.api.Assertions.assertThat(pair.getRefreshTokenJti())
                                .isNotNull())
                        .verifyComplete();
            }
        }

        @Test
        @DisplayName("用户不存在时报错")
        void testRotate_UserNotFound() {
            when(redisTemplate.opsForValue()).thenReturn(valueOperations);
            when(valueOperations.setIfAbsent(anyString(), eq("used"), any())).thenReturn(Mono.just(true));
            when(userRepository.findById(100L)).thenReturn(Mono.empty());

            try (MockedStatic<JwtUtil> jwtUtil = mockStatic(JwtUtil.class)) {
                jwtUtil.when(() -> JwtUtil.parseToken("old-refresh-token"))
                        .thenReturn(new DefaultClaims(refreshClaims("old-refresh-jti")));
                when(redisTemplate.hasKey("token:family:revoked:family-1")).thenReturn(Mono.just(false));
                when(tokenRepository.findByJti("old-refresh-jti")).thenReturn(Mono.just(oldRefreshTokenEntity));

                StepVerifier.create(service.rotateRefreshToken("old-refresh-token"))
                        .expectErrorMatches(e ->
                                e instanceof BusinessException && e.getMessage().contains("用户不存在"))
                        .verify();
            }
        }
    }
}
