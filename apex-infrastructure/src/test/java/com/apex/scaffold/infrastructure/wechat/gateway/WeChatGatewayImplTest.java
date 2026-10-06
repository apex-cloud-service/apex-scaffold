package com.apex.scaffold.infrastructure.wechat.gateway;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.infrastructure.circuitbreaker.CircuitBreakerService;
import com.apex.scaffold.infrastructure.wechat.service.WeChatAppService;
import com.apex.scaffold.infrastructure.wechat.service.WeChatMiniProgramService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 微信网关实现类单元测试
 *
 * <p>测试 WeChatGatewayImpl 的 login、getUserInfo、getPhoneNumber 方法，
 * 验证 Map 到 DTO 的转换逻辑。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class WeChatGatewayImplTest {

    @Mock
    private WeChatAppService weChatAppService;

    @Mock
    private WeChatMiniProgramService weChatMiniProgramService;

    @Mock
    private CircuitBreakerService circuitBreakerService;

    @InjectMocks
    private WeChatGatewayImpl weChatGatewayImpl;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        lenient()
                .when(circuitBreakerService.withCircuitBreaker(anyString(), any(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
        // 小程序 code 换会话使用两参断路器（自定义降级透传业务异常）
        lenient()
                .when(circuitBreakerService.withCircuitBreaker(anyString(), any()))
                .thenAnswer(invocation -> invocation.getArgument(1));
    }

    @Test
    @DisplayName("login - 成功委派并返回登录结果")
    void login_Success_ReturnsLoginResult() {
        Map<String, Object> expected = Map.of(
                "openid",
                "test_openid",
                "access_token",
                "token123",
                "unionid",
                "test_unionid",
                "refresh_token",
                "refresh123",
                "expires_in",
                7200);
        when(weChatAppService.login("auth_code")).thenReturn(Mono.just(expected));

        weChatGatewayImpl
                .login("auth_code")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getOpenid()).isEqualTo("test_openid");
                    assertThat(result.getAccessToken()).isEqualTo("token123");
                    assertThat(result.getUnionid()).isEqualTo("test_unionid");
                    assertThat(result.getRefreshToken()).isEqualTo("refresh123");
                    assertThat(result.getExpiresIn()).isEqualTo(7200L);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("login - 服务异常应返回降级结果")
    void login_ServiceError_ReturnsFallback() {
        when(weChatAppService.login("bad_code")).thenReturn(Mono.error(new RuntimeException("微信服务异常")));

        weChatGatewayImpl
                .login("bad_code")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getOpenid()).isNull();
                    assertThat(result.getAccessToken()).isNull();
                    assertThat(result.getExpiresIn()).isZero();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getUserInfo - 成功委派并返回用户信息")
    void getUserInfo_Success_ReturnsUserInfo() {
        Map<String, Object> expected = Map.of(
                "nickname",
                "TestUser",
                "headimgurl",
                "http://avatar.url",
                "openid",
                "openid123",
                "sex",
                1,
                "country",
                "中国",
                "province",
                "北京",
                "city",
                "朝阳");
        when(weChatAppService.getUserInfo("access123", "openid123")).thenReturn(Mono.just(expected));

        weChatGatewayImpl
                .getUserInfo("access123", "openid123")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getNickname()).isEqualTo("TestUser");
                    assertThat(result.getAvatarUrl()).isEqualTo("http://avatar.url");
                    assertThat(result.getOpenid()).isEqualTo("openid123");
                    assertThat(result.getGender()).isEqualTo(1);
                    assertThat(result.getCountry()).isEqualTo("中国");
                    assertThat(result.getProvince()).isEqualTo("北京");
                    assertThat(result.getCity()).isEqualTo("朝阳");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getUserInfo - 返回空Map应返回空字段DTO")
    void getUserInfo_EmptyResult_ReturnsEmptyDto() {
        when(weChatAppService.getUserInfo("access123", "openid123")).thenReturn(Mono.just(Map.of()));

        weChatGatewayImpl
                .getUserInfo("access123", "openid123")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getNickname()).isNull();
                    assertThat(result.getAvatarUrl()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getPhoneNumber - 成功委派并返回手机号结果")
    void getPhoneNumber_Success_ReturnsPhoneResult() {
        Map<String, Object> phoneInfo = Map.of(
                "phoneNumber", "13800138000",
                "purePhoneNumber", "13800138000",
                "countryCode", "86");
        Map<String, Object> expected = Map.of("phone_info", phoneInfo);
        when(weChatAppService.getPhoneNumber("phone_code")).thenReturn(Mono.just(expected));

        weChatGatewayImpl
                .getPhoneNumber("phone_code")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getPhoneNumber()).isEqualTo("13800138000");
                    assertThat(result.getPurePhoneNumber()).isEqualTo("13800138000");
                    assertThat(result.getCountryCode()).isEqualTo("86");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("getPhoneNumber - 返回空Map应返回空字段DTO")
    void getPhoneNumber_EmptyResult_ReturnsEmptyDto() {
        when(weChatAppService.getPhoneNumber("invalid_code")).thenReturn(Mono.just(Map.of()));

        weChatGatewayImpl
                .getPhoneNumber("invalid_code")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getPhoneNumber()).isNull();
                    assertThat(result.getPurePhoneNumber()).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("miniProgramCodeToSession - 成功委派并转换会话信息")
    void miniProgramCodeToSession_Success() {
        when(weChatMiniProgramService.codeToSession("js_code"))
                .thenReturn(Mono.just(Map.of(
                        "openid", "mini_openid",
                        "unionid", "union_001",
                        "session_key", "session_key_value")));

        weChatGatewayImpl
                .miniProgramCodeToSession("js_code")
                .as(StepVerifier::create)
                .assertNext(session -> {
                    assertThat(session.getOpenid()).isEqualTo("mini_openid");
                    assertThat(session.getUnionid()).isEqualTo("union_001");
                    assertThat(session.getSessionKey()).isEqualTo("session_key_value");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("miniProgramCodeToSession - 渠道未配置业务异常直接透传")
    void miniProgramCodeToSession_BusinessException_Passthrough() {
        when(weChatMiniProgramService.codeToSession("js_code"))
                .thenReturn(Mono.error(new BusinessException(ResultCode.WECHAT_CHANNEL_NOT_CONFIGURED)));

        weChatGatewayImpl
                .miniProgramCodeToSession("js_code")
                .as(StepVerifier::create)
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ResultCode.WECHAT_CHANNEL_NOT_CONFIGURED.getCode()))
                .verify();
    }

    @Test
    @DisplayName("miniProgramPhoneNumber - 成功委派并解析 phone_info")
    void miniProgramPhoneNumber_Success() {
        when(weChatMiniProgramService.getPhoneNumber("phone_code"))
                .thenReturn(
                        Mono.just(Map.of("phone_info", Map.of("purePhoneNumber", "13800138000", "countryCode", "86"))));

        weChatGatewayImpl
                .miniProgramPhoneNumber("phone_code")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getPurePhoneNumber()).isEqualTo("13800138000");
                    assertThat(result.getCountryCode()).isEqualTo("86");
                })
                .verifyComplete();
    }
}
