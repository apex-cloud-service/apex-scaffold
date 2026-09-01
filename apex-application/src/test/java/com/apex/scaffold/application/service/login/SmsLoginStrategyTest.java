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
 * 短信验证码登录策略单元测试类
 *
 * <p>测试 {@link SmsLoginStrategy} 的登录逻辑，包括验证码验证、已有用户登录和
 * 新用户注册场景，以及参数验证和异常处理。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SmsLoginStrategy
 * @see SmsCodeService
 * @see TokenService
 */
@ExtendWith(MockitoExtension.class)
class SmsLoginStrategyTest {

    @Mock
    private SmsCodeService smsCodeService;

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Mock
    private LoginResponseHelper loginResponseHelper;

    @Mock
    private AuthTicketService authTicketService;

    @InjectMocks
    private SmsLoginStrategy smsLoginStrategy;

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

        smsLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 手机号为空白时抛出参数异常")
    void login_PhoneIsBlank_ThrowsError() {
        request.setPhone("   ");

        smsLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 验证码为空时抛出参数异常")
    void login_SmsCodeIsNull_ThrowsError() {
        request.setSmsCode(null);

        smsLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 验证码为空白时抛出参数异常")
    void login_SmsCodeIsBlank_ThrowsError() {
        request.setSmsCode("   ");

        smsLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 验证码错误时抛出异常")
    void login_InvalidSmsCode_ThrowsError() {
        when(smsCodeService.validate("13800138000", "123456")).thenReturn(Mono.just(false));

        smsLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 已有用户登录成功")
    void login_ExistingUser_Success() {
        when(smsCodeService.validate("13800138000", "123456")).thenReturn(Mono.just(true));
        when(userService.getByPhone("13800138000")).thenReturn(Mono.just(existingUser));
        when(tokenService.generateAccessToken("test_openid", null)).thenReturn("access_token");
        when(tokenService.generateRefreshToken("test_openid", null)).thenReturn("refresh_token");
        when(tokenService.storeToken(eq("test_openid"), eq("access_token"), eq("refresh_token"), any(), any()))
                .thenReturn(Mono.empty());
        when(userService.updateLoginInfo(1L, null)).thenReturn(Mono.just(true));
        when(loginResponseHelper.build(existingUser, "access_token", "refresh_token", false, "13800138000", null))
                .thenReturn(loginResponse);

        smsLoginStrategy
                .login(request)
                .as(StepVerifier::create)
                .expectNext(loginResponse)
                .verifyComplete();

        verify(smsCodeService).validate("13800138000", "123456");
        verify(userService).getByPhone("13800138000");
        verify(tokenService).storeToken(eq("test_openid"), eq("access_token"), eq("refresh_token"), any(), any());
    }

    @Test
    @DisplayName("login - 未注册手机号返回注册票据且不创建用户")
    void login_NewUser_Success() {
        when(smsCodeService.validate("13800138000", "123456")).thenReturn(Mono.just(true));
        when(userService.getByPhone("13800138000")).thenReturn(Mono.empty());
        when(authTicketService.issue(any())).thenReturn(Mono.just("registration-ticket"));

        smsLoginStrategy
                .login(request)
                .as(StepVerifier::create)
                .assertNext(response -> {
                    assertThat(response.getNextAction()).isEqualTo("REGISTER_REQUIRED");
                    assertThat(response.getAuthTicket()).isEqualTo("registration-ticket");
                })
                .verifyComplete();

        verify(userService, never()).create(any(UserEntity.class));
    }
}
