package com.apex.scaffold.authservice.interfaces.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.apex.scaffold.auth.application.dto.LoginRequest;
import com.apex.scaffold.auth.application.dto.LoginResponse;
import com.apex.scaffold.auth.application.dto.RefreshTokenRequest;
import com.apex.scaffold.auth.application.service.AuthenticationApplicationService;
import com.apex.scaffold.auth.domain.model.vo.TokenPair;
import com.apex.scaffold.authservice.AuthCenterSliceTestConfiguration;
import com.apex.scaffold.authservice.interfaces.router.AuthenticationRouter;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

/**
 * 认证Handler集成测试
 *
 * <p>测试认证相关的HTTP接口，验证：
 * <ul>
 *   <li>用户登录：用户名密码认证</li>
 *   <li>用户登出：撤销所有Token</li>
 *   <li>Token刷新：使用刷新令牌换取新访问令牌</li>
 *   <li>异常处理：各种错误场景的响应</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2026-09-21
 */
@WebFluxTest(controllers = AuthenticationHandler.class)
@Import({AuthenticationHandler.class, AuthenticationRouter.class, AuthCenterSliceTestConfiguration.class})
@DisplayName("认证Handler集成测试")
@WithMockUser
class AuthenticationHandlerIntegrationTest {

    /**
     * {@code WebTestClient} 在 {@code @WebFluxTest} 中无法通过构造器解析，
     * 故采用 JSR-250 {@code @Resource} 字段注入。
     */
    @Resource
    private WebTestClient webTestClient;

    @MockBean
    private AuthenticationApplicationService authenticationService;

    private LoginRequest loginRequest;
    private LoginResponse loginResponse;
    private TokenPair tokenPair;
    private RefreshTokenRequest refreshTokenRequest;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest();
        loginRequest.setUsername("testuser");
        loginRequest.setPassword("password123");
        loginRequest.setTenantId("test-tenant");
        loginRequest.setDeviceId("device-001");

        loginResponse = LoginResponse.builder()
                .userId(100L)
                .username("testuser")
                .tenantId("test-tenant")
                .accessToken("access-token-123")
                .refreshToken("refresh-token-123")
                .expiresIn(7200L)
                .tokenType("Bearer")
                .build();

        tokenPair = TokenPair.builder()
                .accessToken("new-access-token")
                .refreshToken("new-refresh-token")
                .expiresIn(7200L)
                .tokenType("Bearer")
                .build();

