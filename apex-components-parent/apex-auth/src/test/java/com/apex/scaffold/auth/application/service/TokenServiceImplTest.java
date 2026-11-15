package com.apex.scaffold.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.domain.model.entity.TokenEntity;
import com.apex.scaffold.auth.domain.model.entity.UserEntity;
import com.apex.scaffold.auth.domain.model.vo.TokenEntityPair;
import com.apex.scaffold.auth.domain.model.vo.TokenPair;
import com.apex.scaffold.auth.domain.model.vo.TokenRotationResult;
import com.apex.scaffold.auth.domain.repository.TokenRepository;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import com.apex.scaffold.auth.domain.service.JwtKeyMaterialService;
import com.apex.scaffold.auth.domain.service.TokenEncryptionService;
import com.apex.scaffold.common.audit.SecurityAuditService;
import com.apex.scaffold.common.exception.BusinessException;
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
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * TokenServiceImpl 单元测试
 *
 * <p>说明（2026-09-30）：{@code SecurityAuditService} 已由 apex-auth 内部下沉至
 * {@code apex-common}，本类仅同步更新其 import 路径为
 * {@code com.apex.scaffold.common.audit.SecurityAuditService}，其余 Mock 依赖与断言逻辑保持不变。
 *
 * <p>说明（2026-10-08）：微服务迁移 Phase 2.1 后，Token 签名/验签算法由对称 HS256
 * （{@code JwtUtil}）升级为非对称 RS256（{@link JwtKeyMaterialService}）。本测试相应
 * 移除 {@code MockedStatic<JwtUtil>} 静态打桩，改为对 {@link JwtKeyMaterialService} 的
 * 响应式方法（{@code sign} / {@code parse}）进行 Mock，断言逻辑保持不变。
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

    /**
     * RS256 密钥材料服务（签发/验签外观层），替代迁移前的 JwtUtil 静态调用。
     */
    @Mock
    private JwtKeyMaterialService jwtKeyMaterialService;

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
            when(tokenRepository.savePair(any(TokenEntityPair.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(new TokenEncryptionService.EncryptedToken("encrypted-data", "test-iv"));
            when(encryptionService.hashToken(anyString())).thenReturn("token-hash");
            when(auditService.logTokenIssued(anyLong(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());
            // RS256 签发：访问令牌与刷新令牌均返回同一模拟值
            when(jwtKeyMaterialService.sign(anyString(), anyMap(), anyLong()))
                    .thenReturn(Mono.just("mocked-jwt-token"));

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
            verify(tokenRepository).savePair(any(TokenEntityPair.class));
            verify(tokenRepository, never()).save(any());
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
            ArgumentCaptor<TokenEntityPair> entityCaptor = ArgumentCaptor.forClass(TokenEntityPair.class);
            when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));
            when(tokenRepository.savePair(entityCaptor.capture()))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(new TokenEncryptionService.EncryptedToken("encrypted-data", "test-iv"));
            when(encryptionService.hashToken(anyString())).thenReturn("token-hash");
            when(auditService.logTokenIssued(anyLong(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());
            when(jwtKeyMaterialService.sign(anyString(), anyMap(), anyLong()))
                    .thenReturn(Mono.just("mocked-jwt-token"));

            // When
            tokenService
                    .issueToken(100L, "test-tenant", "device-001", "192.168.1.100")
                    .block();

            // Then
            TokenEntity accessToken = entityCaptor.getValue().accessToken();
            assertThat(accessToken.getTokenType()).isEqualTo("ACCESS");
            assertThat(accessToken.getUserId()).isEqualTo(100L);
            assertThat(accessToken.getTenantId()).isEqualTo("test-tenant");
            assertThat(accessToken.getDeviceId()).isEqualTo("device-001");
            assertThat(accessToken.getClientIp()).isEqualTo("192.168.1.100");
            assertThat(accessToken.getJti()).isNotNull();

            TokenEntity refreshToken = entityCaptor.getValue().refreshToken();
            assertThat(refreshToken.getTokenType()).isEqualTo("REFRESH");
            assertThat(refreshToken.getUserId()).isEqualTo(100L);
            assertThat(refreshToken.getTenantId()).isEqualTo("test-tenant");
        }
    }

    @Nested
    @DisplayName("issueToken() 统一账户模型测试")
    class IssueTokenUnifiedAccountTest {

        @Test
        @DisplayName("统一账户映射 - claims 携带 accountId/username/clientType")
        void testIssueTokenWithAccountMapping() {
            // Given：用户已与主应用统一账户建立映射（accountId=500）
            UserEntity mappedUser = UserEntity.builder()
                    .id(100L)
                    .username("testuser")
                    .tenantId("test-tenant")
                    .passwordHash("hashed-password")
                    .email("test@example.com")
                    .phone("13800138000")
                    .status(1)
                    .failedLoginAttempts(0)
                    .accountId(500L)
                    .build();
            when(userRepository.findById(100L)).thenReturn(Mono.just(mappedUser));
            when(tokenRepository.savePair(any(TokenEntityPair.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(new TokenEncryptionService.EncryptedToken("encrypted-data", "test-iv"));
            when(encryptionService.hashToken(anyString())).thenReturn("token-hash");
            when(auditService.logTokenIssued(anyLong(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
            when(jwtKeyMaterialService.sign(anyString(), claimsCaptor.capture(), anyLong()))
                    .thenReturn(Mono.just("mocked-jwt-token"));

            // When：携带客户端类型 PC_ADMIN 签发（PC 管理端统一账户链路）
            Mono<TokenPair> result =
                    tokenService.issueToken(100L, "test-tenant", "device-001", "192.168.1.100", "PC_ADMIN");

            // Then
            StepVerifier.create(result)
                    .assertNext(pair -> assertThat(pair.getAccessToken()).isEqualTo("mocked-jwt-token"))
                    .verifyComplete();

            // 首次调用为 ACCESS 令牌 claims
            Map<String, Object> accessClaims = claimsCaptor.getAllValues().get(0);
            assertThat(accessClaims).containsEntry("tokenType", "ACCESS");
            assertThat(accessClaims).containsEntry("accountId", 500L);
            assertThat(accessClaims).containsEntry("username", "testuser");
            assertThat(accessClaims).containsEntry("clientType", "PC_ADMIN");
            assertThat(accessClaims).containsEntry("tenantId", "test-tenant");
            // 刷新令牌 claims 同样携带统一账户字段
            Map<String, Object> refreshClaims = claimsCaptor.getAllValues().get(1);
            assertThat(refreshClaims).containsEntry("accountId", 500L);
        }

        @Test
        @DisplayName("向后兼容 - 未映射用户与未上报 clientType 时 claims 不含对应键")
        void testIssueTokenLegacyClaimsCompatible() {
            // Given：mockUser 未设置 accountId，4 参签名（无 clientType）
            when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));
            when(tokenRepository.savePair(any(TokenEntityPair.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(new TokenEncryptionService.EncryptedToken("encrypted-data", "test-iv"));
            when(encryptionService.hashToken(anyString())).thenReturn("token-hash");
            when(auditService.logTokenIssued(anyLong(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
            when(jwtKeyMaterialService.sign(anyString(), claimsCaptor.capture(), anyLong()))
                    .thenReturn(Mono.just("mocked-jwt-token"));

            Mono<TokenPair> result = tokenService.issueToken(100L, "test-tenant", "device-001", "192.168.1.100");

            StepVerifier.create(result)
                    .assertNext(pair -> assertThat(pair.getAccessToken()).isEqualTo("mocked-jwt-token"))
                    .verifyComplete();

            Map<String, Object> accessClaims = claimsCaptor.getAllValues().get(0);
            assertThat(accessClaims).doesNotContainKey("accountId");
            assertThat(accessClaims).doesNotContainKey("clientType");
            // username 由用户档案携带（主应用侧回退匹配依据）
            assertThat(accessClaims).containsEntry("username", "testuser");
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

            when(jwtKeyMaterialService.parse("valid-token")).thenReturn(Mono.just(new DefaultClaims(claims)));
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

        @Test
        @DisplayName("Token 声明不完整时抛出异常")
        void testValidateTokenIncompleteClaims() {
            // Given - 缺少 jti
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", 100L);
            claims.put("tenantId", "test-tenant");
            claims.put("tokenType", "ACCESS");

            when(jwtKeyMaterialService.parse("incomplete-token")).thenReturn(Mono.just(new DefaultClaims(claims)));

            // When
            Mono<TokenEntity> result = tokenService.validateToken("incomplete-token");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("Token声明不完整"))
                    .verify();

            verify(tokenRepository, never()).findByJti(anyString());
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

            when(jwtKeyMaterialService.parse("non-existent-token")).thenReturn(Mono.just(new DefaultClaims(claims)));
            when(tokenRepository.findByJti("non-existent-jti")).thenReturn(Mono.empty());

            // When
            Mono<TokenEntity> result = tokenService.validateToken("non-existent-token");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("Token不存在"))
                    .verify();
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

            when(jwtKeyMaterialService.parse("revoked-token")).thenReturn(Mono.just(new DefaultClaims(claims)));
            when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));

            // When
            Mono<TokenEntity> result = tokenService.validateToken("revoked-token");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("Token已撤销"))
                    .verify();
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

            when(jwtKeyMaterialService.parse("expired-token")).thenReturn(Mono.just(new DefaultClaims(claims)));
            when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));

            // When
            Mono<TokenEntity> result = tokenService.validateToken("expired-token");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("Token已过期"))
                    .verify();
        }

        @Test
        @DisplayName("JWT 解析失败时抛出异常")
        void testValidateTokenJwtException() {
            // Given - RS256 验签失败以错误信号形式返回
            when(jwtKeyMaterialService.parse("malformed-token"))
                    .thenReturn(Mono.error(new JwtException("Malformed JWT")));

            // When
            Mono<TokenEntity> result = tokenService.validateToken("malformed-token");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("Token无效"))
                    .verify();

            verify(tokenRepository, never()).findByJti(anyString());
        }

        @Test
        @DisplayName("JWT 过期异常时抛出特定错误")
        void testValidateTokenExpiredJwtException() {
            // Given
            when(jwtKeyMaterialService.parse("expired-jwt-token"))
                    .thenReturn(Mono.error(new ExpiredJwtException(null, null, "JWT expired")));

            // When
            Mono<TokenEntity> result = tokenService.validateToken("expired-jwt-token");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("Token已过期"))
                    .verify();
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

            when(jwtKeyMaterialService.parse("valid-refresh-token"))
                    .thenReturn(Mono.just(new DefaultClaims(refreshClaims)));
            when(jwtKeyMaterialService.sign(anyString(), anyMap(), anyLong()))
                    .thenReturn(Mono.just("new-mocked-jwt-token"));

            when(tokenRepository.findByJti("refresh-jti-456")).thenReturn(Mono.just(mockRefreshToken));
            when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));
            when(tokenRepository.rotateRefreshToken(eq(mockRefreshToken), any(TokenEntityPair.class)))
                    .thenAnswer(invocation -> Mono.just(TokenRotationResult.rotated(invocation.getArgument(1))));
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(new TokenEncryptionService.EncryptedToken("encrypted-data", "test-iv"));
            when(encryptionService.hashToken(anyString())).thenReturn("token-hash");
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
            verify(tokenRepository).rotateRefreshToken(eq(mockRefreshToken), any(TokenEntityPair.class));
            verify(tokenRepository, never()).save(any());
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

            when(jwtKeyMaterialService.parse("access-token")).thenReturn(Mono.just(new DefaultClaims(accessClaims)));
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

        @Test
        @DisplayName("刷新令牌无效时抛出异常")
        void testRefreshTokenInvalid() {
            // Given
            when(jwtKeyMaterialService.parse("invalid-refresh-token"))
                    .thenReturn(Mono.error(new JwtException("Invalid token")));

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

    @Nested
    @DisplayName("revokeToken() 方法测试")
    class RevokeTokenTest {

        @Test
        @DisplayName("成功撤销 Token")
        void testRevokeTokenSuccess() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", "access-jti-123");

            when(jwtKeyMaterialService.parse("token-to-revoke")).thenReturn(Mono.just(new DefaultClaims(claims)));
            when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));
            when(tokenRepository.revokeByJti(eq("access-jti-123"), anyString(), eq("manual")))
                    .thenReturn(Mono.just(true));
            when(auditService.logTokenRevoked(anyLong(), isNull(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            // When
            Mono<Void> result = tokenService.revokeToken("token-to-revoke");

            // Then
            StepVerifier.create(result).verifyComplete();

            verify(tokenRepository).findByJti("access-jti-123");
            verify(tokenRepository).revokeByJti("access-jti-123", "user:100", "manual");
        }

        @Test
        @DisplayName("Token 不存在时抛出异常")
        void testRevokeTokenNotFound() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", "non-existent-jti");

            when(jwtKeyMaterialService.parse("non-existent-token")).thenReturn(Mono.just(new DefaultClaims(claims)));
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

        @Test
        @DisplayName("撤销 Token 使用仓储 CAS，不覆盖已有实体状态")
        void testRevokeTokenUsesCasWithoutEntityRewrite() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", "access-jti-123");

            when(jwtKeyMaterialService.parse("token-to-revoke")).thenReturn(Mono.just(new DefaultClaims(claims)));
            when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));
            when(tokenRepository.revokeByJti("access-jti-123", "user:100", "manual"))
                    .thenReturn(Mono.just(true));
            when(auditService.logTokenRevoked(anyLong(), isNull(), anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());

            // When
            tokenService.revokeToken("token-to-revoke").block();

            // Then
            verify(tokenRepository).revokeByJti("access-jti-123", "user:100", "manual");
            verify(tokenRepository, never()).update(any());
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

    @Test
    void consumedRefreshReplayRevokesTenantUserEvenWhenAuditFails() {
        mockRefreshToken.revoke("system", "token_refresh");
        Map<String, Object> claims = new HashMap<>();
        claims.put("jti", mockRefreshToken.getJti());
        claims.put("userId", 100L);
        claims.put("tenantId", "test-tenant");
        claims.put("tokenType", "REFRESH");
        when(jwtKeyMaterialService.parse("consumed-refresh")).thenReturn(Mono.just(new DefaultClaims(claims)));
        when(tokenRepository.findByJti(mockRefreshToken.getJti())).thenReturn(Mono.just(mockRefreshToken));
        lenient()
                .when(auditService.logTokenReplayAttack(anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("audit unavailable")));
        when(tokenRepository.checkRefreshReplay(mockRefreshToken)).thenReturn(Mono.just(TokenRotationResult.replay()));

        StepVerifier.create(tokenService.refreshToken("consumed-refresh"))
                .expectErrorMatches(error -> error instanceof BusinessException)
                .verify();

        verify(tokenRepository).checkRefreshReplay(mockRefreshToken);
        verifyNoInteractions(userRepository, encryptionService);
    }

    @Test
    void signingFailureDoesNotConsumeRefreshToken() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("jti", mockRefreshToken.getJti());
        claims.put("userId", 100L);
        claims.put("tenantId", "test-tenant");
        claims.put("tokenType", "REFRESH");
        when(jwtKeyMaterialService.parse("refresh-sign-failure")).thenReturn(Mono.just(new DefaultClaims(claims)));
        when(tokenRepository.findByJti(mockRefreshToken.getJti())).thenReturn(Mono.just(mockRefreshToken));
        when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));
        when(jwtKeyMaterialService.sign(anyString(), anyMap(), anyLong()))
                .thenReturn(Mono.error(new IllegalStateException("sign unavailable")));
        lenient()
                .when(tokenRepository.revokeByJti(anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(true));

        StepVerifier.create(tokenService.refreshToken("refresh-sign-failure"))
                .expectErrorMessage("sign unavailable")
                .verify();

        verify(tokenRepository, never()).revokeByJti(anyString(), anyString(), anyString());
        verify(tokenRepository, never()).rotateRefreshToken(any(), any());
        verify(tokenRepository, never()).save(any());
    }

    @Nested
    class AtomicRefreshSecurityTest {
        private void refreshClaims(Map<String, Object> overrides) {
            Map<String, Object> claims = new HashMap<>();
            claims.put("jti", mockRefreshToken.getJti());
            claims.put("userId", 100L);
            claims.put("tenantId", "test-tenant");
            claims.put("tokenType", "REFRESH");
            claims.putAll(overrides);
            when(jwtKeyMaterialService.parse("atomic-refresh")).thenReturn(Mono.just(new DefaultClaims(claims)));
            lenient()
                    .when(tokenRepository.findByJti(mockRefreshToken.getJti()))
                    .thenReturn(Mono.just(mockRefreshToken));
        }

        private void prepareRefresh() {
            when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));
            when(jwtKeyMaterialService.sign(anyString(), anyMap(), anyLong()))
                    .thenReturn(Mono.just("signed-new-token"));
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(new TokenEncryptionService.EncryptedToken("cipher", "iv"));
            when(encryptionService.hashToken(anyString())).thenReturn("hash");
        }

        @Test
        void rotationPreservesFamilyChainClientTypeAndCurrentAccountClaims() {
            mockRefreshToken.setTokenFamily("original-family");
            mockRefreshToken.setRotationCount(3);
            mockUser.setAccountId(500L);
            refreshClaims(Map.of("clientType", "PC_ADMIN"));
            prepareRefresh();
            ArgumentCaptor<TokenEntityPair> tokens = ArgumentCaptor.forClass(TokenEntityPair.class);
            when(tokenRepository.rotateRefreshToken(eq(mockRefreshToken), tokens.capture()))
                    .thenAnswer(invocation -> {
                        TokenEntityPair pair = invocation.getArgument(1);
                        pair.accessToken().setId(8L);
                        return Mono.just(TokenRotationResult.rotated(pair));
                    });
            when(auditService.logTokenRefreshed(anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.empty());
            StepVerifier.create(tokenService.refreshToken("atomic-refresh"))
                    .assertNext(pair -> {
                        assertThat(pair.getAccessTokenId()).isEqualTo(8L);
                        assertThat(pair.getRefreshTokenJti())
                                .isEqualTo(tokens.getValue().refreshToken().getJti());
                    })
                    .verifyComplete();
            for (TokenEntity entity : java.util.List.of(
                    tokens.getValue().accessToken(), tokens.getValue().refreshToken())) {
                assertThat(entity.getTokenFamily()).isEqualTo("original-family");
                assertThat(entity.getPreviousJti()).isEqualTo(mockRefreshToken.getJti());
                assertThat(entity.getRotationCount()).isEqualTo(4);
                assertThat(entity.getTokenValue()).isEqualTo("cipher");
            }
            verify(jwtKeyMaterialService, times(2))
                    .sign(
                            eq("100"),
                            argThat(claims -> "PC_ADMIN".equals(claims.get("clientType"))
                                    && Long.valueOf(500).equals(claims.get("accountId"))
                                    && "testuser".equals(claims.get("username"))),
                            anyLong());
            verify(tokenRepository, never()).savePair(any());
            verify(tokenRepository, never()).revokeByJti(anyString(), anyString(), anyString());
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"manual", "logout", "token_refresh", "token_rotated"})
        void repositoryDecidesRevocationClassAfterTrustedVerification(String reason) {
            mockRefreshToken.revoke("system", reason);
            refreshClaims(Map.of());
            boolean consumed = reason.startsWith("token_");
            when(tokenRepository.checkRefreshReplay(mockRefreshToken))
                    .thenReturn(Mono.just(consumed ? TokenRotationResult.replay() : TokenRotationResult.rejected()));
            if (consumed) {
                when(auditService.logTokenReplayAttack(anyLong(), anyString(), anyString(), anyString()))
                        .thenReturn(Mono.empty());
            }
            StepVerifier.create(tokenService.refreshToken("atomic-refresh"))
                    .expectErrorSatisfies(error -> {
                        assertThat(error).isInstanceOf(BusinessException.class);
                        assertThat(((BusinessException) error).getCode()).isEqualTo(401);
                    })
                    .verify();
            verify(tokenRepository).checkRefreshReplay(mockRefreshToken);
            verifyNoInteractions(userRepository, encryptionService);
            verify(jwtKeyMaterialService, never()).sign(anyString(), anyMap(), anyLong());
        }

        @org.junit.jupiter.params.ParameterizedTest
        @org.junit.jupiter.params.provider.ValueSource(strings = {"tenantId", "userId", "tokenType"})
        void mismatchedSignedClaimsCannotTriggerReplayRevocation(String field) {
            mockRefreshToken.revoke("system", "token_refresh");
            refreshClaims(Map.of(field, field.equals("userId") ? 101L : "mismatch"));
            StepVerifier.create(tokenService.refreshToken("atomic-refresh"))
                    .expectError(BusinessException.class)
                    .verify();
            verify(tokenRepository, never()).checkRefreshReplay(any());
            verify(tokenRepository, never()).rotateRefreshToken(any(), any());
            verifyNoInteractions(auditService);
        }

        @Test
        void persistedTokenHashMismatchCannotTriggerReplayRevocation() {
            mockRefreshToken.setTokenHash("expected-hash");
            mockRefreshToken.revoke("system", "token_refresh");
            refreshClaims(Map.of());
            when(encryptionService.hashToken("atomic-refresh")).thenReturn("wrong-hash");
            StepVerifier.create(tokenService.refreshToken("atomic-refresh"))
                    .expectError(BusinessException.class)
                    .verify();
            verify(tokenRepository, never()).checkRefreshReplay(any());
            verifyNoInteractions(auditService);
        }

        @Test
        void tenantContextConflictCannotTriggerReplayRevocation() {
            mockRefreshToken.revoke("system", "token_refresh");
            refreshClaims(Map.of());
            StepVerifier.create(tokenService
                            .refreshToken("atomic-refresh")
                            .contextWrite(com.apex.scaffold.common.context.TenantContext.withTenant("other-tenant")))
                    .expectError(BusinessException.class)
                    .verify();
            verifyNoInteractions(tokenRepository, auditService);
        }

        @Test
        void encryptionFailureNeverStartsConsumptionTransaction() {
            refreshClaims(Map.of());
            when(userRepository.findById(100L)).thenReturn(Mono.just(mockUser));
            when(jwtKeyMaterialService.sign(anyString(), anyMap(), anyLong())).thenReturn(Mono.just("signed"));
            when(encryptionService.encrypt(anyString())).thenThrow(new IllegalStateException("encrypt unavailable"));
            StepVerifier.create(tokenService.refreshToken("atomic-refresh"))
                    .expectErrorMessage("encrypt unavailable")
                    .verify();
            verify(tokenRepository, never()).rotateRefreshToken(any(), any());
            verifyNoInteractions(auditService);
        }

        @Test
        void auditFailureAfterSuccessfulRotationDoesNotHideTokenPair() {
            refreshClaims(Map.of());
            prepareRefresh();
            when(tokenRepository.rotateRefreshToken(any(), any()))
                    .thenAnswer(invocation -> Mono.just(TokenRotationResult.rotated(invocation.getArgument(1))));
            when(auditService.logTokenRefreshed(anyLong(), anyString(), anyString(), anyString()))
                    .thenThrow(new IllegalStateException("audit unavailable"));
            StepVerifier.create(tokenService.refreshToken("atomic-refresh"))
                    .expectNextCount(1)
                    .verifyComplete();
            org.mockito.InOrder order = inOrder(tokenRepository, auditService);
            order.verify(tokenRepository).findByJti(mockRefreshToken.getJti());
            order.verify(tokenRepository).rotateRefreshToken(any(), any());
            order.verify(auditService).logTokenRefreshed(anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        void auditTimeoutCannotMaskCommittedReplayUnauthorized() {
            mockRefreshToken.revoke("system", "token_refresh");
            refreshClaims(Map.of());
            when(tokenRepository.checkRefreshReplay(mockRefreshToken))
                    .thenReturn(Mono.just(TokenRotationResult.replay()));
            when(auditService.logTokenReplayAttack(anyLong(), anyString(), anyString(), anyString()))
                    .thenReturn(Mono.never());
            StepVerifier.withVirtualTime(() -> tokenService.refreshToken("atomic-refresh"))
                    .thenAwait(java.time.Duration.ofSeconds(3))
                    .expectError(BusinessException.class)
                    .verify();
        }

        @Test
        void repositoryFailureDoesNotLogSuccessOrReturnTokens() {
            refreshClaims(Map.of());
            prepareRefresh();
            when(tokenRepository.rotateRefreshToken(any(), any()))
                    .thenReturn(Mono.error(new IllegalStateException("insert failed")));
            StepVerifier.create(tokenService.refreshToken("atomic-refresh"))
                    .expectErrorMessage("insert failed")
                    .verify();
            verifyNoInteractions(auditService);
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

            when(jwtKeyMaterialService.parse("valid-token")).thenReturn(Mono.just(new DefaultClaims(claims)));
            when(tokenRepository.findByJti("access-jti-123")).thenReturn(Mono.just(mockAccessToken));

            // When
            Mono<Long> result = tokenService.extractUserId("valid-token");

            // Then
            StepVerifier.create(result).expectNext(100L).verifyComplete();
        }

        @Test
        @DisplayName("Token 中缺少用户 ID 时抛出异常")
        void testExtractUserIdMissing() {
            // Given
            Map<String, Object> claims = new HashMap<>();
            claims.put("tenantId", "test-tenant");
            claims.put("tokenType", "ACCESS");

            when(jwtKeyMaterialService.parse("token-without-userId")).thenReturn(Mono.just(new DefaultClaims(claims)));

            // When
            Mono<Long> result = tokenService.extractUserId("token-without-userId");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("Token声明不完整"))
                    .verify();
        }

        @Test
        @DisplayName("Token 解析失败时抛出异常")
        void testExtractUserIdParseFailed() {
            // Given
            when(jwtKeyMaterialService.parse("malformed-token"))
                    .thenReturn(Mono.error(new JwtException("Malformed JWT")));

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
