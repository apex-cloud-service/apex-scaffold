package com.apex.scaffold.infrastructure.wechat.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.infrastructure.wechat.config.WeChatAppProperties;
import com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 微信APP服务单元测试类
 *
 * <p>测试 WeChatAppService 的 login、refreshToken、getUserInfo、checkAccessToken、
 * getGlobalAccessToken、sendTemplateMessage、sendCustomerMessage、getPhoneNumber 方法，
 * 验证微信API调用、错误处理和响应解析逻辑。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class WeChatAppServiceTest {

    @Mock
    private WeChatAppProperties properties;

    @InjectMocks
    private WeChatAppService weChatAppService;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private WebClient.RequestBodySpec requestBodySpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(weChatAppService, "webClient", webClient);
    }

    /**
     * 执行 {@code login_Success_ReturnsLoginResult} 操作。
     */
    @Test
    @DisplayName("login - 成功返回登录结果")
    void login_Success_ReturnsLoginResult() {
        Map<String, Object> response = Map.of("openid", "test_openid", "access_token", "wx_token");
        mockWebClientGet(response);

        weChatAppService
                .login("auth_code")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("openid", "test_openid");
                    assertThat(result).containsEntry("access_token", "wx_token");
                })
                .verifyComplete();
    }

    /**
     * 执行 {@code login_ErrorCode_ThrowsWeChatApiException} 操作。
     */
    @Test
    @DisplayName("login - 微信返回错误码应抛出WeChatApiException")
    void login_ErrorCode_ThrowsWeChatApiException() {
        Map<String, Object> response = Map.of("errcode", 40029, "errmsg", "invalid code");
        mockWebClientGet(response);

        weChatAppService
                .login("invalid_code")
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(WeChatApiException.class);
                    WeChatApiException ex = (WeChatApiException) e;
                    assertThat(ex.getErrcode()).isEqualTo(40029);
                    assertThat(ex.getErrmsg()).isEqualTo("invalid code");
                })
                .verify();
    }

    /**
     * 刷新{@code token_Success_ReturnsRefreshResult}。
     */
    @Test
    @DisplayName("refreshToken - 成功返回刷新结果")
    void refreshToken_Success_ReturnsRefreshResult() {
        Map<String, Object> response = Map.of("access_token", "new_token", "openid", "test_openid");
        mockWebClientGet(response);

        weChatAppService
                .refreshToken("refresh_token_value")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("access_token", "new_token");
                })
                .verifyComplete();
    }

    /**
     * 刷新{@code token_ErrorCode_ThrowsWeChatApiException}。
     */
    @Test
    @DisplayName("refreshToken - 微信返回错误码应抛出WeChatApiException")
    void refreshToken_ErrorCode_ThrowsWeChatApiException() {
        Map<String, Object> response = Map.of("errcode", 40030, "errmsg", "invalid refresh_token");
        mockWebClientGet(response);

        weChatAppService
                .refreshToken("bad_refresh_token")
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(WeChatApiException.class);
                    assertThat(((WeChatApiException) e).getErrcode()).isEqualTo(40030);
                })
                .verify();
    }

    /**
     * 获取{@code userInfo_Success_ReturnsUserInfo}。
     */
    @Test
    @DisplayName("getUserInfo - 成功返回用户信息")
    void getUserInfo_Success_ReturnsUserInfo() {
        Map<String, Object> response = Map.of("nickname", "TestUser", "openid", "test_openid");
        mockWebClientGet(response);

        weChatAppService
                .getUserInfo("access_token", "test_openid")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("nickname", "TestUser");
                })
                .verifyComplete();
    }

    /**
     * 获取{@code userInfo_ErrorCode_ThrowsWeChatApiException}。
     */
    @Test
    @DisplayName("getUserInfo - 微信返回错误码应抛出WeChatApiException")
    void getUserInfo_ErrorCode_ThrowsWeChatApiException() {
        Map<String, Object> response = Map.of("errcode", 40003, "errmsg", "invalid openid");
        mockWebClientGet(response);

        weChatAppService
                .getUserInfo("access_token", "invalid_openid")
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(WeChatApiException.class);
                    assertThat(((WeChatApiException) e).getErrcode()).isEqualTo(40003);
                })
                .verify();
    }

    /**
     * 检查{@code accessToken_Valid_ReturnsTrue}。
     */
    @Test
    @DisplayName("checkAccessToken - token有效应返回true")
    void checkAccessToken_Valid_ReturnsTrue() {
        Map<String, Object> response = Map.of("errcode", 0);
        mockWebClientGet(response);

        weChatAppService
                .checkAccessToken("valid_token", "test_openid")
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }

    /**
     * 检查{@code accessToken_Invalid_ReturnsFalse}。
     */
    @Test
    @DisplayName("checkAccessToken - token无效应返回false")
    void checkAccessToken_Invalid_ReturnsFalse() {
        Map<String, Object> response = Map.of("errcode", 40001);
        mockWebClientGet(response);

        weChatAppService
                .checkAccessToken("invalid_token", "test_openid")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 检查{@code accessToken_RequestError_ReturnsFalse}。
     */
    @Test
    @DisplayName("checkAccessToken - 请求异常应返回false")
    void checkAccessToken_RequestError_ReturnsFalse() {
        mockWebClientGetError(new RuntimeException("网络异常"));

        weChatAppService
                .checkAccessToken("token", "openid")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    /**
     * 获取{@code globalAccessToken_Success_ReturnsToken}。
     */
    @Test
    @DisplayName("getGlobalAccessToken - 成功返回access_token")
    void getGlobalAccessToken_Success_ReturnsToken() {
        Map<String, Object> response = Map.of("access_token", "global_token_123", "expires_in", 7200);
        mockWebClientGet(response);

        weChatAppService
                .getGlobalAccessToken()
                .as(StepVerifier::create)
                .expectNext("global_token_123")
                .verifyComplete();
    }

    /**
     * 获取{@code globalAccessToken_ErrorCode_ThrowsWeChatApiException}。
     */
    @Test
    @DisplayName("getGlobalAccessToken - 微信返回错误码应抛出WeChatApiException")
    void getGlobalAccessToken_ErrorCode_ThrowsWeChatApiException() {
        Map<String, Object> response = Map.of("errcode", 40013, "errmsg", "invalid appid");
        mockWebClientGet(response);

        weChatAppService
                .getGlobalAccessToken()
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(WeChatApiException.class);
                    assertThat(((WeChatApiException) e).getErrcode()).isEqualTo(40013);
                })
                .verify();
    }

    /**
     * 发送{@code templateMessage_ZeroErrcode_Success}。
     */
    @Test
    @DisplayName("sendTemplateMessage - errcode为0应成功")
    void sendTemplateMessage_ZeroErrcode_Success() {
        Map<String, Object> response = Map.of("errcode", 0, "errmsg", "ok");
        mockWebClientPost(response);

        Map<String, Object> templateData = Map.of("touser", "openid", "template_id", "TMPL123");
        weChatAppService
                .sendTemplateMessage("access_token", templateData)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("errcode", 0);
                })
                .verifyComplete();
    }

    /**
     * 发送{@code templateMessage_NoErrcode_Success}。
     */
    @Test
    @DisplayName("sendTemplateMessage - 响应中不包含errcode应成功")
    void sendTemplateMessage_NoErrcode_Success() {
        Map<String, Object> response = Map.of("msgid", 12345);
        mockWebClientPost(response);

        weChatAppService
                .sendTemplateMessage("access_token", Map.of())
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("msgid", 12345);
                })
                .verifyComplete();
    }

    /**
     * 发送{@code templateMessage_NonZeroErrcode_ThrowsWeChatApiException}。
     */
    @Test
    @DisplayName("sendTemplateMessage - errcode非0应抛出WeChatApiException")
    void sendTemplateMessage_NonZeroErrcode_ThrowsWeChatApiException() {
        Map<String, Object> response = Map.of("errcode", 40001, "errmsg", "invalid credential");
        mockWebClientPost(response);

        weChatAppService
                .sendTemplateMessage("bad_token", Map.of())
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(WeChatApiException.class);
                    assertThat(((WeChatApiException) e).getErrcode()).isEqualTo(40001);
                })
                .verify();
    }

    /**
     * 发送{@code customerMessage_ZeroErrcode_Success}。
     */
    @Test
    @DisplayName("sendCustomerMessage - errcode为0应成功")
    void sendCustomerMessage_ZeroErrcode_Success() {
        Map<String, Object> response = Map.of("errcode", 0, "errmsg", "ok");
        mockWebClientPost(response);

        weChatAppService
                .sendCustomerMessage("access_token", Map.of("touser", "openid"))
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("errcode", 0);
                })
                .verifyComplete();
    }

    /**
     * 发送{@code customerMessage_NonZeroErrcode_ThrowsWeChatApiException}。
     */
    @Test
    @DisplayName("sendCustomerMessage - errcode非0应抛出WeChatApiException")
    void sendCustomerMessage_NonZeroErrcode_ThrowsWeChatApiException() {
        Map<String, Object> response = Map.of("errcode", 45015, "errmsg", "response out of time limit");
        mockWebClientPost(response);

        weChatAppService
                .sendCustomerMessage("access_token", Map.of())
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> {
                    assertThat(e).isInstanceOf(WeChatApiException.class);
                    assertThat(((WeChatApiException) e).getErrcode()).isEqualTo(45015);
                })
                .verify();
    }

    /**
     * 获取{@code phoneNumber_ReturnsMockPhone}。
     */
    @Test
    @DisplayName("getPhoneNumber - 应直接返回模拟手机号")
    void getPhoneNumber_ReturnsMockPhone() {
        // Mock getGlobalAccessToken() internal call
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        // First bodyToMono call returns access_token response, second returns phone_info response
        when(responseSpec.bodyToMono(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(
                        Mono.just(Map.of("access_token", "global_token_123", "expires_in", 7200)),
                        Mono.just(Map.of("phone_info", Map.of("purePhoneNumber", "13800138000", "countryCode", "86"))));

        // Mock getPhoneNumber POST request
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);

        weChatAppService
                .getPhoneNumber("phone_code_123")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("phone", "13800138000");
                    assertThat(result).containsEntry("countryCode", "86");
                })
                .verifyComplete();
    }

    /**
     * 执行 {@code mockWebClientGet} 操作。
     * @param response 响应参数
     */
    @SuppressWarnings("unchecked")
    private void mockWebClientGet(Map<String, Object> response) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(response));
    }

    /**
     * 执行 {@code mockWebClientGetError} 操作。
     * @param error 参数 {@code error}
     */
    private void mockWebClientGetError(Throwable error) {
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(Mono.error(error));
    }

    /**
     * 执行 {@code mockWebClientPost} 操作。
     * @param response 响应参数
     */
    @SuppressWarnings("unchecked")
    private void mockWebClientPost(Map<String, Object> response) {
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(Mono.just(response));
    }
}
