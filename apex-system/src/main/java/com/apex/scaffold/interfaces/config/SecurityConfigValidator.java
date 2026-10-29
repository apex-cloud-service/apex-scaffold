package com.apex.scaffold.interfaces.config;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * 安全配置校验器
 *
 * <p>在生产环境（{@code prod} Profile）下启动时，校验关键安全配置是否已正确设置。
 * 若检测到配置缺失或不符合安全要求，将抛出 {@link IllegalStateException} 阻止应用启动。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>JWT 密钥：必须设置且长度不小于 32 字节</li>
 *   <li>Redis 密码：生产环境必须设置</li>
 *   <li>微信 AppSecret：若启用微信登录则必须设置</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * # application-prod.yml
 * jwt:
 *   secret: your-secret-key-here
 *   min-secret-length: 32
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Data
@Configuration
@Profile("prod")
public class SecurityConfigValidator {

    /**
     * 安全配置属性类
     *
     * <p>绑定 {@code jwt}、{@code spring.data.redis}、{@code wechat} 等配置项。
     */
    @Data
    @Configuration
    @ConfigurationProperties(prefix = "")
    public static class SecurityProperties {
        /**
         * JWT 配置
         */
        private JwtConfig jwt = new JwtConfig();
        /**
         * Redis 配置
         */
        private RedisConfig redis = new RedisConfig();
        /**
         * 微信配置
         */
        private WechatConfig wechat = new WechatConfig();

        /**
         * JWT 配置类
         */
        @Data
        public static class JwtConfig {
            /**
             * JWT 密钥
             */
            private String secret;
            /**
             * 密钥最小长度要求（字节）
             */
            private int minKeyLength = 32;
        }

        /**
         * Redis 配置类
         */
        @Data
        public static class RedisConfig {
            /**
             * Redis 密码
             */
            private String password;
        }

        /**
         * 微信配置类
         */
        @Data
        public static class WechatConfig {
            /**
             * 微信小程序配置
             */
            private WechatAppConfig app = new WechatAppConfig();

            /**
             * 微信小程序配置类
             */
            @Data
            public static class WechatAppConfig {
                /**
                 * 微信小程序 AppID
                 */
                private String appId;
                /**
                 * 微信小程序 AppSecret
                 */
                private String appSecret;
            }
        }
    }

    /**
     * 安全配置属性实例
     */
    private final SecurityProperties securityProperties;

    /**
     * 构造安全配置校验器
     *
     * @param securityProperties 安全配置属性
     */
    public SecurityConfigValidator(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    /**
     * 生产环境安全配置校验
     *
     * <p>在 Spring 容器初始化完成后执行，按以下顺序校验：
     * <ol>
     *   <li>校验 JWT 密钥是否设置且长度符合要求</li>
     *   <li>校验 Redis 密码是否设置</li>
     *   <li>若配置了微信 AppID，则校验 AppSecret 是否设置</li>
     * </ol>
     *
     * @throws IllegalStateException 任一校验失败则抛出此异常，阻止应用启动
     */
    @PostConstruct
    public void validate() {
        log.info("开始执行生产环境安全配置校验...");

        validateJwtSecret();
        validateRedisPassword();
        validateWechatSecret();

        log.info("生产环境安全配置校验通过");
    }

    /**
     * 校验 JWT 密钥配置
     *
     * <p>检查 JWT 密钥是否为空或长度不足。若密钥长度小于配置的 {@code minSecretLength}，
     * 将抛出异常阻止应用启动。
     *
     * @throws IllegalStateException JWT 密钥为空或长度不足时抛出
     */
    private void validateJwtSecret() {
        String secret = securityProperties.getJwt().getSecret();
        int minKeyLength = securityProperties.getJwt().getMinKeyLength();

        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("生产环境配置错误：JWT_SECRET 未设置。请使用强随机密钥（建议至少 32 字节）");
        }

        if (secret.length() < minKeyLength) {
            throw new IllegalStateException(String.format(
                    "生产环境配置错误：JWT_SECRET 长度不足（当前：%d 字节，最小要求：%d 字节）。" + "请使用更长的随机密钥以增强安全性",
                    secret.length(), minKeyLength));
        }

        log.info("JWT 密钥校验通过，长度：{} 字节", secret.length());
    }

    /**
     * 校验 Redis 密码配置
     *
     * <p>生产环境下 Redis 密码为必填项。若密码为空，将抛出异常阻止应用启动。
     *
     * @throws IllegalStateException Redis 密码为空时抛出
     */
    private void validateRedisPassword() {
        String password = securityProperties.getRedis().getPassword();

        if (password == null || password.isBlank()) {
            throw new IllegalStateException("生产环境配置错误：REDIS_PASSWORD 未设置。Redis 服务必须配置访问密码");
        }

        log.info("Redis 密码校验通过");
    }

    /**
     * 校验微信 AppSecret 配置
     *
     * <p>若配置了微信 AppID（表示启用微信登录功能），则必须同时配置 AppSecret。
     * 此校验为强制校验，未配置 AppSecret 时将抛出异常阻止应用启动。
     *
     * @throws IllegalStateException 配置了微信 AppID 但未设置 AppSecret 时抛出
     */
    private void validateWechatSecret() {
        String appId = securityProperties.getWechat().getApp().getAppId();
        String appSecret = securityProperties.getWechat().getApp().getAppSecret();

        if (appId != null && !appId.isBlank()) {
            if (appSecret == null || appSecret.isBlank()) {
                throw new IllegalStateException("安全配置错误：已配置微信 AppID 但未设置 AppSecret，微信登录功能将不可用");
            } else {
                log.info("微信 AppSecret 校验通过");
            }
        } else {
            log.info("未配置微信 AppID，跳过微信相关校验");
        }
    }
}