        refreshTokenRequest = new RefreshTokenRequest();
        refreshTokenRequest.setRefreshToken("refresh-token-123");
    }

    @Nested
    @DisplayName("POST /auth/v1/login - 用户登录")
    class LoginTest {

        @Test
        @DisplayName("登录成功返回200和Token信息")
        void testLoginSuccess() {
            when(authenticationService.login(any(LoginRequest.class), anyString()))
                    .thenReturn(Mono.just(loginResponse));

            webTestClient
                    .mutateWith(SecurityMockServerConfigurers.csrf())
                    .post()
                    .uri("/auth/v1/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(loginRequest)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.userId")
                    .isEqualTo(100)
                    .jsonPath("$.username")
                    .isEqualTo("testuser")
                    .jsonPath("$.tenantId")
                    .isEqualTo("test-tenant")
                    .jsonPath("$.accessToken")
                    .isEqualTo("access-token-123")
                    .jsonPath("$.refreshToken")
                    .isEqualTo("refresh-token-123")
                    .jsonPath("$.expiresIn")
                    .isEqualTo(7200)
                    .jsonPath("$.tokenType")
                    .isEqualTo("Bearer");
        }

        @Test
        @DisplayName("用户名或密码错误返回401")
        void testLoginUnauthorized() {
            when(authenticationService.login(any(LoginRequest.class), anyString()))
                    .thenReturn(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED)));

            webTestClient
                    .mutateWith(SecurityMockServerConfigurers.csrf())
                    .post()
                    .uri("/auth/v1/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(loginRequest)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized()
                    .expectBody(String.class)
                    .isEqualTo(new BusinessException(ResultCode.UNAUTHORIZED).getMessage());
        }

        @Test
        @DisplayName("账户被锁定返回403")
        void testLoginForbidden() {
            when(authenticationService.login(any(LoginRequest.class), anyString()))
                    .thenReturn(Mono.error(new BusinessException(ResultCode.FORBIDDEN)));

            webTestClient
                    .mutateWith(SecurityMockServerConfigurers.csrf())
                    .post()
                    .uri("/auth/v1/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(loginRequest)
                    .exchange()
                    .expectStatus()
                    .isForbidden()
                    .expectBody(String.class)
                    .isEqualTo(new BusinessException(ResultCode.FORBIDDEN).getMessage());
        }

        @Test
        @DisplayName("系统错误返回500")
        void testLoginSystemError() {
            when(authenticationService.login(any(LoginRequest.class), anyString()))
                    .thenReturn(Mono.error(new RuntimeException("Database error")));

            webTestClient
                    .mutateWith(SecurityMockServerConfigurers.csrf())
                    .post()
                    .uri("/auth/v1/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(loginRequest)
                    .exchange()
                    .expectStatus()
                    .is5xxServerError()
                    .expectBody(String.class)
                    .isEqualTo("系统错误");
        }
    }

    @Nested
    @DisplayName("POST /auth/v1/logout/{userId} - 用户登出")
    class LogoutTest {
        @BeforeEach
        void authenticatedSubject() {
            org.springframework.security.authentication.UsernamePasswordAuthenticationToken principal =
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "100", "", java.util.List.of());
            principal.setDetails("test-tenant");
            webTestClient = webTestClient.mutateWith(SecurityMockServerConfigurers.mockAuthentication(principal));
        }

        @Test
        @DisplayName("登出成功返回200")
        void testLogoutSuccess() {
            when(authenticationService.logout(eq(100L), eq("test-tenant"))).thenReturn(Mono.empty());

            webTestClient
                    .mutateWith(SecurityMockServerConfigurers.csrf())
                    .post()
                    .uri("/auth/v1/logout/100?tenantId=test-tenant")
                    .exchange()
                    .expectStatus()
                    .isOk();
        }

        @Test
        @DisplayName("路径用户与安全主体不一致返回403")
        void testLogoutInvalidUserId() {
            webTestClient
                    .mutateWith(SecurityMockServerConfigurers.csrf())
                    .post()
                    .uri("/auth/v1/logout/invalid?tenantId=test-tenant")
                    .exchange()
                    .expectStatus()
                    .isForbidden();
        }

        @Test
        @DisplayName("登出失败返回500")
        void testLogoutFailure() {
            when(authenticationService.logout(eq(100L), eq("test-tenant")))
                    .thenReturn(Mono.error(new RuntimeException("Token revocation failed")));

            webTestClient
                    .mutateWith(SecurityMockServerConfigurers.csrf())
                    .post()
                    .uri("/auth/v1/logout/100?tenantId=test-tenant")
                    .exchange()
                    .expectStatus()
                    .is5xxServerError();
        }

        @Test
        @DisplayName("未提供tenantId时使用安全主体租户")
        void testLogoutDefaultTenant() {
            when(authenticationService.logout(eq(100L), eq("test-tenant"))).thenReturn(Mono.empty());

            webTestClient
                    .mutateWith(SecurityMockServerConfigurers.csrf())
                    .post()
                    .uri("/auth/v1/logout/100")
                    .exchange()
                    .expectStatus()
                    .isOk();
        }
    }

    @Nested
    @DisplayName("POST /auth/v1/refresh - Token刷新")
    class RefreshTokenTest {

        @Test
        @DisplayName("刷新成功返回200和新Token")
        void testRefreshSuccess() {
            when(authenticationService.refreshToken(eq("refresh-token-123"))).thenReturn(Mono.just(tokenPair));

            webTestClient
                    .mutateWith(SecurityMockServerConfigurers.csrf())
                    .post()
                    .uri("/auth/v1/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(refreshTokenRequest)
                    .exchange()
                    .expectStatus()
                    .isOk()
                    .expectBody()
                    .jsonPath("$.accessToken")
                    .isEqualTo("new-access-token")
                    .jsonPath("$.refreshToken")
                    .isEqualTo("new-refresh-token")
                    .jsonPath("$.expiresIn")
                    .isEqualTo(7200)
                    .jsonPath("$.tokenType")
                    .isEqualTo("Bearer");
        }

        @Test
        @DisplayName("刷新令牌无效返回401")
        void testRefreshInvalidToken() {
            when(authenticationService.refreshToken(anyString()))
                    .thenReturn(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED)));

            webTestClient
                    .mutateWith(SecurityMockServerConfigurers.csrf())
                    .post()
                    .uri("/auth/v1/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(refreshTokenRequest)
                    .exchange()
                    .expectStatus()
                    .isUnauthorized()
                    .expectBody(String.class)
                    .isEqualTo("刷新令牌无效或已过期");
        }

        @Test
        @DisplayName("系统错误返回500")
        void testRefreshSystemError() {
            when(authenticationService.refreshToken(anyString()))
                    .thenReturn(Mono.error(new RuntimeException("System error")));

            webTestClient
                    .mutateWith(SecurityMockServerConfigurers.csrf())
                    .post()
                    .uri("/auth/v1/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(refreshTokenRequest)
                    .exchange()
                    .expectStatus()
                    .is5xxServerError()
                    .expectBody(String.class)
                    .isEqualTo("刷新失败");
        }
    }

    @Test
    void testLoginPassesRemoteAddressAndRequestFields() {
        when(authenticationService.login(any(LoginRequest.class), eq("192.0.2.10")))
                .thenReturn(Mono.just(loginResponse));
        WebTestClient client = WebTestClient.bindToRouterFunction(
                        new AuthenticationRouter(new AuthenticationHandler(authenticationService))
                                .authenticationRoutes())
                .webFilter((exchange, chain) -> chain.filter(exchange.mutate()
                        .request(exchange.getRequest()
                                .mutate()
                                .remoteAddress(new java.net.InetSocketAddress("192.0.2.10", 12345))
                                .build())
                        .build()))
                .build();
        client.post()
                .uri("/auth/v1/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(loginRequest)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.accessToken")
                .isEqualTo("access-token-123");
        org.mockito.Mockito.verify(authenticationService)
                .login(
                        org.mockito.ArgumentMatchers.argThat(request -> "testuser".equals(request.getUsername())
                                && "password123".equals(request.getPassword())
                                && "test-tenant".equals(request.getTenantId())
                                && "device-001".equals(request.getDeviceId())),
                        eq("192.0.2.10"));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"login,系统错误", "refresh,刷新失败"})
    void testMalformedJsonDoesNotCallService(String endpoint, String message) {
        webTestClient
                .mutateWith(SecurityMockServerConfigurers.csrf())
                .post()
                .uri("/auth/v1/" + endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{")
                .exchange()
                .expectStatus()
                .isEqualTo(500)
                .expectBody(String.class)
                .isEqualTo(message);
        org.mockito.Mockito.verifyNoInteractions(authenticationService);
    }
}
