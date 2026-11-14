package com.apex.scaffold.interfaces.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

/**
 * JWT 密钥启动校验器单元测试。
 *
 * <p>验证非本地开发/测试 Profile 下：默认密钥被拒绝、弱密钥被拒绝、
 * 合格自定义密钥通过。通过 Mock {@link Environment} 隔离密钥来源
 * （环境变量 {@code JWT_SECRET} 与系统属性 {@code jwt.secret}）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.0.0
 */
@DisplayName("JwtSecretStartupValidator 单元测试")
class JwtSecretStartupValidatorTest {

    /** 满足最小长度要求的自定义密钥（32 位） */
    private static final String STRONG_SECRET = "0123456789abcdef0123456789abcdef";

    /**
     * 构建密钥来源可控的校验器实例。
     *
     * @param envSecret  环境变量 JWT_SECRET 的取值（null 表示未配置）
     * @param sysSecret  系统属性 jwt.secret 的取值（null 表示未配置）
     * @return 校验器实例
     */
    private JwtSecretStartupValidator validatorWith(String envSecret, String sysSecret) {
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[] {"staging"});
        when(environment.getProperty("JWT_SECRET")).thenReturn(envSecret);
        when(environment.getProperty(eq("jwt.secret"), anyString())).thenReturn(sysSecret == null ? "" : sysSecret);
        when(environment.getProperty(anyString(), any(Class.class), any())).thenReturn(32);
        return new JwtSecretStartupValidator(environment);
    }

    /**
     * 验证 {@code shouldRejectDefaultSecret} 场景。
     */
    @Test
    @DisplayName("未配置自定义密钥时应拒绝启动")
    void shouldRejectDefaultSecret() {
        assertThatThrownBy(() -> validatorWith(null, null).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("未设置自定义 JWT 密钥");
    }

    /**
     * 验证 {@code shouldRejectWeakCustomSecret} 场景。
     */
    @Test
    @DisplayName("自定义密钥长度不足时应拒绝启动")
    void shouldRejectWeakCustomSecret() {
        assertThatThrownBy(() -> validatorWith(null, "too-short").validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("密钥长度不足");
    }

    /**
     * 验证 {@code shouldAcceptStrongCustomSecret} 场景。
     */
    @Test
    @DisplayName("合格自定义密钥应通过校验")
    void shouldAcceptStrongCustomSecret() {
        assertThatCode(() -> validatorWith(null, STRONG_SECRET).validate()).doesNotThrowAnyException();
    }

    /**
     * 验证 {@code shouldPreferEnvSecretOverSysProp} 场景。
     */
    @Test
    @DisplayName("环境变量密钥优先级高于系统属性")
    void shouldPreferEnvSecretOverSysProp() {
        assertThatCode(() -> validatorWith(STRONG_SECRET, "too-short").validate())
                .doesNotThrowAnyException();
    }
}
