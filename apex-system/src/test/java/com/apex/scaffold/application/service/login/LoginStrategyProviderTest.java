package com.apex.scaffold.application.service.login;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.enums.LoginType;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link LoginStrategyProvider} 单元测试
 *
 * <p>验证自注册机制的策略提供者，通过手动构造 {@link LoginStrategy} mock 列表
 * 测试 {@code init()} 自动注册和 {@code getStrategy(String)} 策略分发逻辑。
 *
 * @author Apex Team
 */
class LoginStrategyProviderTest {

    private LoginStrategy weChatCodeLoginStrategy;

    private LoginStrategy weChatPhoneLoginStrategy;

    private LoginStrategy smsLoginStrategy;

    private LoginStrategy chuanglanPhoneLoginStrategy;

    private LoginStrategy chuanglanSmsLoginStrategy;

    private LoginStrategyProvider loginStrategyProvider;

    /**
     * 测试前置：创建 mock 策略列表并初始化 Provider
     */
    @BeforeEach
    void setUp() {
        weChatCodeLoginStrategy = mock(LoginStrategy.class);
        when(weChatCodeLoginStrategy.getLoginType()).thenReturn(LoginType.WX_CODE);

        weChatPhoneLoginStrategy = mock(LoginStrategy.class);
        when(weChatPhoneLoginStrategy.getLoginType()).thenReturn(LoginType.WX_PHONE);

        smsLoginStrategy = mock(LoginStrategy.class);
        when(smsLoginStrategy.getLoginType()).thenReturn(LoginType.WX_SMS);

        chuanglanPhoneLoginStrategy = mock(LoginStrategy.class);
        when(chuanglanPhoneLoginStrategy.getLoginType()).thenReturn(LoginType.CL_PHONE);

        chuanglanSmsLoginStrategy = mock(LoginStrategy.class);
        when(chuanglanSmsLoginStrategy.getLoginType()).thenReturn(LoginType.CL_SMS);

        List<LoginStrategy> strategies = List.of(
                weChatCodeLoginStrategy,
                weChatPhoneLoginStrategy,
                smsLoginStrategy,
                chuanglanPhoneLoginStrategy,
                chuanglanSmsLoginStrategy);

        loginStrategyProvider = new LoginStrategyProvider(strategies);
        loginStrategyProvider.init();
    }

    @Test
    @DisplayName("init - 自注册后所有策略都已注册")
    void init_AllStrategiesRegistered() {
        assertThat(loginStrategyProvider.getStrategy("WX_CODE")).isEqualTo(weChatCodeLoginStrategy);
        assertThat(loginStrategyProvider.getStrategy("WX_PHONE")).isEqualTo(weChatPhoneLoginStrategy);
        assertThat(loginStrategyProvider.getStrategy("WX_SMS")).isEqualTo(smsLoginStrategy);
        assertThat(loginStrategyProvider.getStrategy("CL_PHONE")).isEqualTo(chuanglanPhoneLoginStrategy);
        assertThat(loginStrategyProvider.getStrategy("CL_SMS")).isEqualTo(chuanglanSmsLoginStrategy);
    }

    @Test
    @DisplayName("getStrategy - 小写类型名正确匹配")
    void getStrategy_LowerCase_ReturnsCorrectStrategy() {
        assertThat(loginStrategyProvider.getStrategy("wx_code")).isEqualTo(weChatCodeLoginStrategy);
        assertThat(loginStrategyProvider.getStrategy("wx_phone")).isEqualTo(weChatPhoneLoginStrategy);
        assertThat(loginStrategyProvider.getStrategy("wx_sms")).isEqualTo(smsLoginStrategy);
        assertThat(loginStrategyProvider.getStrategy("cl_phone")).isEqualTo(chuanglanPhoneLoginStrategy);
        assertThat(loginStrategyProvider.getStrategy("cl_sms")).isEqualTo(chuanglanSmsLoginStrategy);
    }

    @Test
    @DisplayName("getStrategy - 混合大小写类型名正确匹配")
    void getStrategy_MixedCase_ReturnsCorrectStrategy() {
        assertThat(loginStrategyProvider.getStrategy("Wx_Code")).isEqualTo(weChatCodeLoginStrategy);
        assertThat(loginStrategyProvider.getStrategy("Wx_Phone")).isEqualTo(weChatPhoneLoginStrategy);
    }

    @Test
    @DisplayName("getStrategy - null类型名返回列表中第一个策略")
    void getStrategy_NullType_ReturnsFirstStrategy() {
        assertThat(loginStrategyProvider.getStrategy(null)).isEqualTo(weChatCodeLoginStrategy);
    }

    @Test
    @DisplayName("getStrategy - 空字符串类型名返回列表中第一个策略")
    void getStrategy_BlankType_ReturnsFirstStrategy() {
        assertThat(loginStrategyProvider.getStrategy("")).isEqualTo(weChatCodeLoginStrategy);
        assertThat(loginStrategyProvider.getStrategy("   ")).isEqualTo(weChatCodeLoginStrategy);
    }

    @Test
    @DisplayName("getStrategy - 无效类型名抛出不支持登录类型异常")
    void getStrategy_InvalidType_ThrowsBusinessException() {
        assertThatThrownBy(() -> loginStrategyProvider.getStrategy("INVALID_TYPE"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ResultCode.UNSUPPORTED_LOGIN_TYPE.getCode());
                });
    }

    @Test
    @DisplayName("getStrategy - 未注册类型名抛出不支持登录类型异常")
    void getStrategy_UnregisteredType_ThrowsBusinessException() {
        assertThatThrownBy(() -> loginStrategyProvider.getStrategy("UNKNOWN"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ResultCode.UNSUPPORTED_LOGIN_TYPE.getCode());
                });
    }

    @Test
    @DisplayName("getStrategy - 空策略列表时 null 类型名抛出异常")
    void getStrategy_EmptyStrategies_NullType_ThrowsBusinessException() {
        LoginStrategyProvider emptyProvider = new LoginStrategyProvider(Collections.emptyList());
        emptyProvider.init();

        assertThatThrownBy(() -> emptyProvider.getStrategy(null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo(ResultCode.UNSUPPORTED_LOGIN_TYPE.getCode());
                });
    }
}
