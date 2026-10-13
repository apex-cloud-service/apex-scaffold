package com.apex.scaffold.application.security;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图形验证码配置属性。
 *
 * <p>通过 {@code apex.captcha.*} 前缀配置图形验证码的行为，包括
 * 开关、字符长度与有效期。验证码生成与校验由
 * {@link CaptchaService} 使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "apex.captcha")
public class CaptchaProperties {

    /**
     * 是否启用图形验证码。
     *
     * <p>关闭后验证码接口返回 404，登录时不再强制校验。</p>
     */
    private boolean enabled = true;

    /**
     * 验证码字符长度。
     *
     * <p>取值 4-6，缺省 4 位。</p>
     */
    private int length = 4;

    /**
     * 验证码有效期。
     *
     * <p>超过有效期后验证码失效，需重新获取。</p>
     */
    private Duration ttl = Duration.ofMinutes(2);
}
