package com.apex.scaffold.application.service.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.enums.LoginType;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.SmsCodeService;
import com.apex.scaffold.domain.service.TokenService;
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
 * 创蓝短信验证码登录策略单元测试类
 *
 * <p>测试 {@link ChuanglanSmsLoginStrategy} 的登录逻辑，包括验证码验证、
 * 已有用户登录和新用户注册场景，以及参数验证和异常处理。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ChuanglanSmsLoginStrategy
 * @see SmsCodeService
 * @see TokenService
 */
@ExtendWith(MockitoExtension.class)
class ChuanglanSmsLoginStrategyTest {

    @Mock
    private SmsCodeService chuanglanSmsCodeService;

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Mock
    private LoginResponseHelper loginResponseHelper;

    @Mock
    private AuthTicketService authTicketService;

    @InjectMocks
    private ChuanglanSmsLoginStrategy chuanglanSmsLoginStrategy;

    private LoginRequest request;
    private LoginResponse loginResponse;
    private UserEntity existingUser;
    private UserEntity newUser;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        lenient().when(userService.assertPhoneRegistrationAllowed(anyString())).thenReturn(Mono.empty());
        request = new LoginRequest();
        request.setPhone("13800138000");
        request.setSmsCode("123456");

        loginResponse = new LoginResponse();
        loginResponse.setAccessToken("access_token");
        loginResponse.setRefreshToken("refresh_token");
        loginResponse.setIsNewUser(false);

        existingUser = new UserEntity();
        existingUser.setId(1L);
        existingUser.setOpenId("test_openid");
        existingUser.setPhone("13800138000");

        newUser = new UserEntity();
        newUser.setId(2L);
        newUser.setPhone("13800138000");
    }

    @Test
    @DisplayName("login - 手机号为空时抛出参数异常")
    void login_PhoneIsNull_ThrowsError() {
        request.setPhone(null);

        chuanglanSmsLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 手机号为空白时抛出参数异常")
    void login_PhoneIsBlank_ThrowsError() {
        request.setPhone("   ");

        chuanglanSmsLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 验证码为空时抛出参数异常")
    void login_SmsCodeIsNull_ThrowsError() {
        request.setSmsCode(null);

        chuanglanSmsLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 验证码为空白时抛出参数异常")
    void login_SmsCodeIsBlank_ThrowsError() {
        request.setSmsCode("   ");

        chuanglanSmsLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 验证码错误时抛出异常")
    void login_InvalidSmsCode_ThrowsError() {
        when(chuanglanSmsCodeService.validate("13800138000", "123456")).thenReturn(Mono.just(false));

        chuanglanSmsLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 已有用户登录成功")
    void login_ExistingUser_Success() {
        when(chuanglanSmsCodeService.validate("13800138000", "123456")).thenReturn(Mono.just(true));
        when(userService.getByPhone("13800138000")).thenReturn(Mono.just(existingUser));
        when(tokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("access_token");
        when(tokenService.generateRefreshToken(any(), any(), any(), any())).thenReturn("refresh_token");
        when(tokenService.storeSessionToken(
                        eq("test_openid"), any(), eq("access_token"), eq("refresh_token"), any(), any()))
                .thenReturn(Mono.empty());
        when(userService.updateLoginInfo(1L, null)).thenReturn(Mono.just(true));
        when(loginResponseHelper.build(existingUser, "access_token", "refresh_token", false, "13800138000", null))
                .thenReturn(loginResponse);

        chuanglanSmsLoginStrategy
                .login(request)
                .as(StepVerifier::create)
                .expectNext(loginResponse)
                .verifyComplete();

        verify(chuanglanSmsCodeService).validate("13800138000", "123456");
        verify(userService).getByPhone("13800138000");
        verify(tokenService)
                .storeSessionToken(eq("test_openid"), any(), eq("access_token"), eq("refresh_token"), any(), any());
    }

    @Test
    @DisplayName("login - 未注册手机号返回注册票据")
    void login_NewUser_Success() {
        when(chuanglanSmsCodeService.validate("13800138000", "123456")).thenReturn(Mono.just(true));
        when(userService.getByPhone("13800138000")).thenReturn(Mono.empty());
        when(authTicketService.issue(any())).thenReturn(Mono.just("registration-ticket"));

        chuanglanSmsLoginStrategy
                .login(request)
                .as(StepVerifier::create)
                .assertNext(response -> assertThat(response.getNextAction()).isEqualTo("REGISTER_REQUIRED"))
                .verifyComplete();

        verify(userService, never()).create(any(UserEntity.class));
    }

    @Test
    @DisplayName("getLoginType - 应返回CL_SMS")
    void getLoginType_ShouldReturnClSms() {
        assertThat(chuanglanSmsLoginStrategy.getLoginType()).isEqualTo(LoginType.CL_SMS);
    }
}
