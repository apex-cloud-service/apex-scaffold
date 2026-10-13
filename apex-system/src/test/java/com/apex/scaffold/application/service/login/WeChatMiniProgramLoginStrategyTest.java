package com.apex.scaffold.application.service.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.UserSocialService;
import com.apex.scaffold.application.service.auth.AuthNextAction;
import com.apex.scaffold.application.service.auth.AuthTicketPayload;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.enums.LoginType;
import com.apex.scaffold.domain.enums.SocialChannel;
import com.apex.scaffold.domain.gateway.WeChatGateway;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.model.entity.UserSocialEntity;
import com.apex.scaffold.domain.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 微信小程序登录策略单元测试。
 *
 * <p>覆盖账户归一四步：渠道绑定命中登录、UnionID 归一、手机号归一、
 * 票据引导（绑定/注册），以及参数与登录失败分支。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
@ExtendWith(MockitoExtension.class)
class WeChatMiniProgramLoginStrategyTest {

    private static final String OPEN_ID = "mini_openid";
    private static final String UNION_ID = "union_001";
    private static final String PHONE = "13812345678";
    private static final String DEVICE_ID = "device-1";
    private static final Long USER_ID = 1001L;

    @Mock
    private WeChatGateway weChatGateway;

    @Mock
    private UserService userService;

    @Mock
    private UserSocialService userSocialService;

    @Mock
    private TokenService tokenService;

    @Mock
    private LoginResponseHelper loginResponseHelper;

    @Mock
    private AuthTicketService authTicketService;

    private WeChatMiniProgramLoginStrategy strategy;

    /**
     * 验证 {@code setUp} 场景。
     */
    @BeforeEach
    void setUp() {
        strategy = new WeChatMiniProgramLoginStrategy(
                weChatGateway, userService, userSocialService, tokenService, loginResponseHelper, authTicketService);
        // 默认放行社交绑定登记（幂等操作，多数用例不关注绑定细节）
        lenientWhenUpsertBindingSucceeds();
    }

    /** 默认放行社交绑定登记，避免逐用例重复打桩。 */
    private void lenientWhenUpsertBindingSucceeds() {
        org.mockito.Mockito.lenient()
                .when(userSocialService.upsertBinding(any(), any(), any(SocialChannel.class), any(), any()))
                .thenReturn(Mono.empty());
    }

    /** 构造小程序会话网关返回值。 */
    private WeChatGateway.MiniProgramSession session(String openid, String unionid) {
        WeChatGateway.MiniProgramSession session = new WeChatGateway.MiniProgramSession();
        session.setOpenid(openid);
        session.setUnionid(unionid);
        session.setSessionKey("session_key_value");
        return session;
    }

