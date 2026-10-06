package com.apex.scaffold.application.service.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.UserSocialService;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.gateway.WeChatGateway;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 微信授权码登录策略单元测试。
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
class WeChatCodeLoginStrategyTest {

    @Mock
    private WeChatGateway weChatGateway;

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Mock
    private LoginResponseHelper loginResponseHelper;

    @Mock
    private AuthTicketService authTicketService;

    @Mock
    private UserSocialService userSocialService;

    private WeChatCodeLoginStrategy strategy;
    private LoginRequest request;

    /**
     * 验证 {@code setUp} 场景。
     */
    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(userService.assertWechatRegistrationAllowed(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.empty());
        strategy = new WeChatCodeLoginStrategy(
                weChatGateway, userService, userSocialService, tokenService, loginResponseHelper, authTicketService);
        // 默认放行社交身份绑定登记（幂等操作，多数用例不关注绑定细节）
        org.mockito.Mockito.lenient()
                .when(userSocialService.upsertBinding(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.empty());
        request = new LoginRequest();
        request.setCode("wx-code");
        request.setDeviceId("device");
        request.setClientIp("127.0.0.1");
    }

    /**
     * 验证 {@code registeredWechatUserLogsInDirectly} 场景。
     */
    @Test
    void registeredWechatUserLogsInDirectly() {
        when(weChatGateway.login("wx-code")).thenReturn(Mono.just(loginResult(7200L)));
        // 模拟微信用户信息获取（登录时拉取昵称、头像、性别等资料）
        when(weChatGateway.getUserInfo("wx-access", "openid")).thenReturn(Mono.empty());
        UserEntity user = user("openid", "13812345678");
        when(userService.getByOpenId("openid")).thenReturn(Mono.just(user));
        when(userService.update(user)).thenReturn(Mono.just(user));
        when(tokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("access");
        when(tokenService.generateRefreshToken(any(), any(), any(), any())).thenReturn("refresh");
        when(tokenService.storeSessionToken(eq("openid"), any(), eq("access"), eq("refresh"), any(), any()))
                .thenReturn(Mono.empty());
        LoginResponse expected = new LoginResponse();
        expected.setNextAction("AUTHENTICATED");
        when(loginResponseHelper.build(user, "access", "refresh", false, "13812345678", "127.0.0.1"))
                .thenReturn(expected);

        StepVerifier.create(strategy.login(request)).expectNext(expected).verifyComplete();
        verify(authTicketService, never()).issue(any());
    }

    /**
     * 验证 {@code existingWechatWithoutPhoneRequiresBinding} 场景。
     */
    @Test
    void existingWechatWithoutPhoneRequiresBinding() {
        when(weChatGateway.login("wx-code")).thenReturn(Mono.just(loginResult(null)));
        when(weChatGateway.getUserInfo("wx-access", "openid")).thenReturn(Mono.empty());
        when(userService.getByOpenId("openid")).thenReturn(Mono.just(user("openid", null)));
        when(authTicketService.issue(
                        org.mockito.ArgumentMatchers.argThat(payload -> "device".equals(payload.getDeviceId()))))
                .thenReturn(Mono.just("wx-ticket"));

        StepVerifier.create(strategy.login(request))
                .assertNext(response -> {
                    assertThat(response.getNextAction()).isEqualTo("PHONE_BIND_REQUIRED");
                    assertThat(response.getAuthTicket()).isEqualTo("wx-ticket");
                    assertThat(response.getAuthTicketExpiresIn()).isEqualTo(300L);
                })
                .verifyComplete();
    }

    /**
     * 验证 {@code unknownWechatDoesNotCreateUserAndRequiresBinding} 场景。
     */
    @Test
    void unknownWechatDoesNotCreateUserAndRequiresBinding() {
        when(weChatGateway.login("wx-code")).thenReturn(Mono.just(loginResult(60L)));
        when(weChatGateway.getUserInfo("wx-access", "openid")).thenReturn(Mono.empty());
        when(userService.getByOpenId("openid")).thenReturn(Mono.empty());
        when(authTicketService.issue(
                        org.mockito.ArgumentMatchers.argThat(payload -> "device".equals(payload.getDeviceId()))))
                .thenReturn(Mono.just("wx-ticket"));

        StepVerifier.create(strategy.login(request))
                .assertNext(response -> assertThat(response.getNextAction()).isEqualTo("PHONE_BIND_REQUIRED"))
                .verifyComplete();
        verify(userService, never()).create(any());
    }

    /**
     * 验证 {@code invalidWechatResponseFails} 场景。
     */
    @Test
    void invalidWechatResponseFails() {
        WeChatGateway.WeChatLoginResult invalid = loginResult(60L);
        invalid.setOpenid(" ");
        when(weChatGateway.login("wx-code")).thenReturn(Mono.just(invalid));

        StepVerifier.create(strategy.login(request)).verifyErrorSatisfies(error -> {
            assertThat(error).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.WX_LOGIN_FAILED.getCode());
        });
    }

    /**
     * 验证已注册微信用户登录时，从微信接口成功拉取用户信息（包括性别）并同步到用户实体。
     */
    @Test
    void registeredUserSyncsWeChatProfileIncludingGender() {
        when(weChatGateway.login("wx-code")).thenReturn(Mono.just(loginResult(7200L)));
        // 模拟微信返回完整的用户信息（包含性别=女（2））
        WeChatGateway.WeChatUserInfo wxUserInfo = new WeChatGateway.WeChatUserInfo();
        wxUserInfo.setNickname("微信昵称");
        wxUserInfo.setAvatarUrl("https://avatar.url");
        wxUserInfo.setGender(2);
        wxUserInfo.setCountry("中国");
        wxUserInfo.setProvince("广东");
        wxUserInfo.setCity("深圳");
        when(weChatGateway.getUserInfo("wx-access", "openid")).thenReturn(Mono.just(wxUserInfo));
        UserEntity user = user("openid", "13812345678");
        when(userService.getByOpenId("openid")).thenReturn(Mono.just(user));
        when(userService.update(user)).thenReturn(Mono.just(user));
        when(tokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("access");
        when(tokenService.generateRefreshToken(any(), any(), any(), any())).thenReturn("refresh");
        when(tokenService.storeSessionToken(eq("openid"), any(), eq("access"), eq("refresh"), any(), any()))
                .thenReturn(Mono.empty());
        LoginResponse expected = new LoginResponse();
        expected.setNextAction("AUTHENTICATED");
        when(loginResponseHelper.build(user, "access", "refresh", false, "13812345678", "127.0.0.1"))
                .thenReturn(expected);

        StepVerifier.create(strategy.login(request)).expectNext(expected).verifyComplete();
        verify(authTicketService, never()).issue(any());
    }

    /**
     * 验证 {@code loginTypeIsWxCode} 场景。
     */
    @Test
    void loginTypeIsWxCode() {
        assertThat(strategy.getLoginType().name()).isEqualTo("WX_CODE");
    }

    /**
     * 构造微信登录结果对象。
     *
     * @param expiresIn 令牌有效期秒数
     * @return 微信登录结果
     */
    private WeChatGateway.WeChatLoginResult loginResult(Long expiresIn) {
        WeChatGateway.WeChatLoginResult result = new WeChatGateway.WeChatLoginResult();
        result.setOpenid("openid");
        result.setUnionid("unionid");
        result.setAccessToken("wx-access");
        result.setRefreshToken("wx-refresh");
        result.setExpiresIn(expiresIn);
        return result;
    }

    /**
     * 构造指定属性的用户实体。
     *
     * @param openId 微信 OpenId
     * @param phone  手机号
     * @return 用户实体
     */
    private UserEntity user(String openId, String phone) {
        UserEntity user = new UserEntity();
        user.setId(1L);
        user.setOpenId(openId);
        user.setPhone(phone);
        return user;
    }
}
