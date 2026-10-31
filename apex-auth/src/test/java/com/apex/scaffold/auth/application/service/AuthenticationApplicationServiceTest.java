package com.apex.scaffold.auth.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.auth.application.dto.LoginRequest;
import com.apex.scaffold.auth.application.dto.LoginResponse;
import com.apex.scaffold.auth.domain.model.entity.UserEntity;
import com.apex.scaffold.auth.domain.model.vo.TokenPair;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.event.TokenRevokedEvent;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * AuthenticationApplicationService 单元测试
 *
 * <p>测试认证应用服务的核心功能：
 * <ul>
 *   <li>用户登录认证</li>
 *   <li>用户登出</li>
 *   <li>Token 刷新</li>
 * </ul>
 *
 * @author Apex Scaffold
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("认证应用服务单元测试")
class AuthenticationApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenService tokenService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @Mock
    private TotpService totpService;

    @Mock
    private SessionService sessionService;

    @Mock
    private com.apex.scaffold.auth.domain.service.JwtKeyMaterialService jwtKeyMaterialService;

    @Mock
    private com.apex.scaffold.auth.domain.service.TenantStatusPort tenantStatusPort;

    @InjectMocks
    private AuthenticationApplicationService authenticationService;

    private UserEntity mockUser;
    private TokenPair mockTokenPair;

    @BeforeEach
    void setUp() {
        mockUser = UserEntity.builder()
                .id(100L)
                .username("testuser")
                .tenantId("test-tenant")
                .passwordHash("$2a$10$hashed-password")
                .status(1)
                .failedLoginAttempts(0)
                .build();

        // 统一认证收口（A4）：登录链现含 MFA 判定与登出会话终止，缺省桩保持既有用例语义
        org.mockito.Mockito.lenient()
                .when(totpService.isEnabled(
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.just(Boolean.FALSE));
        org.mockito.Mockito.lenient()
                .when(sessionService.terminateUserSessions(
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.empty());
        org.mockito.Mockito.lenient()
                .when(tenantStatusPort.validateLoginTenant(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.empty());

        mockTokenPair = TokenPair.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .expiresIn(7200L) // 使用 Long 类型
                .tokenType("Bearer")
                .build();
    }

    @Nested
    @DisplayName("login() 方法测试")
    class LoginTest {

        @Test
        @DisplayName("用户名密码正确时登录成功")
        void testLoginSuccess() {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("correct-password");
            request.setTenantId("test-tenant");
            request.setDeviceId("device-001"); // 设置 deviceId

            when(userRepository.findByUsername("testuser", "test-tenant")).thenReturn(Mono.just(mockUser));
            when(passwordEncoder.matches("correct-password", "$2a$10$hashed-password"))
                    .thenReturn(true);
            when(userRepository.update(any(UserEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
            when(tokenService.issueToken(eq(100L), eq("test-tenant"), eq("device-001"), eq("192.168.1.1")))
                    .thenReturn(Mono.just(mockTokenPair));

            // When
            Mono<LoginResponse> result = authenticationService.login(request, "192.168.1.1");

            // Then
            StepVerifier.create(result)
                    .assertNext(response -> {
                        assertThat(response.getUserId()).isEqualTo(100L);
                        assertThat(response.getUsername()).isEqualTo("testuser");
                        assertThat(response.getTenantId()).isEqualTo("test-tenant");
                        assertThat(response.getAccessToken()).isEqualTo("access-token");
                        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
                    })
                    .verifyComplete();

            verify(userRepository).findByUsername("testuser", "test-tenant");
            verify(passwordEncoder).matches("correct-password", "$2a$10$hashed-password");
            verify(userRepository).update(any(UserEntity.class));
            verify(tokenService).issueToken(eq(100L), eq("test-tenant"), eq("device-001"), eq("192.168.1.1"));
        }

        @Test
        @DisplayName("密码错误时返回错误")
        void testLoginWrongPassword() {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("wrong-password");
            request.setTenantId("test-tenant");

            when(userRepository.findByUsername("testuser", "test-tenant")).thenReturn(Mono.just(mockUser));
            when(passwordEncoder.matches("wrong-password", "$2a$10$hashed-password"))
                    .thenReturn(false);
            when(userRepository.update(any(UserEntity.class)))
                    .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

            // When
            Mono<LoginResponse> result = authenticationService.login(request, "192.168.1.1");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("用户名或密码错误"))
                    .verify();

            verify(userRepository).findByUsername("testuser", "test-tenant");
            verify(passwordEncoder).matches("wrong-password", "$2a$10$hashed-password");
            verify(userRepository).update(any(UserEntity.class)); // 记录失败次数
            verify(tokenService, never()).issueToken(anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("用户不存在时返回错误")
        void testLoginUserNotFound() {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("nonexistent");
            request.setPassword("password");
            request.setTenantId("test-tenant");

            when(userRepository.findByUsername("nonexistent", "test-tenant")).thenReturn(Mono.empty());

            // When
            Mono<LoginResponse> result = authenticationService.login(request, "192.168.1.1");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("用户名或密码错误"))
                    .verify();

            verify(userRepository).findByUsername("nonexistent", "test-tenant");
            verify(tokenService, never()).issueToken(anyLong(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("租户不匹配时返回错误")
        void testLoginTenantMismatch() {
            // Given
            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password");
            request.setTenantId("wrong-tenant");

            when(userRepository.findByUsername("testuser", "wrong-tenant")).thenReturn(Mono.empty());

            // When
            Mono<LoginResponse> result = authenticationService.login(request, "192.168.1.1");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("用户名或密码错误"))
                    .verify();

            verify(userRepository).findByUsername("testuser", "wrong-tenant");
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("账户被禁用时返回错误")
        void testLoginUserDisabled() {
            // Given
            UserEntity disabledUser = UserEntity.builder()
                    .id(100L)
                    .username("testuser")
                    .tenantId("test-tenant")
                    .passwordHash("$2a$10$hashed-password")
                    .status(0) // 禁用状态
                    .build();

            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password");
            request.setTenantId("test-tenant");

            when(userRepository.findByUsername("testuser", "test-tenant")).thenReturn(Mono.just(disabledUser));

            // When
            Mono<LoginResponse> result = authenticationService.login(request, "192.168.1.1");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("账户已禁用"))
                    .verify();

            verify(userRepository).findByUsername("testuser", "test-tenant");
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("账户被锁定时返回错误")
        void testLoginUserLocked() {
            // Given
            UserEntity lockedUser = UserEntity.builder()
                    .id(100L)
                    .username("testuser")
                    .tenantId("test-tenant")
                    .passwordHash("$2a$10$hashed-password")
                    .status(1)
                    .failedLoginAttempts(5)
                    .lockedUntil(LocalDateTime.now().plusMinutes(30))
                    .build();

            LoginRequest request = new LoginRequest();
            request.setUsername("testuser");
            request.setPassword("password");
            request.setTenantId("test-tenant");

            when(userRepository.findByUsername("testuser", "test-tenant")).thenReturn(Mono.just(lockedUser));

            // When
            Mono<LoginResponse> result = authenticationService.login(request, "192.168.1.1");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("账户已锁定"))
                    .verify();

            verify(userRepository).findByUsername("testuser", "test-tenant");
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("logout() 方法测试")
    class LogoutTest {

        @Test
        @DisplayName("成功撤销用户所有 Token 并发布令牌撤销事件")
        void testLogoutSuccess() {
            // Given
            when(tokenService.revokeAllUserTokens(100L, "test-tenant")).thenReturn(Mono.empty());
            when(domainEventPublisher.publish(any(TokenRevokedEvent.class))).thenReturn(Mono.empty());

            // When
            Mono<Void> result = authenticationService.logout(100L, "test-tenant");

            // Then
            StepVerifier.create(result).verifyComplete();

            verify(tokenService).revokeAllUserTokens(100L, "test-tenant");
            // 登出成功必须发布 TokenRevokedEvent，且负载正确携带 userId/tenantId/原因
            ArgumentCaptor<TokenRevokedEvent> captor = ArgumentCaptor.forClass(TokenRevokedEvent.class);
            verify(domainEventPublisher).publish(captor.capture());
            TokenRevokedEvent published = captor.getValue();
            assertThat(published.getUserId()).isEqualTo(100L);
            assertThat(published.getTenantId()).isEqualTo("test-tenant");
            assertThat(published.getReason()).isEqualTo("LOGOUT");
        }

        @Test
        @DisplayName("Token 撤销失败时传播错误且不发布事件")
        void testLogoutTokenRevokeFailure() {
            // Given
            when(tokenService.revokeAllUserTokens(100L, "test-tenant"))
                    .thenReturn(Mono.error(new RuntimeException("撤销失败")));

            // When
            Mono<Void> result = authenticationService.logout(100L, "test-tenant");

            // Then
            StepVerifier.create(result).expectErrorMessage("撤销失败").verify();

            verify(tokenService).revokeAllUserTokens(100L, "test-tenant");
            // 令牌撤销失败时不得发布令牌撤销事件（延迟构造保证撤销结果先行）
            verify(domainEventPublisher, never()).publish(any());
        }
    }

    @Nested
    @DisplayName("refreshToken() 方法测试")
    class RefreshTokenTest {

        @Test
        @DisplayName("使用有效刷新令牌成功刷新")
        void testRefreshTokenSuccess() {
            // Given
            when(tokenService.refreshToken("valid-refresh-token")).thenReturn(Mono.just(mockTokenPair));

            // When
            Mono<TokenPair> result = authenticationService.refreshToken("valid-refresh-token");

            // Then
            StepVerifier.create(result)
                    .assertNext(pair -> {
                        assertThat(pair.getAccessToken()).isEqualTo("access-token");
                        assertThat(pair.getRefreshToken()).isEqualTo("refresh-token");
                        assertThat(pair.getExpiresIn()).isEqualTo(7200);
                        assertThat(pair.getTokenType()).isEqualTo("Bearer");
                    })
                    .verifyComplete();

            verify(tokenService).refreshToken("valid-refresh-token");
        }

        @Test
        @DisplayName("使用无效刷新令牌时返回错误")
        void testRefreshTokenInvalid() {
            // Given
            when(tokenService.refreshToken("invalid-refresh-token"))
                    .thenReturn(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token无效")));

            // When
            Mono<TokenPair> result = authenticationService.refreshToken("invalid-refresh-token");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("Token无效"))
                    .verify();

            verify(tokenService).refreshToken("invalid-refresh-token");
        }

        @Test
        @DisplayName("使用过期刷新令牌时返回错误")
        void testRefreshTokenExpired() {
            // Given
            when(tokenService.refreshToken("expired-refresh-token"))
                    .thenReturn(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "Token已过期")));

            // When
            Mono<TokenPair> result = authenticationService.refreshToken("expired-refresh-token");

            // Then
            StepVerifier.create(result)
                    .expectErrorMatches(e ->
                            e instanceof BusinessException && e.getMessage().contains("Token已过期"))
                    .verify();

            verify(tokenService).refreshToken("expired-refresh-token");
        }
    }
}
