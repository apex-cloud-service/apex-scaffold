package com.apex.scaffold.application.service.login;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.service.UserService;
import com.apex.scaffold.application.service.auth.AuthTicketService;
import com.apex.scaffold.domain.enums.LoginType;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * {@link AbstractPhoneLoginStrategy} 的单元测试
 *
 * <p>测试模板方法模式的登录流程：认证→用户查询/创建→Token生成。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("AbstractPhoneLoginStrategy 模板方法单元测试")
@ExtendWith(MockitoExtension.class)
class AbstractPhoneLoginStrategyTest {

    @Mock
    private UserService userService;

    @Mock
    private TokenService tokenService;

    @Mock
    private AuthTicketService authTicketService;

    private static final String TEST_PHONE = "13812345678";
    private static final String ACCESS_TOKEN = "access-token-xxx";
    private static final String REFRESH_TOKEN = "refresh-token-xxx";

    /** 测试用具体子类 - 覆盖buildLoginResponse以绕过LoginResponseHelper */
    static class TestPhoneLoginStrategy extends AbstractPhoneLoginStrategy {

        private final String authenticatedPhone;
        private volatile String generatedSubject;
        private volatile String generatedDeviceId;

        /**
         */
        TestPhoneLoginStrategy(
                UserService userService,
                TokenService tokenService,
                LoginResponseHelper loginResponseHelper,
                AuthTicketService authTicketService,
                String authenticatedPhone) {
            super(userService, tokenService, loginResponseHelper, authTicketService);
            this.authenticatedPhone = authenticatedPhone;
        }

        /** {@inheritDoc} */
        @Override
        protected Mono<String> authenticate(LoginRequest request) {
            return Mono.just(authenticatedPhone);
        }

        /** {@inheritDoc} */
        @Override
        public LoginType getLoginType() {
            return LoginType.WX_SMS;
        }

        /** {@inheritDoc} */
        @Override
        protected Mono<LoginResponse> buildLoginResponse(
                UserEntity user, String phone, boolean isNewUser, String deviceId, LoginRequest request) {
            // 记录生成的 subject 用于验证
            this.generatedSubject = user.getOpenId() != null ? user.getOpenId() : phone;
            this.generatedDeviceId = deviceId;
            tokenService.generateAccessToken(generatedSubject, deviceId);
            tokenService.generateRefreshToken(generatedSubject, deviceId);
            return tokenService
                    .storeToken(generatedSubject, ACCESS_TOKEN, REFRESH_TOKEN)
                    .thenReturn(new LoginResponse());
        }
    }

    /**
     * 设置{@code upTokens}。
     */
    @BeforeEach
    void setUpTokens() {
        lenient().when(userService.assertPhoneRegistrationAllowed(anyString())).thenReturn(Mono.empty());
        lenient()
                .when(tokenService.generateAccessToken(anyString(), anyString()))
                .thenReturn(ACCESS_TOKEN);
        lenient()
                .when(tokenService.generateRefreshToken(anyString(), anyString()))
                .thenReturn(REFRESH_TOKEN);
        lenient()
                .when(tokenService.storeToken(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
    }

    @Nested
    @DisplayName("login 模板方法")
    class LoginTemplateTests {
        @Test
        @DisplayName("已存在用户登录：不应创建新用户")
        void existingUserShouldNotBeNewUser() {
            UserEntity existingUser = new UserEntity();
            existingUser.setId(1L);
            existingUser.setPhone(TEST_PHONE);
            existingUser.setOpenId("openid-123");

            when(userService.getByPhone(TEST_PHONE)).thenReturn(Mono.just(existingUser));

            TestPhoneLoginStrategy strategy =
                    new TestPhoneLoginStrategy(userService, tokenService, null, authTicketService, TEST_PHONE);
            LoginRequest request = new LoginRequest();
            request.setDeviceId("device-1");

            Mono<LoginResponse> result = strategy.login(request);

            StepVerifier.create(result).expectNextCount(1).verifyComplete();
            verify(userService, never()).create(any());
        }

        @Test
        @DisplayName("未注册手机号登录：不创建用户并返回注册票据")
        void unregisteredPhoneShouldReturnRegistrationTicket() {
            when(userService.getByPhone(TEST_PHONE)).thenReturn(Mono.empty());
            when(authTicketService.issue(org.mockito.ArgumentMatchers.argThat(payload ->
                            "device-1".equals(payload.getDeviceId()) && TEST_PHONE.equals(payload.getPhone()))))
                    .thenReturn(Mono.just("registration-ticket"));

            TestPhoneLoginStrategy strategy =
                    new TestPhoneLoginStrategy(userService, tokenService, null, authTicketService, TEST_PHONE);
            LoginRequest request = new LoginRequest();
            request.setDeviceId("device-1");

            Mono<LoginResponse> result = strategy.login(request);

            StepVerifier.create(result)
                    .assertNext(response -> {
                        org.assertj.core.api.Assertions.assertThat(response.getNextAction())
                                .isEqualTo("REGISTER_REQUIRED");
                        org.assertj.core.api.Assertions.assertThat(response.getAuthTicket())
                                .isEqualTo("registration-ticket");
                    })
                    .verifyComplete();
            verify(userService, never()).create(any(UserEntity.class));
        }

        @Test
        @DisplayName("subject选择：有openId时使用openId生成Token")
        void subjectShouldUseOpenIdWhenAvailable() {
            UserEntity user = new UserEntity();
            user.setId(1L);
            user.setPhone(TEST_PHONE);
            user.setOpenId("openid-subject-test");

            when(userService.getByPhone(TEST_PHONE)).thenReturn(Mono.just(user));

            TestPhoneLoginStrategy strategy =
                    new TestPhoneLoginStrategy(userService, tokenService, null, authTicketService, TEST_PHONE);
            LoginRequest request = new LoginRequest();
            request.setDeviceId("device-1");

            Mono<LoginResponse> result = strategy.login(request);

            StepVerifier.create(result).expectNextCount(1).verifyComplete();
            verify(tokenService).generateAccessToken("openid-subject-test", "device-1");
            verify(tokenService).generateRefreshToken("openid-subject-test", "device-1");
        }

        @Test
        @DisplayName("subject选择：无openId时使用phone生成Token")
        void subjectShouldUsePhoneWhenNoOpenId() {
            UserEntity user = new UserEntity();
            user.setId(1L);
            user.setPhone(TEST_PHONE);

            when(userService.getByPhone(TEST_PHONE)).thenReturn(Mono.just(user));

            TestPhoneLoginStrategy strategy =
                    new TestPhoneLoginStrategy(userService, tokenService, null, authTicketService, TEST_PHONE);
            LoginRequest request = new LoginRequest();
            request.setDeviceId("device-1");

            Mono<LoginResponse> result = strategy.login(request);

            StepVerifier.create(result).expectNextCount(1).verifyComplete();
            verify(tokenService).generateAccessToken(TEST_PHONE, "device-1");
            verify(tokenService).generateRefreshToken(TEST_PHONE, "device-1");
        }
    }
}
