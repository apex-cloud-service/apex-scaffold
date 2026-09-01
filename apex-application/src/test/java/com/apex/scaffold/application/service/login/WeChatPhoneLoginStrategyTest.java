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
import com.apex.scaffold.domain.gateway.WeChatGateway;
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
 * 微信一键登录策略单元测试类
 *
 * <p>测试 {@link WeChatPhoneLoginStrategy} 的登录逻辑，包括手机号授权凭证验证、
 * 已有用户登录和新用户注册场景，以及参数验证和异常处理。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see WeChatPhoneLoginStrategy
 * @see WeChatGateway
 * @see TokenService
 */
@ExtendWith(MockitoExtension.class)
class WeChatPhoneLoginStrategyTest {

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

    @InjectMocks
    private WeChatPhoneLoginStrategy weChatPhoneLoginStrategy;

    private LoginRequest request;
    private LoginResponse loginResponse;
    private UserEntity existingUser;
    private UserEntity newUser;

    private WeChatGateway.WeChatPhoneResult createPhoneResult(String phoneNumber) {
        WeChatGateway.WeChatPhoneResult result = new WeChatGateway.WeChatPhoneResult();
        result.setPhoneNumber(phoneNumber);
        result.setPurePhoneNumber(phoneNumber);
        result.setCountryCode("86");
        return result;
    }

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        lenient().when(userService.assertPhoneRegistrationAllowed(anyString())).thenReturn(Mono.empty());
        request = new LoginRequest();
        request.setPhoneCode("test_phone_code");

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
    @DisplayName("login - phoneCode为空时抛出参数异常")
    void login_PhoneCodeIsNull_ThrowsError() {
        request.setPhoneCode(null);

        weChatPhoneLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - phoneCode为空白时抛出参数异常")
    void login_PhoneCodeIsBlank_ThrowsError() {
        request.setPhoneCode("   ");

        weChatPhoneLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.PARAM_ERROR.getCode());
        });
    }

    @Test
    @DisplayName("login - 获取手机号失败时抛出异常")
    void login_GetPhoneFailed_ThrowsError() {
        WeChatGateway.WeChatPhoneResult phoneResult = createPhoneResult(null);

        when(weChatGateway.getPhoneNumber("test_phone_code")).thenReturn(Mono.just(phoneResult));

        weChatPhoneLoginStrategy.login(request).as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            BusinessException be = (BusinessException) ex;
            assertThat(be.getCode()).isEqualTo(ResultCode.WX_LOGIN_FAILED.getCode());
        });
    }

    @Test
    @DisplayName("login - 已有用户登录成功")
    void login_ExistingUser_Success() {
        WeChatGateway.WeChatPhoneResult phoneResult = createPhoneResult("13800138000");

        when(weChatGateway.getPhoneNumber("test_phone_code")).thenReturn(Mono.just(phoneResult));
        when(userService.getByPhone("13800138000")).thenReturn(Mono.just(existingUser));
        when(tokenService.generateAccessToken("test_openid", null)).thenReturn("access_token");
        when(tokenService.generateRefreshToken("test_openid", null)).thenReturn("refresh_token");
        when(tokenService.storeToken(eq("test_openid"), eq("access_token"), eq("refresh_token"), any(), any()))
                .thenReturn(Mono.empty());
        when(userService.updateLoginInfo(1L, null)).thenReturn(Mono.just(true));
        when(loginResponseHelper.build(existingUser, "access_token", "refresh_token", false, "13800138000", null))
                .thenReturn(loginResponse);

        weChatPhoneLoginStrategy
                .login(request)
                .as(StepVerifier::create)
                .expectNext(loginResponse)
                .verifyComplete();

        verify(weChatGateway).getPhoneNumber("test_phone_code");
        verify(userService).getByPhone("13800138000");
        verify(tokenService).storeToken(eq("test_openid"), eq("access_token"), eq("refresh_token"), any(), any());
    }

    @Test
    @DisplayName("login - 未注册手机号返回注册票据")
    void login_NewUser_Success() {
        WeChatGateway.WeChatPhoneResult phoneResult = createPhoneResult("13900139000");

        when(weChatGateway.getPhoneNumber("test_phone_code")).thenReturn(Mono.just(phoneResult));
        when(userService.getByPhone("13900139000")).thenReturn(Mono.empty());
        when(authTicketService.issue(any())).thenReturn(Mono.just("registration-ticket"));

        weChatPhoneLoginStrategy
                .login(request)
                .as(StepVerifier::create)
                .assertNext(response -> assertThat(response.getNextAction()).isEqualTo("REGISTER_REQUIRED"))
                .verifyComplete();

        verify(userService, never()).create(any(UserEntity.class));
    }
}
