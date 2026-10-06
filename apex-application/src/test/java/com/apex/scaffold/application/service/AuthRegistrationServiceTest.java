package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.dto.RegisterRequest;
import com.apex.scaffold.application.dto.WechatBindPhoneRequest;
import com.apex.scaffold.application.service.auth.AuthTicketPayload;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.application.service.auth.AuthTicketType;
import com.apex.scaffold.application.service.login.LoginResponseHelper;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.SmsCodeService;
import com.apex.scaffold.domain.service.TokenService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 认证注册应用服务单元测试。
 *
 * @author weiqiang.ling
 */
@ExtendWith(MockitoExtension.class)
class AuthRegistrationServiceTest {

    private static final String PHONE = "13812345678";

    @Mock
    private UserService userService;

    @Mock
    private AuthTicketService ticketService;

    @Mock
    private SmsCodeService smsCodeService;

    @Mock
    private TokenService tokenService;

    @Mock
    private LoginResponseHelper loginResponseHelper;

    @Mock
    private UserSocialService userSocialService;

    private AuthRegistrationService service;

    /**
     * 验证 {@code setUp} 场景。
     */
    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient()
                .when(userService.assertPhoneRegistrationAllowed(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.empty());
        org.mockito.Mockito.lenient()
                .when(userService.assertWechatRegistrationAllowed(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(Mono.empty());
        service = new AuthRegistrationService(
                userService, ticketService, smsCodeService, tokenService, loginResponseHelper, userSocialService);
        // 默认放行社交身份绑定登记（幂等操作，多数用例不关注绑定细节）
        org.mockito.Mockito.lenient()
                .when(userSocialService.upsertBinding(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(Mono.empty());
    }

    /**
     * 验证 {@code invalidSmsDoesNotConsumeWechatTicket} 场景。
     */
    @Test
    void invalidSmsDoesNotConsumeWechatTicket() {
        WechatBindPhoneRequest request = bindRequest();
        when(smsCodeService.validate(PHONE, "123456")).thenReturn(Mono.just(false));

        StepVerifier.create(service.bindWechatPhone(request))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.PARAM_ERROR))
                .verify();
        verify(ticketService, never()).execute(anyString(), any(), any(), any(), any());
    }

    /**
     * 验证 {@code unknownWechatAndPhoneReturnsRegistrationTicket} 场景。
     */
    @Test
    void unknownWechatAndPhoneReturnsRegistrationTicket() {
        WechatBindPhoneRequest request = bindRequest();
        AuthTicketPayload wechat = wechatPayload();
        when(smsCodeService.validate(PHONE, "123456")).thenReturn(Mono.just(true));
        prepareTicketExecution("wx-ticket", AuthTicketType.WECHAT_BIND, wechat);
        when(userService.getByOpenId("wx-openid")).thenReturn(Mono.empty());
        when(userService.getByPhone(PHONE)).thenReturn(Mono.empty());
        when(ticketService.issue(any())).thenReturn(Mono.just("registration-ticket"));

        StepVerifier.create(service.bindWechatPhone(request))
                .assertNext(response -> {
                    assertThat(response.getNextAction()).isEqualTo("REGISTER_REQUIRED");
                    assertThat(response.getAuthTicket()).isEqualTo("registration-ticket");
                    assertThat(response.getAuthTicketExpiresIn()).isEqualTo(300L);
                })
                .verifyComplete();
        verify(userService, never()).create(any());
    }

    /**
     * 验证 {@code registeredPhoneWithoutWechatIsBoundAndLoggedIn} 场景。
     */
    @Test
    void registeredPhoneWithoutWechatIsBoundAndLoggedIn() {
        WechatBindPhoneRequest request = bindRequest();
        UserEntity phoneUser = user(2L, "PHONE_old", PHONE);
        prepareValidWechatBinding();
        when(userService.getByOpenId("wx-openid")).thenReturn(Mono.empty());
        when(userService.getByPhone(PHONE)).thenReturn(Mono.just(phoneUser));
        when(tokenService.deleteToken("PHONE_old")).thenReturn(Mono.just(1L));
        when(userService.update(phoneUser)).thenReturn(Mono.just(phoneUser));
        LoginResponse expected = prepareLogin(phoneUser, false);

        StepVerifier.create(service.bindWechatPhone(request))
                .expectNext(expected)
                .verifyComplete();
        assertThat(phoneUser.getOpenId()).isEqualTo("wx-openid");
        assertThat(phoneUser.getUnionId()).isEqualTo("wx-unionid");
        verify(tokenService).deleteToken("PHONE_old");
    }

    /**
     * 验证 {@code nullLegacyOpenIdCanBeBoundWithoutDeletingNullToken} 场景。
     */
    @Test
    void nullLegacyOpenIdCanBeBoundWithoutDeletingNullToken() {
        WechatBindPhoneRequest request = bindRequest();
        UserEntity phoneUser = user(2L, null, PHONE);
        prepareValidWechatBinding();
        when(userService.getByOpenId("wx-openid")).thenReturn(Mono.empty());
        when(userService.getByPhone(PHONE)).thenReturn(Mono.just(phoneUser));
        when(userService.update(phoneUser)).thenReturn(Mono.just(phoneUser));
        LoginResponse expected = prepareLogin(phoneUser, false);

        StepVerifier.create(service.bindWechatPhone(request))
                .expectNext(expected)
                .verifyComplete();
        verify(tokenService, never()).deleteToken(any());
    }

    /**
     * 验证 {@code sameWechatBindingIsIdempotent} 场景。
     */
    @Test
    void sameWechatBindingIsIdempotent() {
        WechatBindPhoneRequest request = bindRequest();
        UserEntity user = user(2L, "wx-openid", PHONE);
        prepareValidWechatBinding();
        when(userService.getByOpenId("wx-openid")).thenReturn(Mono.just(user));
        when(userService.getByPhone(PHONE)).thenReturn(Mono.just(user));
        when(userService.update(user)).thenReturn(Mono.just(user));
        LoginResponse expected = prepareLogin(user, false);

        StepVerifier.create(service.bindWechatPhone(request))
                .expectNext(expected)
                .verifyComplete();
        verify(tokenService, never()).deleteToken(any());
    }

    /**
     * 验证 {@code phoneBoundToDifferentWechatIsRejected} 场景。
     */
    @Test
    void phoneBoundToDifferentWechatIsRejected() {
        prepareValidWechatBinding();
        when(userService.getByOpenId("wx-openid")).thenReturn(Mono.empty());
        when(userService.getByPhone(PHONE)).thenReturn(Mono.just(user(2L, "other-wechat", PHONE)));

        StepVerifier.create(service.bindWechatPhone(bindRequest()))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.PHONE_ALREADY_BOUND))
                .verify();
    }

