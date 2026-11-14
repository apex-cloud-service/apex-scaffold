package com.apex.scaffold.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 配置属性。
 *
 * <p>将 YAML 中 {@code jwt.*} 配置映射为 Java 对象，供 {@link com.apex.scaffold.common.util.JwtUtil}
 * 在运行时通过 {@code applyTo} 应用。
 *
 * <p>配置路径示例：
 * <pre>{@code * jwt:
 *   # Access Token 有效期，单位：毫秒（默认 2 小时）
 *   access-token-expire: 7200000
 *   # Refresh Token 有效期，单位：毫秒（默认 7 天）
 *   refresh-token-expire: 604800000
 *   # JWT 工具类默认过期时间（毫秒）
 *   default-expire-time: 604800000
 *   # 密钥最小长度
 *   min-secret-length: 32
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.1.0
 */
@ConfigurationProperties(prefix = "jwt")
@Validated
@Data
public class JwtProperties {

    /** Access Token 有效期，单位：毫秒（默认 2 小时） */
    private long accessTokenExpire = 7200000L;

    /** Refresh Token 有效期，单位：毫秒（默认 7 天） */
    private long refreshTokenExpire = 604800000L;

    /** JWT 工具类默认过期时间，单位：毫秒（默认 7 天） */
    private long defaultExpireTime = 604800000L;

    /** JWT 密钥最小长度（生产环境强制检查） */
    private int minSecretLength = 32;

    /**
     * JWT签名密钥。
     *
     * <p>优先级: 环境变量JWT_SECRET &gt; 配置文件jwt.secret &gt; 启动失败</p>
     *
     * <p><b>安全警告</b>: 生产环境必须设置此值,不允许使用默认值!</p>
     */
    private String secret;

    /**
     * 获取有效的JWT密钥。
     *
     * <p>如果配置中未设置密钥,抛出IllegalStateException强制用户配置。</p>
     *
     * @return JWT签名密钥
     * @throws IllegalStateException 当密钥未配置时抛出
     */
    public String getRequiredSecret() {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException("JWT密钥未配置! 请通过以下方式之一配置:\n" + "1. 环境变量: export JWT_SECRET=your-secret-key\n"
                    + "2. 配置文件: jwt.secret=your-secret-key\n"
                    + "生产环境强烈建议使用环境变量方式!");
        }
        return secret;
    }
}
