package com.apex.scaffold.interfaces.filter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 安全响应头配置属性类
 *
 * <p>用于配置 {@link SecurityHeaderWebFilter} 的响应头参数，包括 HSTS 的
 * max-age 和 includeSubdomains 选项。
 *
 * <h2>配置前缀</h2>
 * <p>在 {@code application.yml} 中使用 {@code apex.security.security-headers} 前缀进行配置：
 * <pre>{@code * apex:
 *   security:
 *     security-headers:
 *       enabled: true
 *       hsts-max-age: 31536000
 *       hsts-include-subdomains: true
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see SecurityHeaderWebFilter
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "apex.security.security-headers")
public class SecurityHeadersProperties {

    /** HSTS max-age 默认值：31536000 秒（1 年） */
    public static final long DEFAULT_HSTS_MAX_AGE = 31536000L;

    /**
     * 是否启用安全响应头功能
     *
     * <p>当启用时，{@link SecurityHeaderWebFilter} 将向每个响应添加
     * X-Content-Type-Options、X-Frame-Options、Strict-Transport-Security 等安全头。
     * 默认值为 true。
     */
    private boolean enabled = true;

    /**
     * HSTS max-age（秒）
     *
     * <p>指定浏览器记住该域名只能通过 HTTPS 访问的时间。
     * 默认值为 31536000 秒（1 年）。
     */
    private long hstsMaxAge = DEFAULT_HSTS_MAX_AGE;

    /**
     * HSTS 是否包含子域名
     *
     * <p>当为 true 时，HSTS 策略将应用于当前域名及其所有子域名。
     * 默认值为 true。
     */
    private boolean hstsIncludeSubdomains = true;
}
