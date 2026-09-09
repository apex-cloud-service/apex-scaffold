package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.result.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SocialLoginHandler} 的单元测试类
 *
 * <p>测试社交登录处理器的核心逻辑：OIDC 登录发起、社交登录路由、回调处理。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.4.0
 */
@DisplayName("SocialLoginHandler 单元测试")
@ExtendWith(MockitoExtension.class)
class SocialLoginHandlerTest {

    private SocialLoginHandler handler;

    /**
     * 设置测试环境
     */
    @BeforeEach
    void setUp() {
        handler = new SocialLoginHandler();
    }

    @Nested
    @DisplayName("initiateOidcLogin 方法")
    class InitiateOidcLoginTests {

        /**
         * 验证 OIDC 登录发起应重定向到正确的 OAuth2 授权端点
         */
        @Test
        @DisplayName("应重定向到 Keycloak OIDC 授权端点")
        void shouldRedirectToKeycloakOidcEndpoint() {
            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .get("/api/v1/auth/oidc/login")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.initiateOidcLogin(request))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(302);
                        URI location = response.headers().getLocation();
                        assertThat(location).isNotNull();
                        assertThat(location.toString()).isEqualTo("/oauth2/authorization/keycloak");
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("initiateSocialLogin 方法")
    class InitiateSocialLoginTests {

        /**
         * 验证 GitHub 登录应重定向到正确的端点
         */
        @Test
        @DisplayName("应重定向到 GitHub OAuth2 授权端点")
        void shouldRedirectToGitHubEndpoint() {
            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .get("/api/v1/auth/social/github/login")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.initiateSocialLogin(request
                    .attribute("provider", "github")
                    .orElseThrow()))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(302);
                        URI location = response.headers().getLocation();
                        assertThat(location).isNotNull();
                        assertThat(location.toString()).isEqualTo("/oauth2/authorization/github");
                        return true;
                    })
                    .verifyComplete();
        }

        /**
         * 验证 Google 登录应重定向到正确的端点
         */
        @Test
        @DisplayName("应重定向到 Google OAuth2 授权端点")
        void shouldRedirectToGoogleEndpoint() {
            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .get("/api/v1/auth/social/google/login")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.initiateSocialLogin(request
                    .attribute("provider", "google")
                    .orElseThrow()))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(302);
                        URI location = response.headers().getLocation();
                        assertThat(location).isNotNull();
                        assertThat(location.toString()).isEqualTo("/oauth2/authorization/google");
                        return true;
                    })
                    .verifyComplete();
        }

        /**
         * 验证微信登录应重定向到正确的端点
         */
        @Test
        @DisplayName("应重定向到微信 OAuth2 授权端点")
        void shouldRedirectToWeChatEndpoint() {
            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .get("/api/v1/auth/social/wechat/login")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.initiateSocialLogin(request
                    .attribute("provider", "wechat")
                    .orElseThrow()))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(302);
                        URI location = response.headers().getLocation();
                        assertThat(location).isNotNull();
                        assertThat(location.toString()).isEqualTo("/oauth2/authorization/wechat");
                        return true;
                    })
                    .verifyComplete();
        }

        /**
         * 验证不支持的 provider 应返回 400 错误
         */
        @Test
        @DisplayName("不支持的 provider 应返回 400 Bad Request")
        void shouldReturn400ForUnsupportedProvider() {
            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .get("/api/v1/auth/social/unsupported/login")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.initiateSocialLogin(request
                    .attribute("provider", "unsupported")
                    .orElseThrow()))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(400);
                        return true;
                    })
                    .verifyComplete();
        }
    }

    @Nested
    @DisplayName("handleOidcCallback 方法")
    class HandleOidcCallbackTests {

        /**
         * 验证完整的回调参数应返回成功响应
         */
        @Test
        @DisplayName("完整的回调参数应返回 Token 信息")
        void shouldReturnTokenInfoWithCompleteParameters() {
            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .get("/api/v1/auth/oidc/callback")
                    .queryParam("accessToken", "test-access-token")
                    .queryParam("refreshToken", "test-refresh-token")
                    .queryParam("userId", "123")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.handleOidcCallback(request))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(200);
                        return true;
                    })
                    .verifyComplete();
        }

        /**
         * 验证缺少 accessToken 应返回 400 错误
         */
        @Test
        @DisplayName("缺少 accessToken 应返回 400 Bad Request")
        void shouldReturn400WhenMissingAccessToken() {
            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .get("/api/v1/auth/oidc/callback")
                    .queryParam("refreshToken", "test-refresh-token")
                    .queryParam("userId", "123")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.handleOidcCallback(request))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(400);
                        return true;
                    })
                    .verifyComplete();
        }

        /**
         * 验证缺少 refreshToken 应返回 400 错误
         */
        @Test
        @DisplayName("缺少 refreshToken 应返回 400 Bad Request")
        void shouldReturn400WhenMissingRefreshToken() {
            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .get("/api/v1/auth/oidc/callback")
                    .queryParam("accessToken", "test-access-token")
                    .queryParam("userId", "123")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.handleOidcCallback(request))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(400);
                        return true;
                    })
                    .verifyComplete();
        }

        /**
         * 验证缺少 userId 应返回 400 错误
         */
        @Test
        @DisplayName("缺少 userId 应返回 400 Bad Request")
        void shouldReturn400WhenMissingUserId() {
            MockServerHttpRequest httpRequest = MockServerHttpRequest
                    .get("/api/v1/auth/oidc/callback")
                    .queryParam("accessToken", "test-access-token")
                    .queryParam("refreshToken", "test-refresh-token")
                    .build();
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange,
                    HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.handleOidcCallback(request))
                    .expectNextMatches(response -> {
                        assertThat(response.statusCode().value()).isEqualTo(400);
                        return true;
                    })
                    .verifyComplete();
        }
    }
}
