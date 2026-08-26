package com.apex.scaffold.interfaces.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.dto.RefreshTokenRequest;
import com.apex.scaffold.application.dto.RegisterRequest;
import com.apex.scaffold.application.dto.SendSmsRequest;
import com.apex.scaffold.application.dto.WechatBindPhoneRequest;
import com.apex.scaffold.application.dto.WxPhoneRequest;
import com.apex.scaffold.application.service.AuthRegistrationService;
import com.apex.scaffold.application.service.AuthService;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import java.security.Principal;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link AuthHandler} 的单元测试类
 *
 * <p>测试认证Handler的核心逻辑：参数解析、鉴权、业务调用、响应构建。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("AuthHandler 单元测试")
@ExtendWith(MockitoExtension.class)
class AuthHandlerTest {

    @Mock
    private AuthService authService;

    @Mock
    private AuthRegistrationService authRegistrationService;

    @Mock
    private RequestHandlerUtil requestHandlerUtil;

    @Mock
    private MessageUtils messageUtils;

    @Mock
    private SecurityMetricsRecorder securityMetricsRecorder;

    private static final String TEST_OPEN_ID = "test-open-id-123";

    private AuthHandler handler;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        handler = new AuthHandler(
                authService, authRegistrationService, requestHandlerUtil, messageUtils, securityMetricsRecorder);
    }

    @Nested
    @DisplayName("register 方法")
    class RegisterTests {

        /**
         * 验证 {@code shouldRegisterSuccessfully} 场景。
         */
        @Test
        @DisplayName("应传递客户端IP并完成注册")
        void shouldRegisterSuccessfully() {
            RegisterRequest registerRequest = new RegisterRequest();
            LoginResponse loginResponse = new LoginResponse();
            loginResponse.setNextAction("AUTHENTICATED");
            when(requestHandlerUtil.handleRequestBody(any(ServerRequest.class), eq(RegisterRequest.class)))
                    .thenReturn(Mono.just(registerRequest));
            when(requestHandlerUtil.extractClientIp(any(ServerRequest.class))).thenReturn("127.0.0.1");
            when(authRegistrationService.register(registerRequest)).thenReturn(Mono.just(loginResponse));
            when(requestHandlerUtil.buildSuccessResponse(loginResponse))
                    .thenReturn(org.springframework.web.reactive.function.server.ServerResponse.ok()
                            .bodyValue(loginResponse));

            StepVerifier.create(handler.register(createPostRequestWithBody("/auth/register", "{}")))
                    .assertNext(response ->
                            assertThat(response.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            assertThat(registerRequest.getClientIp()).isEqualTo("127.0.0.1");
            verify(authRegistrationService).register(registerRequest);
        }
    }

    @Nested
    @DisplayName("bindWechatPhone 方法")
    class BindWechatPhoneTests {

        /**
         * 验证 {@code shouldBindWechatPhoneSuccessfully} 场景。
         */
        @Test
        @DisplayName("应在注册前验证并绑定微信手机号")
        void shouldBindWechatPhoneSuccessfully() {
            WechatBindPhoneRequest bindRequest = new WechatBindPhoneRequest();
            LoginResponse response = new LoginResponse();
            response.setNextAction("REGISTER_REQUIRED");
            when(requestHandlerUtil.handleRequestBody(any(ServerRequest.class), eq(WechatBindPhoneRequest.class)))
                    .thenReturn(Mono.just(bindRequest));
            when(requestHandlerUtil.extractClientIp(any(ServerRequest.class))).thenReturn("127.0.0.1");
            when(authRegistrationService.bindWechatPhone(bindRequest)).thenReturn(Mono.just(response));
            when(requestHandlerUtil.buildSuccessResponse(response))
                    .thenReturn(org.springframework.web.reactive.function.server.ServerResponse.ok()
                            .bodyValue(response));

            StepVerifier.create(handler.bindWechatPhone(createPostRequestWithBody("/auth/wechat/bind-phone", "{}")))
                    .assertNext(result ->
                            assertThat(result.statusCode().is2xxSuccessful()).isTrue())
                    .verifyComplete();

            assertThat(bindRequest.getClientIp()).isEqualTo("127.0.0.1");
            verify(authRegistrationService).bindWechatPhone(bindRequest);
        }
    }

    /**
     * 创建{@code postRequestWithBody}。
     * @param path 参数 {@code path}
     * @param body 参数 {@code body}
     * @return 操作结果
     */
    private ServerRequest createPostRequestWithBody(String path, String body) {
        MockServerHttpRequest httpRequest = MockServerHttpRequest.post(path).body(body);
        MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
        return ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
    }

    /**
     * 创建{@code requestWithOpenId}。
     * @param path 参数 {@code path}
     * @return 操作结果
     */
    private ServerRequest createRequestWithOpenId(String path) {
        MockServerHttpRequest httpRequest = MockServerHttpRequest.get(path).build();
        MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
        var exchangeWithPrincipal = exchange.mutate()
                .principal(Mono.just((Principal) () -> TEST_OPEN_ID))
                .build();
        return ServerRequest.create(
                exchangeWithPrincipal, HandlerStrategies.withDefaults().messageReaders());
    }

    /**
     * 创建{@code postRequestWithOpenId}。
     * @param path 参数 {@code path}
     * @param body 参数 {@code body}
     * @return 操作结果
     */
    private ServerRequest createPostRequestWithOpenId(String path, String body) {
        MockServerHttpRequest httpRequest = MockServerHttpRequest.post(path).body(body);
        MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
        var exchangeWithPrincipal = exchange.mutate()
                .principal(Mono.just((Principal) () -> TEST_OPEN_ID))
                .build();
        return ServerRequest.create(
                exchangeWithPrincipal, HandlerStrategies.withDefaults().messageReaders());
    }

    @Nested
    @DisplayName("login 方法")
    class LoginTests {

        /**
         * 执行 {@code shouldLoginSuccessfully} 操作。
         */
        @Test
        @DisplayName("应成功登录并返回200且包含Token信息")
        void shouldLoginSuccessfully() {
            LoginRequest loginRequest = new LoginRequest();
            LoginResponse loginResponse = new LoginResponse();
            loginResponse.setOpenId(TEST_OPEN_ID);
            loginResponse.setAccessToken("test-access-token");

            when(requestHandlerUtil.handleRequestBody(any(ServerRequest.class), eq(LoginRequest.class)))
                    .thenReturn(Mono.just(loginRequest));
            when(requestHandlerUtil.extractClientIp(any(ServerRequest.class))).thenReturn("127.0.0.1");
            when(authService.login(any(LoginRequest.class), anyString())).thenReturn(Mono.just(loginResponse));
            when(requestHandlerUtil.buildSuccessResponse(any(LoginResponse.class)))
                    .thenReturn(org.springframework.web.reactive.function.server.ServerResponse.ok()
                            .bodyValue(loginResponse));

            ServerRequest request = createPostRequestWithBody("/auth/login", "{}");

            StepVerifier.create(handler.login(request))
                    .assertNext(response -> {
                        assertThat(response.statusCode().is2xxSuccessful()).isTrue();
                    })
                    .verifyComplete();

            verify(authService).login(any(LoginRequest.class), anyString());
            verify(requestHandlerUtil).buildSuccessResponse(any(LoginResponse.class));
        }

        /**
         * 验证 {@code shouldRecordMetricWhenLoginFails} 场景。
         */
        @Test
        @DisplayName("登录失败时应该记录安全指标")
        void shouldRecordMetricWhenLoginFails() {
            LoginRequest loginRequest = new LoginRequest();
            when(requestHandlerUtil.handleRequestBody(any(ServerRequest.class), eq(LoginRequest.class)))
                    .thenReturn(Mono.just(loginRequest));
            when(requestHandlerUtil.extractClientIp(any(ServerRequest.class))).thenReturn("127.0.0.1");
            when(authService.login(any(LoginRequest.class), anyString()))
                    .thenReturn(Mono.error(new IllegalStateException("login failed")));

            ServerRequest request = createPostRequestWithBody("/auth/login", "{}");

            StepVerifier.create(handler.login(request))
                    .expectError(IllegalStateException.class)
                    .verify();
            verify(securityMetricsRecorder).recordLoginFailed();
        }
    }

    @Nested
    @DisplayName("refresh 方法")
    class RefreshTests {

        /**
         * 执行 {@code shouldRefreshTokenSuccessfully} 操作。
         */
        @Test
        @DisplayName("应成功刷新Token并返回200")
        void shouldRefreshTokenSuccessfully() {
            LoginResponse loginResponse = new LoginResponse();
            loginResponse.setOpenId(TEST_OPEN_ID);
            loginResponse.setAccessToken("new-access-token");
            loginResponse.setRefreshToken("new-refresh-token");

            RefreshTokenRequest refreshTokenRequest = new RefreshTokenRequest();
            refreshTokenRequest.setRefreshToken("old-refresh-token");
            refreshTokenRequest.setDeviceId("device-123");
            // RefreshToken 从 URL 查询参数迁移至请求 Body（JSON），避免 Token 进入访问日志
            when(requestHandlerUtil.handleRequestBody(any(ServerRequest.class), eq(RefreshTokenRequest.class)))
                    .thenReturn(Mono.just(refreshTokenRequest));
            when(authService.refreshToken(eq("old-refresh-token"), eq("device-123")))
                    .thenReturn(Mono.just(loginResponse));
            when(requestHandlerUtil.buildSuccessResponse(any(LoginResponse.class)))
                    .thenReturn(org.springframework.web.reactive.function.server.ServerResponse.ok()
                            .bodyValue(loginResponse));

            MockServerHttpRequest httpRequest = MockServerHttpRequest.post("/auth/refresh")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body("{\"refreshToken\":\"old-refresh-token\",\"deviceId\":\"device-123\"}");
            MockServerWebExchange exchange = MockServerWebExchange.from(httpRequest);
            ServerRequest request = ServerRequest.create(
                    exchange, HandlerStrategies.withDefaults().messageReaders());

            StepVerifier.create(handler.refresh(request))
                    .assertNext(response -> {
                        assertThat(response.statusCode().is2xxSuccessful()).isTrue();
                    })
                    .verifyComplete();

            verify(authService).refreshToken(eq("old-refresh-token"), eq("device-123"));
            verify(requestHandlerUtil).buildSuccessResponse(any(LoginResponse.class));
        }
    }

    @Nested
    @DisplayName("logout 方法")
    class LogoutTests {

        /**
         * 执行 {@code shouldLogoutSuccessfully} 操作。
         */
        @Test
        @DisplayName("应成功退出登录并返回200")
        void shouldLogoutSuccessfully() {
            when(requestHandlerUtil.extractToken(any(ServerRequest.class)))
                    .thenReturn(java.util.Optional.of("test-token"));
            when(authService.logout(eq("test-token"), eq(TEST_OPEN_ID))).thenReturn(Mono.empty());
            when(requestHandlerUtil.buildSuccessResponse(any()))
                    .thenReturn(org.springframework.web.reactive.function.server.ServerResponse.ok()
                            .bodyValue(Map.of("success", true)));

            ServerRequest request = createRequestWithOpenId("/auth/logout");

            StepVerifier.create(handler.logout(request))
                    .assertNext(response -> {
                        assertThat(response.statusCode().is2xxSuccessful()).isTrue();
                    })
                    .verifyComplete();

            verify(authService).logout(eq("test-token"), eq(TEST_OPEN_ID));
        }
    }

    @Nested
    @DisplayName("bindPhone 方法")
    class BindPhoneTests {

        /**
         * 执行 {@code shouldBindPhoneSuccessfully} 操作。
         */
        @Test
        @DisplayName("应成功绑定手机号并返回200")
        void shouldBindPhoneSuccessfully() {
            WxPhoneRequest wxPhoneRequest = new WxPhoneRequest();
            wxPhoneRequest.setPhoneCode("test-phone-code");

            when(requestHandlerUtil.handleRequestBody(any(ServerRequest.class), eq(WxPhoneRequest.class)))
                    .thenReturn(Mono.just(wxPhoneRequest));
            when(authService.bindPhone(any(WxPhoneRequest.class), eq(TEST_OPEN_ID)))
                    .thenReturn(Mono.just(Map.of("phone", "13800138000")));
            when(requestHandlerUtil.buildSuccessResponse(any()))
                    .thenReturn(org.springframework.web.reactive.function.server.ServerResponse.ok()
                            .bodyValue(Map.of("phone", "13800138000")));

            ServerRequest request =
                    createPostRequestWithOpenId("/auth/bind-phone", "{\"phoneCode\":\"test-phone-code\"}");

            StepVerifier.create(handler.bindPhone(request))
                    .assertNext(response -> {
                        assertThat(response.statusCode().is2xxSuccessful()).isTrue();
                    })
                    .verifyComplete();

            verify(authService).bindPhone(any(WxPhoneRequest.class), eq(TEST_OPEN_ID));
            verify(requestHandlerUtil).buildSuccessResponse(any());
        }
    }

    @Nested
    @DisplayName("sendSmsCode 方法")
    class SendSmsCodeTests {

        /**
         * 执行 {@code shouldSendSmsCodeSuccessfully} 操作。
         */
        @Test
        @DisplayName("应成功发送短信验证码并返回200")
        void shouldSendSmsCodeSuccessfully() {
            SendSmsRequest sendSmsRequest = new SendSmsRequest();
            sendSmsRequest.setPhone("13800138000");

            when(requestHandlerUtil.handleRequestBody(any(ServerRequest.class), eq(SendSmsRequest.class)))
                    .thenReturn(Mono.just(sendSmsRequest));
            when(authService.sendSmsCode(any(SendSmsRequest.class))).thenReturn(Mono.empty());
            when(requestHandlerUtil.buildSuccessResponse(any()))
                    .thenReturn(org.springframework.web.reactive.function.server.ServerResponse.ok()
                            .bodyValue(Map.of("success", true)));

            ServerRequest request = createPostRequestWithBody("/auth/send-sms-code", "{\"phone\":\"13800138000\"}");

            StepVerifier.create(handler.sendSmsCode(request))
                    .assertNext(response -> {
                        assertThat(response.statusCode().is2xxSuccessful()).isTrue();
                    })
                    .verifyComplete();

            verify(authService).sendSmsCode(any(SendSmsRequest.class));
        }
    }
}
