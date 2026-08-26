package com.apex.scaffold.application.service.login;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.application.assembler.UserAssembler;
import com.apex.scaffold.application.dto.LoginResponse;
import com.apex.scaffold.domain.event.UserRegisteredEvent;
import com.apex.scaffold.domain.model.entity.UserEntity;
import com.apex.scaffold.domain.service.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

/**
 * 登录响应构建器单元测试类
 *
 * <p>测试 {@link LoginResponseHelper} 的核心业务逻辑，包括带手机号和不带手机号
 * 两种登录响应构建场景，以及新用户注册事件的发布逻辑（登录事件当前无消费者，不再发布）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see LoginResponseHelper
 * @see UserAssembler
 * @see DomainEventPublisher
 */
@ExtendWith(MockitoExtension.class)
class LoginResponseHelperTest {

    @Mock
    private UserAssembler userAssembler;

    @Mock
    private DomainEventPublisher domainEventPublisher;

    @InjectMocks
    private LoginResponseHelper loginResponseHelper;

    private UserEntity user;
    private LoginResponse loginResponse;

    /**
     * 初始化测试夹具。
     */
    @BeforeEach
    void setUp() {
        user = new UserEntity();
        user.setId(1L);
        user.setOpenId("test_openid");
        user.setUnionId("test_unionid");
        user.setNickName("TestUser");
        user.setPhone("13800138000");

        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setNickName("TestUser");
        userInfo.setPhone("13800138000");

        loginResponse = new LoginResponse();
        loginResponse.setUserInfo(userInfo);
        loginResponse.setOpenId("test_openid");
        loginResponse.setUnionId("test_unionid");

        // @Value 注入的字段在 Mockito @InjectMocks 下不会被赋值，需显式注入，
        // 否则 accessTokenExpireMs 默认为 0，导致 expiresIn(=accessTokenExpireMs/1000) 断言失败。
        ReflectionTestUtils.setField(loginResponseHelper, "accessTokenExpireMs", 7200000L);
    }

    /**
     * 验证不带手机号构建登录响应成功。
     */
    @Test
    @DisplayName("build - 不带手机号构建登录响应成功")
    void build_WithoutPhone_Success() {
        when(userAssembler.toLoginResponse(user)).thenReturn(loginResponse);

        LoginResponse result =
                loginResponseHelper.build(user, "access_token", "refresh_token", false, null, "127.0.0.1");

        assertThat(result.getAccessToken()).isEqualTo("access_token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh_token");
        assertThat(result.getTokenType()).isEqualTo("Bearer");
        assertThat(result.getExpiresIn()).isEqualTo(7200L);
        assertThat(result.getOpenId()).isEqualTo("test_openid");
        assertThat(result.getUnionId()).isEqualTo("test_unionid");
        assertThat(result.getIsNewUser()).isFalse();
        // 非新用户不发布任何事件。
        verify(domainEventPublisher, never()).publish(any());
    }

    /**
     * 验证带手机号和客户端IP构建登录响应成功。
     */
    @Test
    @DisplayName("build - 带手机号和客户端IP构建登录响应成功，手机号回填到用户信息")
    void build_WithPhoneAndClientIp_Success() {
        when(userAssembler.toLoginResponse(user)).thenReturn(loginResponse);

        LoginResponse result =
                loginResponseHelper.build(user, "access_token", "refresh_token", false, "13900139000", "192.168.1.1");

        assertThat(result.getAccessToken()).isEqualTo("access_token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh_token");
        assertThat(result.getUserInfo().getPhone()).isEqualTo("13900139000");
        assertThat(result.getIsNewUser()).isFalse();
        verify(domainEventPublisher, never()).publish(any());
    }

    /**
     * 验证新注册用户仅发布注册事件。
     */
    @Test
    @DisplayName("build - 带手机号且为NewUser时，仅发布注册事件")
    void build_WithPhoneAndNewUser_PublishesRegisteredEvent() {
        when(userAssembler.toLoginResponse(user)).thenReturn(loginResponse);
        when(domainEventPublisher.publish(any(UserRegisteredEvent.class))).thenReturn(Mono.empty());

        LoginResponse result =
                loginResponseHelper.build(user, "access_token", "refresh_token", true, "13900139000", "192.168.1.1");

        assertThat(result.getIsNewUser()).isTrue();
        verify(domainEventPublisher, times(1)).publish(any(UserRegisteredEvent.class));
    }

    /**
     * 验证手机号为null时不覆盖用户信息中的手机号。
     */
    @Test
    @DisplayName("build - 手机号为null时不覆盖用户信息中的手机号")
    void build_PhoneIsNull_DoesNotOverridePhone() {
        when(userAssembler.toLoginResponse(user)).thenReturn(loginResponse);

        LoginResponse result =
                loginResponseHelper.build(user, "access_token", "refresh_token", false, null, "192.168.1.1");

        assertThat(result.getUserInfo().getPhone()).isEqualTo("13800138000");
        verify(domainEventPublisher, never()).publish(any());
    }

    /**
     * 验证6参数方法构建登录响应成功。
     */
    @Test
    @DisplayName("build - 6参数方法构建登录响应成功")
    void build_SixParams_Success() {
        when(userAssembler.toLoginResponse(user)).thenReturn(loginResponse);

        LoginResponse result =
                loginResponseHelper.build(user, "access_token", "refresh_token", false, "13900139000", "192.168.1.1");

        assertThat(result.getAccessToken()).isEqualTo("access_token");
        assertThat(result.getUserInfo().getPhone()).isEqualTo("13900139000");
    }

    /**
     * 验证注册事件发布失败时降级，仍返回登录响应。
     *
     * <p>注册事件发布失败（如 Outbox 依赖故障）时应降级，不阻断已成功的登录响应，
     * 避免"登录成功却收到错误"或请求无限挂起。</p>
     */
    @Test
    @DisplayName("build - 注册事件发布失败时降级，仍返回登录响应")
    void build_EventPublishFails_StillReturnsResponse() {
        when(userAssembler.toLoginResponse(user)).thenReturn(loginResponse);
        when(domainEventPublisher.publish(any(UserRegisteredEvent.class)))
                .thenReturn(Mono.error(new IllegalStateException("Outbox 写入失败")));

        LoginResponse result =
                loginResponseHelper.build(user, "access_token", "refresh_token", true, "13900139000", "192.168.1.1");

        assertThat(result.getAccessToken()).isEqualTo("access_token");
        assertThat(result.getRefreshToken()).isEqualTo("refresh_token");
        assertThat(result.getIsNewUser()).isTrue();
    }
}
