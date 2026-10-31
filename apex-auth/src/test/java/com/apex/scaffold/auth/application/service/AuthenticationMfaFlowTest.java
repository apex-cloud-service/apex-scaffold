package com.apex.scaffold.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.application.dto.LoginRequest;
import com.apex.scaffold.auth.application.dto.LoginResponse;
import com.apex.scaffold.auth.domain.model.entity.UserEntity;
import com.apex.scaffold.auth.domain.model.vo.TokenPair;
import com.apex.scaffold.auth.domain.repository.UserRepository;
import com.apex.scaffold.auth.domain.service.JwtKeyMaterialService;
import com.apex.scaffold.auth.domain.service.TokenService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 登录 MFA 二步流程与 会话登记 测试（统一认证收口 A4）。
 *
 * <p>覆盖：MFA 启用用户登录返回挑战凭据且不签发正式令牌；挑战凭据 + 验证码交换正式
 * 令牌并登记会话；验证码错误拒绝；登出终止在线会话。密钥材料使用真实 RSA-2048。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
class AuthenticationMfaFlowTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final TokenService tokenService = mock(TokenService.class);
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder =
            mock(org.springframework.security.crypto.password.PasswordEncoder.class);
    private final DomainEventPublisher domainEventPublisher = mock(DomainEventPublisher.class);
    private final TotpService totpService = mock(TotpService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final com.apex.scaffold.auth.domain.service.TenantStatusPort tenantStatusPort =
            mock(com.apex.scaffold.auth.domain.service.TenantStatusPort.class);

    private final KeyPair keyPair = Jwts.SIG.RS256.keyPair().build();
    private final JwtKeyMaterialService keyMaterialService = keyMaterialService();

    private AuthenticationApplicationService service() {
        return new AuthenticationApplicationService(
                userRepository,
                tokenService,
                passwordEncoder,
                domainEventPublisher,
                totpService,
                sessionService,
                keyMaterialService,
                tenantStatusPort);
    }

    /** 真实 RS256 密钥材料服务（轮换服务 Mockito 打桩，范式同 JwtKeyMaterialServiceTest）。 */
    private JwtKeyMaterialService keyMaterialService() {
        com.apex.scaffold.auth.domain.service.JwtKeyRotationService rotation =
                org.mockito.Mockito.mock(com.apex.scaffold.auth.domain.service.JwtKeyRotationService.class);
        org.mockito.Mockito.when(rotation.getCurrentKeyId()).thenReturn(Mono.just("key-test-0001"));
        org.mockito.Mockito.when(rotation.getCurrentPrivateKey()).thenReturn(Mono.just(keyPair.getPrivate()));
        org.mockito.Mockito.when(rotation.getPublicKey(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.just(keyPair.getPublic()));
        return new JwtKeyMaterialService(rotation);
    }

    private UserEntity activeUser() {
        UserEntity user = UserEntity.builder()
                .id(95302L)
                .tenantId("default")
                .username("mfa-user")
                .passwordHash("$2a$10$stub")
                .status(1)
                .build();
        return user;
    }

    private LoginRequest loginRequest() {
        LoginRequest request = new LoginRequest();
        request.setUsername("mfa-user");
        request.setPassword("secret");
        request.setTenantId("default");
        return request;
    }

    @org.junit.jupiter.api.BeforeEach
    void stubTenantGate() {
        org.mockito.Mockito.lenient()
                .when(tenantStatusPort.validateLoginTenant(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.empty());
    }

    /** 统一认证收口（A6）：租户门拒绝时登录在用户查询前终止。 */
    @org.junit.jupiter.api.Test
    void loginRejectsBeforeUserLookupWhenTenantGateRejects() {
        org.mockito.Mockito.when(tenantStatusPort.validateLoginTenant("frozen-tenant"))
                .thenReturn(Mono.error(
                        new BusinessException(com.apex.scaffold.common.result.ResultCode.UNAUTHORIZED, "租户不可用")));
        LoginRequest request = loginRequest();
        request.setTenantId("frozen-tenant");
        StepVerifier.create(service().login(request, "127.0.0.1"))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(userRepository, tokenService);
    }

    private void stubSuccessfulPasswordLogin() {
        when(userRepository.findByUsername("mfa-user", "default")).thenReturn(Mono.just(activeUser()));
        when(passwordEncoder.matches("secret", "$2a$10$stub")).thenReturn(true);
        when(userRepository.update(any(UserEntity.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    }

    @org.junit.jupiter.api.Test
    void loginReturnsMfaChallengeWithoutIssuingTokens() {
        stubSuccessfulPasswordLogin();
        when(totpService.isEnabled(95302L, "default")).thenReturn(Mono.just(Boolean.TRUE));

        StepVerifier.create(service().login(loginRequest(), "127.0.0.1"))
                .assertNext(response -> {
                    assertThat(response.getMfaRequired()).isTrue();
                    assertThat(response.getChallengeToken()).isNotBlank();
                    assertThat(response.getAccessToken()).isNull();
                })
                .verifyComplete();
        verifyNoInteractions(tokenService, sessionService);
    }

    @org.junit.jupiter.api.Test
    void loginIssuesTokensAndRegistersSessionWhenMfaDisabled() {
        stubSuccessfulPasswordLogin();
        when(totpService.isEnabled(95302L, "default")).thenReturn(Mono.just(Boolean.FALSE));
        TokenPair pair = TokenPair.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .accessTokenId(88L)
                .expiresIn(7200_000L)
                .tokenType("Bearer")
                .build();
        when(tokenService.issueToken(95302L, "default", null, "127.0.0.1")).thenReturn(Mono.just(pair));
        when(sessionService.createSession(
                        anyLong(), anyString(), anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service().login(loginRequest(), "127.0.0.1"))
                .assertNext(response -> {
                    assertThat(response.getAccessToken()).isEqualTo("access-token");
                    assertThat(response.getMfaRequired()).isNull();
                })
                .verifyComplete();
        verify(sessionService).createSession(95302L, "default", 88L, "UNKNOWN", "unknown", "127.0.0.1", null);
    }

    @org.junit.jupiter.api.Test
    void verifyMfaChallengeExchangesChallengeForTokens() {
        stubSuccessfulPasswordLogin();
        when(totpService.isEnabled(95302L, "default")).thenReturn(Mono.just(Boolean.TRUE));
        LoginResponse challenge = service().login(loginRequest(), "127.0.0.1").block();

        when(totpService.verifyCode(95302L, "123456", "default")).thenReturn(Mono.just(Boolean.TRUE));
        when(userRepository.findById(95302L)).thenReturn(Mono.just(activeUser()));
        TokenPair pair = TokenPair.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .accessTokenId(88L)
                .expiresIn(7200_000L)
                .tokenType("Bearer")
                .build();
        when(tokenService.issueToken(95302L, "default", "", "127.0.0.1")).thenReturn(Mono.just(pair));
        when(sessionService.createSession(
                        anyLong(), anyString(), anyLong(), anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service().verifyMfaChallenge(challenge.getChallengeToken(), "123456", "127.0.0.1"))
                .assertNext(response -> {
                    assertThat(response.getAccessToken()).isEqualTo("access-token");
                    assertThat(response.getUserId()).isEqualTo(95302L);
                })
                .verifyComplete();
        verify(sessionService).createSession(95302L, "default", 88L, "UNKNOWN", "unknown", "127.0.0.1", null);
    }

    @org.junit.jupiter.api.Test
    void verifyMfaChallengeRejectsWrongCode() {
        stubSuccessfulPasswordLogin();
        when(totpService.isEnabled(95302L, "default")).thenReturn(Mono.just(Boolean.TRUE));
        LoginResponse challenge = service().login(loginRequest(), "127.0.0.1").block();

        when(totpService.verifyCode(95302L, "000000", "default")).thenReturn(Mono.just(Boolean.FALSE));

        StepVerifier.create(service().verifyMfaChallenge(challenge.getChallengeToken(), "000000", "127.0.0.1"))
                .expectError(BusinessException.class)
                .verify();
        verifyNoInteractions(tokenService, sessionService);
    }

    @org.junit.jupiter.api.Test
    void logoutTerminatesUserSessions() {
        when(tokenService.revokeAllUserTokens(95302L, "default")).thenReturn(Mono.empty());
        when(domainEventPublisher.publish(any())).thenReturn(Mono.empty());
        when(sessionService.terminateUserSessions(95302L, "default")).thenReturn(Mono.empty());

        StepVerifier.create(service().logout(95302L, "default")).verifyComplete();
        verify(sessionService).terminateUserSessions(95302L, "default");
    }
}
