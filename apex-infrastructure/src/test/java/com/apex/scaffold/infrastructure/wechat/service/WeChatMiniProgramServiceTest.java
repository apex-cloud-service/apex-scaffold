package com.apex.scaffold.infrastructure.wechat.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.infrastructure.wechat.config.WeChatMiniAppProperties;
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
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 微信小程序服务单元测试
 *
 * <p>覆盖渠道未配置拦截、jscode2session 成功/错误码、全局 AccessToken
 * 与手机号置换的请求编排逻辑。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
@ExtendWith(MockitoExtension.class)
class WeChatMiniProgramServiceTest {

    @Mock
    private WeChatMiniAppProperties properties;

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

    @InjectMocks
    private WeChatMiniProgramService service;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        lenient().when(properties.getAppId()).thenReturn("wx_mini_app_id");
        lenient().when(properties.getAppSecret()).thenReturn("wx_mini_secret");
    }

    @Test
    @DisplayName("渠道未配置 - codeToSession 返回渠道未配置业务异常")
    void codeToSession_ChannelNotConfigured_ThrowsBusinessException() {
        when(properties.getAppId()).thenReturn(null);

        service.codeToSession("js_code")
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ResultCode.WECHAT_CHANNEL_NOT_CONFIGURED.getCode()))
                .verify();
    }

    @Test
    @DisplayName("渠道未配置 - getGlobalAccessToken 返回渠道未配置业务异常")
    void getGlobalAccessToken_ChannelNotConfigured_ThrowsBusinessException() {
        when(properties.getAppSecret()).thenReturn("");

        service.getGlobalAccessToken()
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ResultCode.WECHAT_CHANNEL_NOT_CONFIGURED.getCode()))
                .verify();
    }

    @Test
    @DisplayName("codeToSession - 成功返回 openid/unionid/session_key")
    void codeToSession_Success() {
        mockWebClientGet(Map.of(
                "openid", "mini_openid",
                "unionid", "union_001",
                "session_key", "session_key_value"));

        service.codeToSession("js_code")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("openid", "mini_openid");
                    assertThat(result).containsEntry("unionid", "union_001");
                    assertThat(result).containsEntry("session_key", "session_key_value");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("codeToSession - 微信返回错误码抛出 WeChatApiException")
    void codeToSession_ErrorCode_ThrowsWeChatApiException() {
        mockWebClientGet(Map.of("errcode", 40029, "errmsg", "invalid code"));

        service.codeToSession("invalid_code")
                .as(StepVerifier::create)
                .expectError(WeChatApiException.class)
                .verify();
    }

    @Test
    @DisplayName("getPhoneNumber - 先取全局 Token 再置换手机号")
    void getPhoneNumber_Success() {
        // GET 全局 AccessToken
        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(any(org.springframework.core.ParameterizedTypeReference.class)))
                .thenReturn(
                        Mono.just(Map.of("access_token", "mini_global_token", "expires_in", 7200)),
                        Mono.just(Map.of("phone_info", Map.of("purePhoneNumber", "13800138000", "countryCode", "86"))));

        // POST 手机号置换
        when(webClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(any(Function.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.bodyValue(any())).thenReturn(requestHeadersSpec);

        service.getPhoneNumber("phone_code_123")
                .as(StepVerifier::create)
                .assertNext(result -> assertThat(result)
                        .containsEntry("phone_info", Map.of("purePhoneNumber", "13800138000", "countryCode", "86")))
                .verifyComplete();
    }

    /**
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
}
