package com.apex.scaffold.infrastructure.wechat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.infrastructure.wechat.config.WeChatMpProperties;
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
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 微信公众号服务单元测试。
 *
 * <p>覆盖渠道未配置拦截、网页授权 code 换会话、授权 URL 拼装与
 * OAuth state 签发/一次性消费。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
@ExtendWith(MockitoExtension.class)
class WeChatMpServiceTest {

    @Mock
    private WeChatMpProperties properties;

    @Mock
    private WebClient webClient;

    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RBucket<String> stateBucket;

    @InjectMocks
    private WeChatMpService service;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        lenient().when(properties.getAppId()).thenReturn("wx_mp_app_id");
        lenient().when(properties.getAppSecret()).thenReturn("wx_mp_secret");
        lenient().doReturn(stateBucket).when(redissonClient).getBucket(anyString(), any(StringCodec.class));
    }

    @Test
    @DisplayName("渠道未配置 - getAccessTokenByCode 返回渠道未配置业务异常")
    void getAccessTokenByCode_ChannelNotConfigured_ThrowsBusinessException() {
        when(properties.getAppId()).thenReturn(null);

        service.getAccessTokenByCode("mp_code")
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ResultCode.WECHAT_CHANNEL_NOT_CONFIGURED.getCode()))
                .verify();
    }

    @Test
    @DisplayName("渠道未配置 - issueAuthState 返回渠道未配置业务异常")
    void issueAuthState_ChannelNotConfigured_ThrowsBusinessException() {
        when(properties.getAppSecret()).thenReturn("");

        service.issueAuthState()
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ResultCode.WECHAT_CHANNEL_NOT_CONFIGURED.getCode()))
                .verify();
    }

    @Test
    @DisplayName("getAccessTokenByCode - 成功返回 openid/unionid")
    void getAccessTokenByCode_Success() {
        mockWebClientGet(Map.of(
                "openid", "mp_openid",
                "unionid", "union_001",
                "access_token", "mp_access_token",
                "expires_in", 7200));

        service.getAccessTokenByCode("mp_code")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsEntry("openid", "mp_openid");
                    assertThat(result).containsEntry("unionid", "union_001");
                    assertThat(result).containsEntry("access_token", "mp_access_token");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getAccessTokenByCode - 微信返回错误码抛出 WeChatApiException")
    void getAccessTokenByCode_ErrorCode_ThrowsWeChatApiException() {
        mockWebClientGet(Map.of("errcode", 40029, "errmsg", "invalid code"));

        service.getAccessTokenByCode("invalid_code")
                .as(StepVerifier::create)
                .expectError(WeChatApiException.class)
                .verify();
    }

    @Test
    @DisplayName("buildAuthorizeUrl - 拼装授权 URL（scope 缺省 snsapi_base，含 #wechat_redirect）")
    void buildAuthorizeUrl_AssemblesAuthorizeUrl() {
        String url = service.buildAuthorizeUrl("https://h5.example.com/login", null, "state-1");

        assertThat(url)
                .startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?")
                .contains("appid=wx_mp_app_id")
                .contains("scope=snsapi_base")
                .contains("state=state-1")
                .endsWith("#wechat_redirect");
    }

    @Test
    @DisplayName("issueAuthState - 签发随机 state 并登记 Redis")
    void issueAuthState_GeneratesAndStoresState() {
        when(stateBucket.setAsync(anyString(), any(java.time.Duration.class)))
                .thenReturn(new org.redisson.misc.CompletableFutureWrapper<>((Void) null));

        service.issueAuthState()
                .as(StepVerifier::create)
                .assertNext(state -> assertThat(state).isNotBlank())
                .verifyComplete();
    }

    @Test
    @DisplayName("validateAuthState - state 存在即原子消费并返回 true")
    void validateAuthState_ConsumedOnceReturnsTrue() {
        when(stateBucket.getAndDeleteAsync()).thenReturn(new org.redisson.misc.CompletableFutureWrapper<>("1"));

        service.validateAuthState("state-1")
                .as(StepVerifier::create)
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    @DisplayName("validateAuthState - state 不存在返回 false")
    void validateAuthState_MissingReturnsFalse() {
        when(stateBucket.getAndDeleteAsync())
                .thenReturn(new org.redisson.misc.CompletableFutureWrapper<>((String) null));

        service.validateAuthState("state-1")
                .as(StepVerifier::create)
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    @DisplayName("validateAuthState - state 为空直接返回 false")
    void validateAuthState_BlankReturnsFalse() {
        service.validateAuthState(" ")
                .as(StepVerifier::create)
                .expectNext(false)
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
