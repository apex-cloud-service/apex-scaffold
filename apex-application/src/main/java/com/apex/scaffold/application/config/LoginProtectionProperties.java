package com.apex.scaffold.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录保护配置属性
 *
 * <p>配置登录暴力破解防护的参数，包括 IP 维度最大失败次数、用户名维度最大失败次数
 * 和计数窗口大小。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.security.login-protection")
public class LoginProtectionProperties {

    /** IP 维度最大登录失败次数，默认 20 次 */
    private Integer ipMaxAttempts = 20;

    /** 用户名维度最大登录失败次数，默认 5 次 */
    private Integer usernameMaxAttempts = 5;

    /** 失败计数窗口大小（秒），默认 900 秒（15 分钟） */
    private Long windowSize = 900L;
}
