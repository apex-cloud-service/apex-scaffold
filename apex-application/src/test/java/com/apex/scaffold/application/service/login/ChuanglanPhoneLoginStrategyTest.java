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
import com.apex.scaffold.domain.gateway.ChuanglanGateway;
import com.apex.scaffold.domain.gateway.ChuanglanGateway.PhoneNumberResult;
import com.apex.scaffold.domain.model.entity.UserEntity;
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
 * 创蓝一键登录策略单元测试类
 *
 * <p>测试 {@link ChuanglanPhoneLoginStrategy} 的登录逻辑，包括闪验token验证、
 * 已有用户登录和新用户注册场景，以及参数验证和异常处理。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ChuanglanPhoneLoginStrategy
 * @see ChuanglanGateway
 * @see TokenService
 */
@ExtendWith(MockitoExtension.class)
class ChuanglanPhoneLoginStrategyTest {

    @Mock
    private ChuanglanGateway chuanglanGateway;

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Mock
    private LoginResponseHelper loginResponseHelper;

    @Mock
    private AuthTicketService authTicketService;

    @InjectMocks
    private ChuanglanPhoneLoginStrategy chuanglanPhoneLoginStrategy;

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
        request.setClToken("test_cl_token");

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
        newUser.setPhone("13900139000");
    }

    @Test
    @DisplayName("login - clToken为空时抛出参数异常")
    void login_ClTokenIsNull_ThrowsError() {
        request.setClToken(null);

        chuanglanPhoneLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - clToken为空白时抛出参数异常")
    void login_ClTokenIsBlank_ThrowsError() {
        request.setClToken("   ");

        chuanglanPhoneLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 获取手机号失败时抛出异常")
    void login_GetPhoneFailed_ThrowsError() {
        PhoneNumberResult failedResult = new PhoneNumberResult(null, false);
        failedResult.setErrorCode("E1001");
        failedResult.setErrorMessage("token无效");

        when(chuanglanGateway.getPhoneNumberByToken(eq("test_cl_token"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Mono.just(failedResult));

        chuanglanPhoneLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.LOGIN_FAILED.getCode());
        });
    }

    @Test
    @DisplayName("login - 获取到的手机号为空时抛出异常")
    void login_PhoneIsNull_ThrowsError() {
        PhoneNumberResult result = new PhoneNumberResult(null, true);

        when(chuanglanGateway.getPhoneNumberByToken(eq("test_cl_token"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Mono.just(result));

        chuanglanPhoneLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.LOGIN_FAILED.getCode());
        });
    }

    @Test
    @DisplayName("login - 已有用户登录成功")
    void login_ExistingUser_Success() {
        PhoneNumberResult phoneResult = new PhoneNumberResult("13800138000", true);

        when(chuanglanGateway.getPhoneNumberByToken(eq("test_cl_token"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Mono.just(phoneResult));
        when(userService.getByPhone("13800138000")).thenReturn(Mono.just(existingUser));
        when(tokenService.generateAccessToken("test_openid", null)).thenReturn("access_token");
        when(tokenService.generateRefreshToken("test_openid", null)).thenReturn("refresh_token");
        when(tokenService.storeToken(eq("test_openid"), eq("access_token"), eq("refresh_token"), any(), any()))
                .thenReturn(Mono.empty());
        when(userService.updateLoginInfo(1L, null)).thenReturn(Mono.just(true));
        when(loginResponseHelper.build(existingUser, "access_token", "refresh_token", false, "13800138000", null))
                .thenReturn(loginResponse);

        chuanglanPhoneLoginStrategy
                .login(request)
                .as(StepVerifier::create)
                .expectNext(loginResponse)
                .verifyComplete();

        verify(chuanglanGateway).getPhoneNumberByToken(eq("test_cl_token"), isNull(), isNull(), isNull(), isNull());
        verify(userService).getByPhone("13800138000");
        verify(tokenService).storeToken(eq("test_openid"), eq("access_token"), eq("refresh_token"), any(), any());
    }

    @Test
    @DisplayName("login - 未注册手机号返回注册票据")
    void login_NewUser_Success() {
        PhoneNumberResult phoneResult = new PhoneNumberResult("13900139000", true);

        when(chuanglanGateway.getPhoneNumberByToken(eq("test_cl_token"), isNull(), isNull(), isNull(), isNull()))
                .thenReturn(Mono.just(phoneResult));
        when(userService.getByPhone("13900139000")).thenReturn(Mono.empty());
        when(authTicketService.issue(any())).thenReturn(Mono.just("registration-ticket"));

        chuanglanPhoneLoginStrategy
                .login(request)
                .as(StepVerifier::create)
                .assertNext(response -> assertThat(response.getNextAction()).isEqualTo("REGISTER_REQUIRED"))
                .verifyComplete();

        verify(userService, never()).create(any(UserEntity.class));
    }

    @Test
    @DisplayName("getLoginType - 应返回CL_PHONE")
    void getLoginType_ShouldReturnClPhone() {
        assertThat(chuanglanPhoneLoginStrategy.getLoginType()).isEqualTo(LoginType.CL_PHONE);
    }
}