    /**
     * 验证 {@code wechatBoundToDifferentPhoneIsRejected} 场景。
     */
    @Test
    void wechatBoundToDifferentPhoneIsRejected() {
        prepareValidWechatBinding();
        when(userService.getByOpenId("wx-openid")).thenReturn(Mono.just(user(1L, "wx-openid", "13912345678")));

        StepVerifier.create(service.bindWechatPhone(bindRequest()))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.WECHAT_ALREADY_BOUND))
                .verify();
        verify(userService, never()).getByPhone(PHONE);
    }

    /**
     * 验证 {@code twoLegacyRowsAreNotSilentlyMerged} 场景。
     */
    @Test
    void twoLegacyRowsAreNotSilentlyMerged() {
        prepareValidWechatBinding();
        when(userService.getByOpenId("wx-openid")).thenReturn(Mono.just(user(1L, "wx-openid", null)));
        when(userService.getByPhone(PHONE)).thenReturn(Mono.just(user(2L, "PHONE_old", PHONE)));

        StepVerifier.create(service.bindWechatPhone(bindRequest()))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.WECHAT_ALREADY_BOUND))
                .verify();
    }

    /**
     * 验证 {@code phoneRegistrationWithNicknameCreatesUserAndLogsIn} 场景。
     */
    @Test
    void phoneRegistrationWithNicknameCreatesUserAndLogsIn() {
        RegisterRequest request = registerRequest("Alice");
        AuthTicketPayload payload = registrationPayload(null);
        prepareTicketExecution("registration-ticket", AuthTicketType.REGISTRATION, payload);
        when(userService.getByPhone(PHONE)).thenReturn(Mono.empty());
        when(userService.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        prepareGeneratedTokenLogin();

        StepVerifier.create(service.register(request))
                .assertNext(response -> assertThat(response.getNextAction()).isEqualTo("AUTHENTICATED"))
                .verifyComplete();
        verify(userService)
                .create(org.mockito.ArgumentMatchers.argThat(
                        user -> user.getOpenId().startsWith("PHONE_")
                                && user.getNickName().equals("Alice")
                                && user.getBirthday().equals(LocalDate.of(2000, 1, 2))
                                && user.getGender() == 2));
    }

    /**
     * 验证 {@code blankNicknameGetsApexUserAndEightDigits} 场景。
     */
    @Test
    void blankNicknameGetsApexUserAndEightDigits() {
        RegisterRequest request = registerRequest(" ");
        prepareTicketExecution("registration-ticket", AuthTicketType.REGISTRATION, registrationPayload(null));
        when(userService.getByPhone(PHONE)).thenReturn(Mono.empty());
        when(userService.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        prepareGeneratedTokenLogin();

        StepVerifier.create(service.register(request)).expectNextCount(1).verifyComplete();
        verify(userService).create(org.mockito.ArgumentMatchers.argThat(user -> user.getNickName()
                .matches("Apex用户\\d{8}")));
    }

    /**
     * 验证 {@code wechatRegistrationCreatesSingleCompleteAccount} 场景。
     */
    @Test
    void wechatRegistrationCreatesSingleCompleteAccount() {
        RegisterRequest request = registerRequest(null);
        AuthTicketPayload payload = registrationPayload("wx-openid");
        prepareTicketExecution("registration-ticket", AuthTicketType.REGISTRATION, payload);
        when(userService.getByPhone(PHONE)).thenReturn(Mono.empty());
        when(userService.getByOpenId("wx-openid")).thenReturn(Mono.empty());
        when(userService.create(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        prepareGeneratedTokenLogin();

        StepVerifier.create(service.register(request)).expectNextCount(1).verifyComplete();
        verify(userService)
                .create(org.mockito.ArgumentMatchers.argThat(
                        user -> user.getOpenId().equals("wx-openid")
                                && user.getPhone().equals(PHONE)
                                && user.getAccessToken().equals("wx-access")));
    }

    /**
     * 验证 {@code legacyWechatRowIsCompletedInsteadOfDuplicated} 场景。
     */
    @Test
    void legacyWechatRowIsCompletedInsteadOfDuplicated() {
        RegisterRequest request = registerRequest("Legacy");
        UserEntity legacy = user(1L, "wx-openid", null);
        prepareTicketExecution("registration-ticket", AuthTicketType.REGISTRATION, registrationPayload("wx-openid"));
        when(userService.getByPhone(PHONE)).thenReturn(Mono.empty());
        when(userService.getByOpenId("wx-openid")).thenReturn(Mono.just(legacy));
        when(userService.update(legacy)).thenReturn(Mono.just(legacy));
        prepareGeneratedTokenLogin();

        StepVerifier.create(service.register(request)).expectNextCount(1).verifyComplete();
        verify(userService, never()).create(any());
        assertThat(legacy.getPhone()).isEqualTo(PHONE);
        assertThat(legacy.getBirthday()).isEqualTo(LocalDate.of(2000, 1, 2));
    }

    /**
     * 验证 {@code retryAfterUserCreationAndTokenFailureLogsInExistingIdentity} 场景。
     */
    @Test
    void retryAfterUserCreationAndTokenFailureLogsInExistingIdentity() {
        RegisterRequest request = registerRequest("Retry");
        prepareTicketExecution("registration-ticket", AuthTicketType.REGISTRATION, registrationPayload(null));
        UserEntity created = user(2L, "PHONE_created", PHONE);
        when(userService.getByPhone(PHONE)).thenReturn(Mono.empty(), Mono.just(created));
        when(userService.create(any())).thenReturn(Mono.just(created));
        when(userService.updateLoginInfo(eq(2L), anyString())).thenReturn(Mono.just(true));
        when(tokenService.generateAccessToken("PHONE_created", "device")).thenReturn("access");
        when(tokenService.generateRefreshToken("PHONE_created", "device")).thenReturn("refresh");
        when(tokenService.storeToken(eq("PHONE_created"), eq("access"), eq("refresh"), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("token store unavailable")), Mono.empty());
        LoginResponse expected = new LoginResponse();
        expected.setNextAction("AUTHENTICATED");
        when(loginResponseHelper.build(created, "access", "refresh", true, PHONE, "127.0.0.1"))
                .thenReturn(expected);

        StepVerifier.create(service.register(request))
                .expectErrorMatches(error -> error instanceof IllegalStateException)
                .verify();
        StepVerifier.create(service.register(request)).expectNext(expected).verifyComplete();
        verify(userService).create(any());
    }

    /**
     * 验证 {@code matchingExistingPhoneIsIdempotentButDifferentWechatIsRejected} 场景。
     */
    @Test
    void matchingExistingPhoneIsIdempotentButDifferentWechatIsRejected() {
        RegisterRequest request = registerRequest("name");
        prepareTicketExecution("registration-ticket", AuthTicketType.REGISTRATION, registrationPayload(null));
        UserEntity phoneUser = user(2L, "PHONE_x", PHONE);
        when(userService.getByPhone(PHONE)).thenReturn(Mono.just(phoneUser));
        prepareGeneratedTokenLogin();

        StepVerifier.create(service.register(request)).expectNextCount(1).verifyComplete();
        verify(userService, never()).create(any());

        AuthTicketPayload wxPayload = registrationPayload("wx-openid");
        prepareTicketExecution("registration-ticket-2", AuthTicketType.REGISTRATION, wxPayload);
        when(userService.getByPhone(PHONE)).thenReturn(Mono.empty());
        when(userService.getByOpenId("wx-openid")).thenReturn(Mono.just(user(1L, "wx-openid", "13912345678")));
        request.setRegistrationTicket("registration-ticket-2");
        StepVerifier.create(service.register(request))
                .expectErrorSatisfies(error -> assertCode(error, ResultCode.USER_ALREADY_EXISTS))
                .verify();
    }

    /**
     * 验证 {@code invalidRegistrationProfileIsRejectedBeforeTicketConsumption} 场景。
     */
    @Test
    void invalidRegistrationProfileIsRejectedBeforeTicketConsumption() {
        RegisterRequest invalid = registerRequest("name");
        invalid.setBirthday(LocalDate.now());

        assertThatThrownBy(() -> service.register(invalid))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertCode(error, ResultCode.PARAM_ERROR));
        verify(ticketService, never()).execute(anyString(), any(), any(), any(), any());

        invalid.setBirthday(LocalDate.of(2000, 1, 1));
        invalid.setGender(0);
        assertThatThrownBy(() -> service.register(invalid)).isInstanceOf(BusinessException.class);
    }

    /**
     * 预置短信校验通过的有效微信绑定场景。
     *
     * <p>先让短信校验返回成功，再预置微信绑定票据的执行行为。</p>
     */
    private void prepareValidWechatBinding() {
        when(smsCodeService.validate(PHONE, "123456")).thenReturn(Mono.just(true));
        prepareTicketExecution("wx-ticket", AuthTicketType.WECHAT_BIND, wechatPayload());
    }

    /**
     * 预置票据执行行为，使票据回调实际执行给定动作。
     *
     * @param ticket  票据标识
     * @param type    票据类型
     * @param payload 票据关联的载荷
     */
    @SuppressWarnings("unchecked")
    private void prepareTicketExecution(String ticket, AuthTicketType type, AuthTicketPayload payload) {
        when(ticketService.execute(eq(ticket), eq(type), eq("device"), eq(LoginResponse.class), any(Function.class)))
                .thenAnswer(invocation -> {
                    Function<AuthTicketPayload, Mono<LoginResponse>> action = invocation.getArgument(4);
                    return action.apply(payload);
                });
    }

    /**
     * 预置普通登录的依赖行为并返回预期登录结果。
     *
     * @param user    登录用户
     * @param newUser 是否为注册新用户
     * @return 预期的登录结果
     */
    private LoginResponse prepareLogin(UserEntity user, boolean newUser) {
        when(userService.updateLoginInfo(eq(user.getId()), anyString())).thenReturn(Mono.just(true));
        when(tokenService.generateAccessToken("wx-openid", "device")).thenReturn("access");
        when(tokenService.generateRefreshToken("wx-openid", "device")).thenReturn("refresh");
        when(tokenService.storeToken(eq("wx-openid"), eq("access"), eq("refresh"), any(), any()))
                .thenReturn(Mono.empty());
        LoginResponse expected = new LoginResponse();
        expected.setNextAction("AUTHENTICATED");
        when(loginResponseHelper.build(user, "access", "refresh", newUser, PHONE, "127.0.0.1"))
                .thenReturn(expected);
        return expected;
    }

    /**
     * 预置由系统生成 Token 的注册登录行为。
     */
    private void prepareGeneratedTokenLogin() {
        when(userService.updateLoginInfo(any(), anyString())).thenReturn(Mono.just(true));
        when(tokenService.generateAccessToken(anyString(), eq("device"))).thenReturn("access");
        when(tokenService.generateRefreshToken(anyString(), eq("device"))).thenReturn("refresh");
        when(tokenService.storeToken(anyString(), eq("access"), eq("refresh"), any(), any()))
                .thenReturn(Mono.empty());
        when(loginResponseHelper.build(any(), eq("access"), eq("refresh"), eq(true), eq(PHONE), eq("127.0.0.1")))
                .thenAnswer(invocation -> {
                    LoginResponse response = new LoginResponse();
                    response.setNextAction("AUTHENTICATED");
                    return response;
                });
    }

    /**
     * 构造微信绑定手机号请求。
     *
     * @return 微信绑定请求
     */
    private WechatBindPhoneRequest bindRequest() {
        WechatBindPhoneRequest request = new WechatBindPhoneRequest();
        request.setWechatTicket("wx-ticket");
        request.setPhone(PHONE);
        request.setSmsCode("123456");
        request.setDeviceId("device");
        request.setClientIp("127.0.0.1");
        return request;
    }

    /**
     * 构造注册请求。
     *
     * @param nickname 昵称
     * @return 注册请求
     */
    private RegisterRequest registerRequest(String nickname) {
        RegisterRequest request = new RegisterRequest();
        request.setRegistrationTicket("registration-ticket");
        request.setGender(2);
        request.setBirthday(LocalDate.of(2000, 1, 2));
        request.setNickName(nickname);
        request.setDeviceId("device");
        request.setClientIp("127.0.0.1");
        return request;
    }

    /**
     * 构造微信绑定场景的票据载荷。
     *
     * @return 微信绑定票据载荷
     */
    private AuthTicketPayload wechatPayload() {
        AuthTicketPayload payload = new AuthTicketPayload();
        payload.setType(AuthTicketType.WECHAT_BIND);
        payload.setDeviceId("device");
        payload.setOpenId("wx-openid");
        payload.setUnionId("wx-unionid");
        payload.setWechatAccessToken("wx-access");
        payload.setWechatRefreshToken("wx-refresh");
        payload.setWechatTokenExpireTime(LocalDateTime.now().plusHours(2));
        return payload;
    }

    /**
     * 构造注册场景的票据载荷。
     *
     * @param openId 微信 OpenId
     * @return 注册票据载荷
     */
    private AuthTicketPayload registrationPayload(String openId) {
        AuthTicketPayload payload = wechatPayload();
        payload.setType(AuthTicketType.REGISTRATION);
        payload.setPhone(PHONE);
        payload.setOpenId(openId);
        return payload;
    }

    /**
     * 构造指定属性的用户实体。
     *
     * @param id     用户 ID
     * @param openId 微信 OpenId
     * @param phone  手机号
     * @return 用户实体
     */
    private UserEntity user(Long id, String openId, String phone) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setOpenId(openId);
        user.setPhone(phone);
        return user;
    }

    /**
     * 断言异常为业务异常且错误码与预期一致。
     *
     * @param error    捕获的异常
     * @param expected 期望的错误码
     */
    private void assertCode(Throwable error, ResultCode expected) {
        assertThat(error).isInstanceOf(BusinessException.class);
        assertThat(((BusinessException) error).getCode()).isEqualTo(expected.getCode());
    }
}
