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
 * 微信公众号网页授权登录策略单元测试。
 *
 * <p>覆盖 state 防 CSRF 校验、账户归一（渠道绑定命中 / UnionID 归一 / 票据引导）
 * 与参数校验分支。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
@ExtendWith(MockitoExtension.class)
class WeChatMpAuthLoginStrategyTest {

    private static final String OPEN_ID = "mp_openid";
    private static final String UNION_ID = "union_001";
    private static final String STATE = "state-uuid-001";
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

    private WeChatMpAuthLoginStrategy strategy;

    /**
     * 验证 {@code setUp} 场景。
     */
    @BeforeEach
    void setUp() {
        strategy = new WeChatMpAuthLoginStrategy(
                weChatGateway, userService, userSocialService, tokenService, loginResponseHelper, authTicketService);
        // 默认放行社交绑定登记（幂等操作，多数用例不关注绑定细节）
        org.mockito.Mockito.lenient()
                .when(userSocialService.upsertBinding(any(), any(), any(SocialChannel.class), any(), any()))
                .thenReturn(Mono.empty());
    }

    /** 构造公众号网页授权会话。 */
    private WeChatGateway.MpAuthSession session(String openid, String unionid) {
        WeChatGateway.MpAuthSession session = new WeChatGateway.MpAuthSession();
        session.setOpenid(openid);
        session.setUnionid(unionid);
        session.setAccessToken("mp_access_token");
        session.setExpiresIn(7200L);
        return session;
    }

