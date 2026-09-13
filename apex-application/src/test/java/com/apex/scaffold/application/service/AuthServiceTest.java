package com.apex.scaffold.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.application.assembler.UserAssembler;
import com.apex.scaffold.application.config.LoginProtectionProperties;
import com.apex.scaffold.application.dto.LoginRequest;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.application.dto.SendSmsRequest;
import com.apex.scaffold.application.dto.WxPhoneRequest;
import com.apex.scaffold.application.security.CaptchaService;
import com.apex.scaffold.application.service.login.LoginStrategy;
import com.apex.scaffold.application.service.login.LoginStrategyProvider;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.gateway.ChuanglanGateway;
import com.apex.scaffold.domain.gateway.WeChatGateway;
import com.apex.scaffold.domain.gateway.WeChatGateway.WeChatPhoneResult;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.TokenBlacklistService;
import com.apex.scaffold.domain.service.TokenService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 认证服务单元测试类
 *
 * <p>测试 AuthService 的核心业务逻辑，包括登录、Token刷新、退出登录、手机号绑定及短信验证码发送功能
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private TokenService tokenService;

    @Mock
    private TokenBlacklistService tokenBlacklistService;

    @Mock
    private UserService userService;

    @Mock
    private LoginStrategyProvider loginStrategyProvider;

    @Mock
    private WeChatGateway weChatGateway;

    @Mock
    private ChuanglanGateway chuanglanGateway;

    @Mock
    private SmsVerificationCoordinator smsVerificationCoordinator;

    private ExecutorService ioTaskExecutor;

    private AuthService authService;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private LoginProtectionProperties loginProtectionProperties;

    @Mock
    private CaptchaService captchaService;

    @Mock
    private LoginLogService loginLogService;

    @Mock
    private UserAssembler userAssembler;

    private UserEntity user;
    private LoginResponse loginResponse;
    private LoginRequest loginRequest;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        ioTaskExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "auth-service-test-io");
            thread.setDaemon(true);
            return thread;
        });
        authService = new AuthService(
                tokenService,
                tokenBlacklistService,
                userService,
                loginStrategyProvider,
                weChatGateway,
                chuanglanGateway,
                smsVerificationCoordinator,
                ioTaskExecutor,
                redisTemplate,
                loginProtectionProperties,
                captchaService,
                loginLogService,
                userAssembler);
        ReflectionTestUtils.setField(authService, "accessTokenExpireMs", 7200000L);
        lenient()
                .when(loginLogService.record(any(com.apex.scaffold.domain.model.entity.LoginLogEntity.class)))
                .thenReturn(Mono.empty());

        user = new UserEntity();
        user.setId(1L);
        user.setOpenId("test_openid");
        user.setNickName("TestUser");

        loginResponse = new LoginResponse();
        loginResponse.setAccessToken("access_token");
        loginResponse.setRefreshToken("refresh_token");
        loginResponse.setTokenType("Bearer");
        loginResponse.setExpiresIn(7200L);
        loginResponse.setOpenId("test_openid");

        loginRequest = new LoginRequest();
        loginRequest.setLoginType("WX_CODE");
    }

    @AfterEach
    void tearDown() {
        ioTaskExecutor.shutdownNow();
    }

    @Test
    @DisplayName("login - 成功登录")
    void login_Success() {
        LoginStrategy strategy = mock(LoginStrategy.class);
        when(loginStrategyProvider.getStrategy("WX_CODE")).thenReturn(strategy);
        when(strategy.login(loginRequest)).thenReturn(Mono.just(loginResponse));

        authService
                .login(loginRequest)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getAccessToken()).isEqualTo("access_token");
                    assertThat(result.getRefreshToken()).isEqualTo("refresh_token");
                    assertThat(result.getTokenType()).isEqualTo("Bearer");
                    assertThat(result.getExpiresIn()).isEqualTo(7200L);
                    assertThat(result.getOpenId()).isEqualTo("test_openid");
                })
                .verifyComplete();

        verify(loginStrategyProvider).getStrategy("WX_CODE");
    }

    @Test
    @DisplayName("login - 暴力破解检查完成后应继续执行登录策略")
    void login_WithClientIpContinuesAfterBruteForceCheck() {
        String clientIp = "127.0.0.1";
        String ipKey = "login:fail:ip:" + clientIp;
        String phone = "13800138000";
        String usernameKey = "login:fail:username:" + phone;
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        LoginStrategy strategy = mock(LoginStrategy.class);

        loginRequest.setLoginType("CL_SMS");
        loginRequest.setPhone(phone);
        // Mockito 对未打桩的 Boolean 方法默认返回 FALSE，此处必须显式开启登录保护，
        // 否则新逻辑会走 enabled=false 跳过防护的路径，checkBruteForce 不会执行。
        when(loginProtectionProperties.getEnabled()).thenReturn(true);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ipKey)).thenReturn(Mono.empty());
        when(valueOperations.get(usernameKey)).thenReturn(Mono.empty());
        when(valueOperations.delete(ipKey)).thenReturn(Mono.just(true));
        when(valueOperations.delete(usernameKey)).thenReturn(Mono.just(true));
        when(loginStrategyProvider.getStrategy("CL_SMS")).thenReturn(strategy);
        when(strategy.login(loginRequest)).thenReturn(Mono.just(loginResponse));

        authService
                .login(loginRequest, clientIp)
                .as(StepVerifier::create)
                .expectNext(loginResponse)
                .verifyComplete();

        verify(strategy).login(loginRequest);
    }

    @Test
    @DisplayName("login - enabled=false 时应跳过暴力破解检查与验证码校验直接登录")
    void login_ProtectionDisabledSkipsChecks() {
        String clientIp = "127.0.0.1";
        LoginStrategy strategy = mock(LoginStrategy.class);
        when(loginProtectionProperties.getEnabled()).thenReturn(false);
        when(loginStrategyProvider.getStrategy("WX_CODE")).thenReturn(strategy);
        when(strategy.login(loginRequest)).thenReturn(Mono.just(loginResponse));

        authService
                .login(loginRequest, clientIp)
                .as(StepVerifier::create)
                .expectNext(loginResponse)
                .verifyComplete();

        verify(strategy).login(loginRequest);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("login - IP失败次数达到阈值且未携带验证码时应返回CAPTCHA_REQUIRED")
    void login_CaptchaEscalationRequiresCaptcha() {
        String clientIp = "127.0.0.1";
        String ipKey = "login:fail:ip:" + clientIp;
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        // Mockito 未打桩的 Boolean 默认返回 FALSE，须显式开启登录保护，否则跳过验证码升级逻辑。
        when(loginProtectionProperties.getEnabled()).thenReturn(true);
        when(loginProtectionProperties.getCaptchaAfterFailures()).thenReturn(3);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ipKey)).thenReturn(Mono.just("3"));

        authService.login(loginRequest, clientIp).as(StepVerifier::create).verifyErrorSatisfies(error -> {
            assertThat(error).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.CAPTCHA_REQUIRED.getCode());
        });
        verify(loginStrategyProvider, never()).getStrategy(anyString());
    }

    @Test
    @DisplayName("login - IP失败次数达到阈值且验证码错误时应返回CAPTCHA_INVALID")
    void login_CaptchaEscalationInvalidCaptcha() {
        String clientIp = "127.0.0.1";
        String ipKey = "login:fail:ip:" + clientIp;
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        loginRequest.setCaptchaId("captcha-1");
        loginRequest.setCaptchaCode("wrong");
        // Mockito 未打桩的 Boolean 默认返回 FALSE，须显式开启登录保护，否则跳过验证码升级逻辑。
        when(loginProtectionProperties.getEnabled()).thenReturn(true);
        when(loginProtectionProperties.getCaptchaAfterFailures()).thenReturn(3);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ipKey)).thenReturn(Mono.just("5"));
        when(captchaService.verify("captcha-1", "wrong")).thenReturn(Mono.just(false));

        authService.login(loginRequest, clientIp).as(StepVerifier::create).verifyErrorSatisfies(error -> {
            assertThat(error).isInstanceOf(BusinessException.class);
            assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.CAPTCHA_INVALID.getCode());
        });
        verify(loginStrategyProvider, never()).getStrategy(anyString());
    }

    @Test
    @DisplayName("login - IP失败次数达到阈值但验证码正确时应继续登录")
    void login_CaptchaEscalationPassesWithValidCaptcha() {
        String clientIp = "127.0.0.1";
        String ipKey = "login:fail:ip:" + clientIp;
        String phone = "13800138000";
        String usernameKey = "login:fail:username:" + phone;
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        LoginStrategy strategy = mock(LoginStrategy.class);
        loginRequest.setLoginType("CL_SMS");
        loginRequest.setPhone(phone);
        loginRequest.setCaptchaId("captcha-1");
        loginRequest.setCaptchaCode("correct");
        // Mockito 未打桩的 Boolean 默认返回 FALSE，须显式开启登录保护，否则跳过验证码升级逻辑。
        when(loginProtectionProperties.getEnabled()).thenReturn(true);
        when(loginProtectionProperties.getCaptchaAfterFailures()).thenReturn(3);
        // 验证码通过后仍会执行 checkBruteForce（IP 维度 failCount=3），
        // 未打桩的 Integer 默认返回 0 会误触发 LOGIN_LOCKED，须显式给出 IP 最大失败阈值。
        when(loginProtectionProperties.getIpMaxAttempts()).thenReturn(20);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(ipKey)).thenReturn(Mono.just("3"));
        when(valueOperations.get(usernameKey)).thenReturn(Mono.empty());
        when(valueOperations.delete(ipKey)).thenReturn(Mono.just(true));
        when(valueOperations.delete(usernameKey)).thenReturn(Mono.just(true));
        when(captchaService.verify("captcha-1", "correct")).thenReturn(Mono.just(true));
        when(loginStrategyProvider.getStrategy("CL_SMS")).thenReturn(strategy);
        when(strategy.login(loginRequest)).thenReturn(Mono.just(loginResponse));

        authService
                .login(loginRequest, clientIp)
                .as(StepVerifier::create)
                .expectNext(loginResponse)
                .verifyComplete();

        verify(strategy).login(loginRequest);
        verify(captchaService).verify("captcha-1", "correct");
    }

    /**
     * 刷新{@code token_Success}。
     */
    @Test
    @DisplayName("refreshToken - 成功刷新Token（含用户信息补全）")
    void refreshToken_Success() {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.get("sub")).thenReturn("test_openid");
        when(tokenService.parseToken("valid_refresh_token")).thenReturn(claims);
        // v3.1.0: Token 轮换在分布式锁内原子完成（rotateRefreshToken），
        // 旧令牌单次消费、写入新令牌对成功后拉黑旧令牌。
        when(tokenService.generateAccessToken("test_openid", null)).thenReturn("new_access_token");
        when(tokenService.generateRefreshToken("test_openid", null)).thenReturn("new_refresh_token");
        when(tokenService.rotateRefreshToken(
                        eq("test_openid"),
                        eq("valid_refresh_token"),
                        eq("new_access_token"),
                        eq("new_refresh_token"),
                        any(),
                        any()))
                .thenReturn(Mono.just(true));
        // 模拟用户查询，验证刷新Token后返回用户信息（含性别）
        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(userAssembler.toLoginResponse(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity entity = invocation.getArgument(0);
            LoginResponse response = new LoginResponse();
            response.setOpenId(entity.getOpenId());
            response.setUserInfo(new LoginResponse.UserInfo());
            response.getUserInfo().setNickName(entity.getNickName());
            return response;
        });

        authService
                .refreshToken("valid_refresh_token", null)
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result.getAccessToken()).isEqualTo("new_access_token");
                    assertThat(result.getRefreshToken()).isEqualTo("new_refresh_token");
                    assertThat(result.getTokenType()).isEqualTo("Bearer");
                    assertThat(result.getExpiresIn()).isEqualTo(7200L);
                    assertThat(result.getOpenId()).isEqualTo("test_openid");
                    // 验证刷新后包含用户信息
                    assertThat(result.getUserInfo()).isNotNull();
                })
                .verifyComplete();
    }

    /**
     * 刷新{@code token_Invalid}。
     */
    @Test
    @DisplayName("refreshToken - refreshToken已失效")
    void refreshToken_Invalid() {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.get("sub")).thenReturn("test_openid");
        when(claims.get("deviceId")).thenReturn(null);
        when(tokenService.parseToken("invalid_refresh_token")).thenReturn(claims);
        // 旧令牌不匹配/已被消费时 rotateRefreshToken 返回 false，应判定为未授权。
        when(tokenService.rotateRefreshToken(
                        eq("test_openid"), eq("invalid_refresh_token"), any(), any(), any(), any()))
                .thenReturn(Mono.just(false));
        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));

        authService
                .refreshToken("invalid_refresh_token", null)
                .as(StepVerifier::create)
                .verifyErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(ex).hasMessage("未授权");
                });
    }

    /**
     * 验证已注销身份的旧 RefreshToken 在重注册后不能复活，且不读取用户信息。
     */
    @Test
    @DisplayName("refreshToken - 注销身份的旧RefreshToken不能在重注册后复活")
    void refreshToken_RevokedSubject() {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.get("sub")).thenReturn("test_openid");
        when(tokenService.parseToken("revoked_refresh_token")).thenReturn(claims);
        when(tokenBlacklistService.isSubjectRevoked(eq("test_openid"), any())).thenReturn(Mono.just(true));

        authService
                .refreshToken("revoked_refresh_token", null)
                .as(StepVerifier::create)
                .verifyErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) error).getCode()).isEqualTo(ResultCode.UNAUTHORIZED.getCode());
                });

        org.mockito.Mockito.verifyNoInteractions(userService);
    }

    /**
     * 刷新{@code token_Expired}。
     *
     * <p>v3.1.0 验证：RefreshToken 过期（{@link io.jsonwebtoken.ExpiredJwtException}）
     * 应返回 {@link ResultCode#TOKEN_EXPIRED}(40101) 专用错误码，
     * 而非通用 {@link ResultCode#UNAUTHORIZED}(401)，
     * 确保前端能精确识别"需重新登录"场景并引导用户跳转登录页面。</p>
     */
    @Test
    @DisplayName("refreshToken - RefreshToken已过期应返回TOKEN_EXPIRED而非UNAUTHORIZED")
    void refreshToken_Expired() {
        io.jsonwebtoken.ExpiredJwtException expiredException = mock(io.jsonwebtoken.ExpiredJwtException.class);
        when(tokenService.parseToken("expired_refresh_token")).thenThrow(expiredException);

        authService
                .refreshToken("expired_refresh_token", null)
                .as(StepVerifier::create)
                .verifyErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode())
                            .as("ExpiredJwtException 应返回 TOKEN_EXPIRED(40101)")
                            .isEqualTo(ResultCode.TOKEN_EXPIRED.getCode());
                    assertThat(be.getMessage()).contains("RefreshToken 已过期");
                });
    }

    /**
     * 刷新{@code token_DeviceMismatch}。
     */
    @Test
    @DisplayName("refreshToken - 设备指纹不匹配")
    void refreshToken_DeviceMismatch() {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.get("sub")).thenReturn("test_openid");
        when(claims.get("deviceId")).thenReturn("device_a");
        when(tokenService.parseToken("valid_refresh_token")).thenReturn(claims);

        authService
                .refreshToken("valid_refresh_token", "device_b")
                .as(StepVerifier::create)
                .verifyErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(ex).hasMessage("RefreshToken已失效");
                });
    }

    @Test
    @DisplayName("logout - 成功退出登录")
    void logout_Success() {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.get("sub")).thenReturn("test_openid");
        when(tokenService.parseToken("bearer_token")).thenReturn(claims);
        when(tokenBlacklistService.add("bearer_token", 7200000)).thenReturn(Mono.empty());
        /*
         * v3.1.0: 登出时也需将 RefreshToken 加入黑名单。
         * 先从 Redis 读取已存储的 RefreshToken，再调用 blacklist。
         */
        when(tokenService.findRefreshToken("test_openid")).thenReturn(Mono.just("stored_refresh_token"));
        when(tokenBlacklistService.add("stored_refresh_token", 7200000)).thenReturn(Mono.empty());
        when(tokenService.deleteToken("test_openid")).thenReturn(Mono.empty());

        authService
                .logout("bearer_token", "test_openid")
                .as(StepVerifier::create)
                .verifyComplete();

        verify(tokenBlacklistService).add("bearer_token", 7200000);
        verify(tokenService).deleteToken("test_openid");
    }

    @Test
    @DisplayName("logout - Token与用户不匹配")
    void logout_TokenMismatch() {
        io.jsonwebtoken.Claims claims = mock(io.jsonwebtoken.Claims.class);
        when(claims.get("sub")).thenReturn("test_openid");
        when(tokenService.parseToken("bearer_token")).thenReturn(claims);

        authService
                .logout("bearer_token", "other_openid")
                .as(StepVerifier::create)
                .verifyErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                });
    }

    @Test
    @DisplayName("bindPhone - 成功绑定手机号")
    void bindPhone_Success() {
        WxPhoneRequest request = new WxPhoneRequest();
        request.setPhoneCode("phone_code");

        WeChatPhoneResult phoneResult = new WeChatPhoneResult();
        phoneResult.setPhoneNumber("13800138000");
        when(weChatGateway.getPhoneNumber("phone_code")).thenReturn(Mono.just(phoneResult));
        when(userService.getByOpenId("test_openid")).thenReturn(Mono.just(user));
        when(userService.update(any(UserEntity.class))).thenReturn(Mono.just(user));

        authService
                .bindPhone(request, "test_openid")
                .as(StepVerifier::create)
                .assertNext(result -> {
                    assertThat(result).containsKey("phone");
                    assertThat(result.get("phone")).isEqualTo("13800138000");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("bindPhone - 获取手机号失败")
    void bindPhone_PhoneNull() {
        WxPhoneRequest request = new WxPhoneRequest();
        request.setPhoneCode("phone_code");

        WeChatGateway.WeChatPhoneResult emptyResult = new WeChatGateway.WeChatPhoneResult();
        emptyResult.setPhoneNumber("");
        when(weChatGateway.getPhoneNumber("phone_code")).thenReturn(Mono.just(emptyResult));

        authService.bindPhone(request, "test_openid").as(StepVerifier::create).verifyErrorSatisfies(ex -> {
            assertThat(ex).isInstanceOf(BusinessException.class);
            assertThat(ex).hasMessage("获取手机号失败");
        });
    }

    /**
     * 发送{@code smsCode_Success}。
     */
    @Test
    @DisplayName("sendSmsCode - 成功发送短信验证码")
    void sendSmsCode_Success() {
        SendSmsRequest request = new SendSmsRequest();
        request.setPhone("13800138000");

        when(smsVerificationCoordinator.send("13800138000")).thenReturn(Mono.empty());

        authService.sendSmsCode(request).as(StepVerifier::create).verifyComplete();

        verify(smsVerificationCoordinator).send("13800138000");
    }

    /**
     * 发送{@code clSmsCode_Success}。
     */
    @Test
    @DisplayName("sendClSmsCode - 成功发送创蓝短信验证码")
    void sendClSmsCode_Success() {
        SendSmsRequest request = new SendSmsRequest();
        request.setPhone("13800138000");

        when(smsVerificationCoordinator.send("13800138000")).thenReturn(Mono.empty());

        authService.sendClSmsCode(request).as(StepVerifier::create).verifyComplete();

        verify(smsVerificationCoordinator).send("13800138000");
    }

    /**
     * 处理{@code loginBlock_ReturnsError}。
     */
    @Test
    @DisplayName("handleLoginBlock - 限流时返回错误")
    void handleLoginBlock_ReturnsError() {
        authService
                .handleLoginBlock(loginRequest, null)
                .as(StepVerifier::create)
                .verifyErrorSatisfies(ex -> {
                    assertThat(ex).isInstanceOf(BusinessException.class);
                    assertThat(ex).hasMessage("系统繁忙，请稍后再试");
                });
    }
}