    /** 构造测试用户实体。 */
    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setOpenId("PHONE_placeholder");
        user.setTenantId("default");
        user.setPhone(PHONE);
        user.setStatus(1);
        return user;
    }

    /** 构造登录请求。 */
    private LoginRequest request(String code, String phoneCode) {
        LoginRequest request = new LoginRequest();
        request.setLoginType("WX_MINI");
        request.setCode(code);
        request.setPhoneCode(phoneCode);
        request.setDeviceId(DEVICE_ID);
        request.setClientIp("127.0.0.1");
        return request;
    }

    /** 打桩：登录成功路径的令牌签发与存储。 */
    private void stubSuccessfulLogin(UserEntity user) {
        when(tokenService.generateAccessToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("access");
        when(tokenService.generateRefreshToken(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("refresh");
        when(tokenService.storeSessionToken(anyString(), anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(Mono.empty());
        when(userService.updateLoginInfo(any(), anyString())).thenReturn(Mono.just(true));
        LoginResponse loginResponse = new LoginResponse();
        loginResponse.setAccessToken("access");
        loginResponse.setRefreshToken("refresh");
        when(loginResponseHelper.build(any(), eq("access"), eq("refresh"), eq(false), any(), any()))
                .thenReturn(loginResponse);
    }

    @Test
    @DisplayName("归一步骤1 - 渠道绑定命中直接登录并保持绑定")
    void boundUserLogsInDirectly() {
        LoginRequest request = request("wx_code", null);
        UserEntity user = user();
        UserSocialEntity binding =
                UserSocialEntity.bind(USER_ID, "default", SocialChannel.WECHAT_MINI, OPEN_ID, UNION_ID);
        when(weChatGateway.miniProgramCodeToSession("wx_code")).thenReturn(Mono.just(session(OPEN_ID, UNION_ID)));
        when(userSocialService.findByChannelAndOpenId(SocialChannel.WECHAT_MINI, OPEN_ID))
                .thenReturn(Mono.just(binding));
        when(userService.getById(USER_ID)).thenReturn(Mono.just(user));
        stubSuccessfulLogin(user);

        StepVerifier.create(strategy.login(request))
                .assertNext(response -> assertThat(response.getAccessToken()).isEqualTo("access"))
                .verifyComplete();

        verify(tokenService)
                .storeSessionToken(eq("PHONE_placeholder"), anyString(), eq("access"), eq("refresh"), any(), any());
        verify(userSocialService)
                .upsertBinding(eq(USER_ID), eq("default"), eq(SocialChannel.WECHAT_MINI), eq(OPEN_ID), eq(UNION_ID));
    }

    @Test
    @DisplayName("归一步骤2 - UnionID 归一登录并补当前渠道绑定行")
    void unionIdNormalizationLogsInAndBinds() {
        LoginRequest request = request("wx_code", null);
        UserEntity user = user();
        UserSocialEntity unionBinding =
                UserSocialEntity.bind(USER_ID, "default", SocialChannel.WECHAT_APP, "app_openid", UNION_ID);
        when(weChatGateway.miniProgramCodeToSession("wx_code")).thenReturn(Mono.just(session(OPEN_ID, UNION_ID)));
        when(userSocialService.findByChannelAndOpenId(SocialChannel.WECHAT_MINI, OPEN_ID))
                .thenReturn(Mono.empty());
        when(userSocialService.findByUnionId(UNION_ID)).thenReturn(Mono.just(unionBinding));
        when(userService.getById(USER_ID)).thenReturn(Mono.just(user));
        stubSuccessfulLogin(user);

        StepVerifier.create(strategy.login(request))
                .assertNext(response -> assertThat(response.getAccessToken()).isEqualTo("access"))
                .verifyComplete();

        // UnionID 归一命中后需补 WECHAT_MINI 渠道绑定行
        verify(userSocialService)
                .upsertBinding(eq(USER_ID), eq("default"), eq(SocialChannel.WECHAT_MINI), eq(OPEN_ID), eq(UNION_ID));
    }

    @Test
    @DisplayName("归一步骤3 - phoneCode 置换手机号命中已注册账户")
    void phoneCodeNormalizationLogsIn() {
        LoginRequest request = request("wx_code", "phone_code_1");
        UserEntity user = user();
        when(weChatGateway.miniProgramCodeToSession("wx_code")).thenReturn(Mono.just(session(OPEN_ID, UNION_ID)));
        when(userSocialService.findByChannelAndOpenId(SocialChannel.WECHAT_MINI, OPEN_ID))
                .thenReturn(Mono.empty());
        when(userSocialService.findByUnionId(UNION_ID)).thenReturn(Mono.empty());
        WeChatGateway.WeChatPhoneResult phoneResult = new WeChatGateway.WeChatPhoneResult();
        phoneResult.setPurePhoneNumber(PHONE);
        when(weChatGateway.miniProgramPhoneNumber("phone_code_1")).thenReturn(Mono.just(phoneResult));
        when(userService.getByPhone(PHONE)).thenReturn(Mono.just(user));
        stubSuccessfulLogin(user);

        StepVerifier.create(strategy.login(request))
                .assertNext(response -> assertThat(response.getAccessToken()).isEqualTo("access"))
                .verifyComplete();

        verify(userSocialService)
                .upsertBinding(eq(USER_ID), eq("default"), eq(SocialChannel.WECHAT_MINI), eq(OPEN_ID), eq(UNION_ID));
        verify(authTicketService, org.mockito.Mockito.never()).issue(any());
    }

    @Test
    @DisplayName("归一步骤4a - 无 phoneCode 的新用户签发绑定票据")
    void newWithoutPhoneCodeIssuesBindTicket() {
        LoginRequest request = request("wx_code", null);
        when(weChatGateway.miniProgramCodeToSession("wx_code")).thenReturn(Mono.just(session(OPEN_ID, UNION_ID)));
        when(userSocialService.findByChannelAndOpenId(SocialChannel.WECHAT_MINI, OPEN_ID))
                .thenReturn(Mono.empty());
        when(userSocialService.findByUnionId(UNION_ID)).thenReturn(Mono.empty());
        when(userService.assertWechatRegistrationAllowed(OPEN_ID)).thenReturn(Mono.empty());
        when(authTicketService.issue(any(AuthTicketPayload.class))).thenReturn(Mono.just("bind_ticket"));

        StepVerifier.create(strategy.login(request))
                .assertNext(response -> {
                    assertThat(response.getNextAction()).isEqualTo(AuthNextAction.PHONE_BIND_REQUIRED.name());
                    assertThat(response.getAuthTicket()).isEqualTo("bind_ticket");
                })
                .verifyComplete();

        ArgumentCaptor<AuthTicketPayload> captor = ArgumentCaptor.forClass(AuthTicketPayload.class);
        verify(authTicketService).issue(captor.capture());
        assertThat(captor.getValue().getType())
                .isEqualTo(com.apex.scaffold.application.service.auth.AuthTicketType.WECHAT_BIND);
        assertThat(captor.getValue().getOpenId()).isEqualTo(OPEN_ID);
        assertThat(captor.getValue().getChannel()).isEqualTo(SocialChannel.WECHAT_MINI.name());
        assertThat(captor.getValue().getRegisterChannel())
                .isEqualTo(com.apex.scaffold.domain.enums.RegisterChannel.MINI_PROGRAM.name());
    }

    @Test
    @DisplayName("归一步骤4b - phoneCode 置换出未注册手机号签发注册票据")
    void newWithPhoneCodeIssuesRegistrationTicket() {
        LoginRequest request = request("wx_code", "phone_code_1");
        when(weChatGateway.miniProgramCodeToSession("wx_code")).thenReturn(Mono.just(session(OPEN_ID, UNION_ID)));
        when(userSocialService.findByChannelAndOpenId(SocialChannel.WECHAT_MINI, OPEN_ID))
                .thenReturn(Mono.empty());
        when(userSocialService.findByUnionId(UNION_ID)).thenReturn(Mono.empty());
        WeChatGateway.WeChatPhoneResult phoneResult = new WeChatGateway.WeChatPhoneResult();
        phoneResult.setPurePhoneNumber(PHONE);
        when(weChatGateway.miniProgramPhoneNumber("phone_code_1")).thenReturn(Mono.just(phoneResult));
        when(userService.getByPhone(PHONE)).thenReturn(Mono.empty());
        when(userService.assertPhoneRegistrationAllowed(PHONE)).thenReturn(Mono.empty());
        when(userService.assertWechatRegistrationAllowed(OPEN_ID)).thenReturn(Mono.empty());
        when(authTicketService.issue(any(AuthTicketPayload.class))).thenReturn(Mono.just("register_ticket"));

        StepVerifier.create(strategy.login(request))
                .assertNext(response -> {
                    assertThat(response.getNextAction()).isEqualTo(AuthNextAction.REGISTER_REQUIRED.name());
                    assertThat(response.getAuthTicket()).isEqualTo("register_ticket");
                })
                .verifyComplete();

        ArgumentCaptor<AuthTicketPayload> captor = ArgumentCaptor.forClass(AuthTicketPayload.class);
        verify(authTicketService).issue(captor.capture());
        assertThat(captor.getValue().getType())
                .isEqualTo(com.apex.scaffold.application.service.auth.AuthTicketType.REGISTRATION);
        assertThat(captor.getValue().getPhone()).isEqualTo(PHONE);
        assertThat(captor.getValue().getChannel()).isEqualTo(SocialChannel.WECHAT_MINI.name());
    }

    @Test
    @DisplayName("code 缺失返回参数错误")
    void missingCodeRejected() {
        StepVerifier.create(strategy.login(request(null, null)))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ResultCode.PARAM_ERROR.getCode()))
                .verify();
    }

    @Test
    @DisplayName("openid 缺失返回微信登录失败")
    void emptyOpenidRejected() {
        when(weChatGateway.miniProgramCodeToSession("wx_code")).thenReturn(Mono.just(session(null, null)));

        StepVerifier.create(strategy.login(request("wx_code", null)))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ResultCode.WX_LOGIN_FAILED.getCode()))
                .verify();
    }

    @Test
    @DisplayName("登录类型为 WX_MINI")
    void loginTypeIsWxMini() {
        assertThat(strategy.getLoginType()).isEqualTo(LoginType.WX_MINI);
    }
}
