package com.apex.scaffold.application.service.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.PasswordUtil;
import com.apex.scaffold.domain.enums.LoginType;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/**
 * 账号密码登录策略单元测试
 *
 * @author Apex Team
 */
class PasswordLoginStrategyTest {

    private UserService userService;
    private TokenService tokenService;
    private LoginResponseHelper loginResponseHelper;
    private PasswordLoginStrategy strategy;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        tokenService = mock(TokenService.class);
        loginResponseHelper = mock(LoginResponseHelper.class);
        strategy = new PasswordLoginStrategy(userService, tokenService, loginResponseHelper);
    }

    /** 构造携带账号密码的登录请求 */
    private LoginRequest request(String username, String password) {
        LoginRequest req = new LoginRequest();
        req.setLoginType("PASSWORD");
        req.setUsername(username);
        req.setPassword(password);
        req.setDeviceId("device-1");
        return req;
    }

    /** 构造已启用、携带 BCrypt 密码哈希的用户实体（UserEntity 仅 @Getter，用反射设字段） */
    private UserEntity user(Long id, String username, String rawPassword) {
        UserEntity user = new UserEntity();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "username", username);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "openId", "open-" + id);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "status", 1);
        org.springframework.test.util.ReflectionTestUtils.setField(user, "password", PasswordUtil.encryptPassword(rawPassword));
        return user;
    }

    @Nested
    @DisplayName("登录类型")
    class LoginTypeTests {

        @Test
        @DisplayName("getLoginType 应返回 PASSWORD")
        void shouldReturnPasswordType() {
            assertThat(strategy.getLoginType()).isEqualTo(LoginType.PASSWORD);
        }
    }

    @Nested
    @DisplayName("登录成功")
    class SuccessTests {

        @Test
        @DisplayName("账号密码正确时应签发 Token 并返回登录响应")
        void shouldLoginSuccessfully() {
            UserEntity user = user(1L, "admin", "secret123");
            when(userService.getByUsername("admin")).thenReturn(Mono.just(user));
            when(tokenService.generateAccessToken("open-1", "device-1")).thenReturn("access-token");
            when(tokenService.generateRefreshToken("open-1", "device-1")).thenReturn("refresh-token");
            when(tokenService.storeToken(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
            LoginResponse expected = new LoginResponse();
            expected.setAccessToken("access-token");
            when(loginResponseHelper.build(user, "access-token", "refresh-token", false)).thenReturn(expected);

            LoginResponse response = strategy.login(request("admin", "secret123")).block();

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            verify(tokenService).storeToken("open-1", "access-token", "refresh-token");
        }
    }

    @Nested
    @DisplayName("登录失败")
    class FailureTests {

        @Test
        @DisplayName("账号不存在时抛出 USER_NOT_FOUND")
        void shouldFailWhenUserNotFound() {
            when(userService.getByUsername("ghost")).thenReturn(Mono.empty());

            assertThatThrownBy(() -> strategy.login(request("ghost", "secret123")).block())
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ResultCode.USER_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("密码错误时抛出 LOGIN_FAILED")
        void shouldFailWhenPasswordWrong() {
            UserEntity user = user(1L, "admin", "correct-pass");
            when(userService.getByUsername("admin")).thenReturn(Mono.just(user));

            assertThatThrownBy(() -> strategy.login(request("admin", "wrong-pass")).block())
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ResultCode.LOGIN_FAILED.getCode());
        }

        @Test
        @DisplayName("账号禁用时抛出 LOGIN_FAILED")
        void shouldFailWhenAccountDisabled() {
            UserEntity user = user(1L, "admin", "secret123");
            user.setStatus(0);
            when(userService.getByUsername("admin")).thenReturn(Mono.just(user));

            assertThatThrownBy(() -> strategy.login(request("admin", "secret123")).block())
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ResultCode.LOGIN_FAILED.getCode());
        }

        @Test
        @DisplayName("用户名或密码为空时抛出 PARAM_ERROR")
        void shouldFailWhenCredentialsMissing() {
            assertThatThrownBy(() -> strategy.login(request("", "secret123")).block())
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getCode())
                    .isEqualTo(ResultCode.PARAM_ERROR.getCode());
        }
    }
}