    /** 构造测试用户实体。 */
    private UserEntity user() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setOpenId("PHONE_placeholder");
        user.setTenantId("default");
        user.setPhone("13812345678");
        user.setStatus(1);
        return user;
    }

    /** 构造登录请求。 */
    private LoginRequest request(String code, String state) {
        LoginRequest request = new LoginRequest();
        request.setLoginType("WX_OAUTH");
        request.setCode(code);
        request.setState(state);
        request.setDeviceId("device-1");
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
    @DisplayName("state 无效时拒绝登录（防 CSRF）")
    void invalidStateRejected() {
        when(weChatGateway.mpValidateAuthState(STATE)).thenReturn(Mono.just(false));

        StepVerifier.create(strategy.login(request("mp_code", STATE)))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ResultCode.PARAM_ERROR.getCode()))
                .verify();

        verify(weChatGateway).mpValidateAuthState(STATE);
    }

    @Test
    @DisplayName("归一步骤1 - 渠道绑定命中直接登录并保持绑定")
    void boundUserLogsInDirectly() {
        UserEntity user = user();
        UserSocialEntity binding =
                UserSocialEntity.bind(USER_ID, "default", SocialChannel.WECHAT_MP, OPEN_ID, UNION_ID);
        when(weChatGateway.mpValidateAuthState(STATE)).thenReturn(Mono.just(true));
        when(weChatGateway.mpAuthCodeToSession("mp_code")).thenReturn(Mono.just(session(OPEN_ID, UNION_ID)));
        when(userSocialService.findByChannelAndOpenId(SocialChannel.WECHAT_MP, OPEN_ID))
                .thenReturn(Mono.just(binding));
        when(userService.getById(USER_ID)).thenReturn(Mono.just(user));
        stubSuccessfulLogin(user);

        StepVerifier.create(strategy.login(request("mp_code", STATE)))
                .assertNext(response -> assertThat(response.getAccessToken()).isEqualTo("access"))
                .verifyComplete();

        verify(userSocialService)
                .upsertBinding(eq(USER_ID), eq("default"), eq(SocialChannel.WECHAT_MP), eq(OPEN_ID), eq(UNION_ID));
    }

    @Test
    @DisplayName("归一步骤2 - UnionID 归一登录并补当前渠道绑定行")
    void unionIdNormalizationLogsInAndBinds() {
        UserEntity user = user();
        UserSocialEntity unionBinding =
                UserSocialEntity.bind(USER_ID, "default", SocialChannel.WECHAT_APP, "app_openid", UNION_ID);
        when(weChatGateway.mpValidateAuthState(STATE)).thenReturn(Mono.just(true));
        when(weChatGateway.mpAuthCodeToSession("mp_code")).thenReturn(Mono.just(session(OPEN_ID, UNION_ID)));
        when(userSocialService.findByChannelAndOpenId(SocialChannel.WECHAT_MP, OPEN_ID))
                .thenReturn(Mono.empty());
        when(userSocialService.findByUnionId(UNION_ID)).thenReturn(Mono.just(unionBinding));
        when(userService.getById(USER_ID)).thenReturn(Mono.just(user));
        stubSuccessfulLogin(user);

        StepVerifier.create(strategy.login(request("mp_code", STATE)))
                .assertNext(response -> assertThat(response.getAccessToken()).isEqualTo("access"))
                .verifyComplete();

        // UnionID 归一命中后需补 WECHAT_MP 渠道绑定行
        verify(userSocialService)
                .upsertBinding(eq(USER_ID), eq("default"), eq(SocialChannel.WECHAT_MP), eq(OPEN_ID), eq(UNION_ID));
    }

    @Test
    @DisplayName("归一步骤4 - 新用户签发绑定票据（渠道=WECHAT_MP）")
    void newUserIssuesBindTicket() {
        when(weChatGateway.mpValidateAuthState(STATE)).thenReturn(Mono.just(true));
        when(weChatGateway.mpAuthCodeToSession("mp_code")).thenReturn(Mono.just(session(OPEN_ID, UNION_ID)));
        when(userSocialService.findByChannelAndOpenId(SocialChannel.WECHAT_MP, OPEN_ID))
                .thenReturn(Mono.empty());
        when(userSocialService.findByUnionId(UNION_ID)).thenReturn(Mono.empty());
        when(userService.assertWechatRegistrationAllowed(OPEN_ID)).thenReturn(Mono.empty());
        when(authTicketService.issue(any(AuthTicketPayload.class))).thenReturn(Mono.just("bind_ticket"));

        StepVerifier.create(strategy.login(request("mp_code", STATE)))
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
        assertThat(captor.getValue().getChannel()).isEqualTo(SocialChannel.WECHAT_MP.name());
        assertThat(captor.getValue().getRegisterChannel())
                .isEqualTo(com.apex.scaffold.domain.enums.RegisterChannel.WECHAT_MP.name());
    }

    @Test
    @DisplayName("code 缺失返回参数错误")
    void missingCodeRejected() {
        StepVerifier.create(strategy.login(request(null, STATE)))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ResultCode.PARAM_ERROR.getCode()))
                .verify();
    }

    @Test
    @DisplayName("state 缺失返回参数错误")
    void missingStateRejected() {
        StepVerifier.create(strategy.login(request("mp_code", null)))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ResultCode.PARAM_ERROR.getCode()))
                .verify();
    }

    @Test
    @DisplayName("openid 缺失返回微信登录失败")
    void emptyOpenidRejected() {
        when(weChatGateway.mpValidateAuthState(STATE)).thenReturn(Mono.just(true));
        when(weChatGateway.mpAuthCodeToSession("mp_code")).thenReturn(Mono.just(session(null, null)));

        StepVerifier.create(strategy.login(request("mp_code", STATE)))
                .expectErrorSatisfies(e -> assertThat(e)
                        .isInstanceOf(BusinessException.class)
                        .hasFieldOrPropertyWithValue("code", ResultCode.WX_LOGIN_FAILED.getCode()))
                .verify();
    }

    @Test
    @DisplayName("登录类型为 WX_OAUTH")
    void loginTypeIsWxOAuth() {
        assertThat(strategy.getLoginType()).isEqualTo(LoginType.WX_OAUTH);
    }
}
