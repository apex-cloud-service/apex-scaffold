package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.interfaces.config.SecurityConfigValidator.SecurityProperties;
import com.apex.scaffold.interfaces.config.SecurityConfigValidator.SecurityProperties.JwtConfig;
import com.apex.scaffold.interfaces.config.SecurityConfigValidator.SecurityProperties.RedisConfig;
import com.apex.scaffold.interfaces.config.SecurityConfigValidator.SecurityProperties.WechatConfig;
import com.apex.scaffold.interfaces.config.SecurityConfigValidator.SecurityProperties.WechatConfig.WechatAppConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SecurityConfigValidator 安全配置校验器测试
 *
 * <p>验证生产环境安全配置校验逻辑，包括 JWT 密钥长度校验、
 * Redis 密码校验、微信 AppSecret 校验等场景
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SecurityConfigValidator 安全配置校验器测试")
class SecurityConfigValidatorTest {

    private SecurityConfigValidator validator;
    private SecurityProperties properties;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        properties = new SecurityProperties();
    }

    @Test
    @DisplayName("JWT 密码为空时应抛出 IllegalStateException")
    void shouldRejectEmptyJwtSecret() {
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setSecret(null);
        properties.setJwt(jwtConfig);
        properties.setRedis(createRedisConfigWithPassword("test-redis-pwd"));
        validator = new SecurityConfigValidator(properties);

        assertThatThrownBy(() -> validator.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET 未设置");
    }

    @Test
    @DisplayName("JWT 密码长度不足时应抛出 IllegalStateException")
    void shouldRejectShortJwtSecret() {
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setSecret("short");
        properties.setJwt(jwtConfig);
        properties.setRedis(createRedisConfigWithPassword("test-redis-pwd"));
        validator = new SecurityConfigValidator(properties);

        assertThatThrownBy(() -> validator.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET 长度不足");
    }

    @Test
    @DisplayName("Redis 密码为空时应抛出 IllegalStateException")
    void shouldRejectEmptyRedisPassword() {
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setSecret("this-is-a-very-long-secret-key-for-testing-purposes");
        properties.setJwt(jwtConfig);
        properties.setRedis(createRedisConfigWithPassword(null));
        validator = new SecurityConfigValidator(properties);

        assertThatThrownBy(() -> validator.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REDIS_PASSWORD 未设置");
    }

    @Test
    @DisplayName("所有配置正确时应通过验证")
    void shouldPassWithValidConfiguration() {
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setSecret("this-is-a-very-long-secret-key-for-testing-purposes");
        properties.setJwt(jwtConfig);
        properties.setRedis(createRedisConfigWithPassword("test-redis-pwd"));
        validator = new SecurityConfigValidator(properties);

        validator.validate();
        // 无异常抛出即为通过
    }

    @Test
    @DisplayName("配置了微信 AppID 但未设置 AppSecret 时应抛出 IllegalStateException")
    void shouldLogWarningWhenWechatSecretMissing() {
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setSecret("this-is-a-very-long-secret-key-for-testing-purposes");
        properties.setJwt(jwtConfig);
        properties.setRedis(createRedisConfigWithPassword("test-redis-pwd"));

        WechatConfig wechatConfig = new WechatConfig();
        WechatAppConfig appConfig = new WechatAppConfig();
        appConfig.setAppId("test-app-id");
        appConfig.setAppSecret(null);
        wechatConfig.setApp(appConfig);
        properties.setWechat(wechatConfig);

        validator = new SecurityConfigValidator(properties);

        assertThatThrownBy(() -> validator.validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("AppSecret");
    }

    @Test
    @DisplayName("未配置微信 AppId 时应跳过微信校验")
    void shouldSkipWechatValidationWhenNoAppId() {
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setSecret("this-is-a-very-long-secret-key-for-testing-purposes");
        properties.setJwt(jwtConfig);
        properties.setRedis(createRedisConfigWithPassword("test-redis-pwd"));

        WechatConfig wechatConfig = new WechatConfig();
        WechatAppConfig appConfig = new WechatAppConfig();
        appConfig.setAppId(null);
        wechatConfig.setApp(appConfig);
        properties.setWechat(wechatConfig);

        validator = new SecurityConfigValidator(properties);

        validator.validate();
        // 无异常抛出即为通过
    }

    private RedisConfig createRedisConfigWithPassword(String password) {
        RedisConfig redisConfig = new RedisConfig();
        redisConfig.setPassword(password);
        return redisConfig;
    }
}
