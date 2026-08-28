package com.apex.scaffold.application.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录保护配置属性
 *
 * <p>配置登录暴力破解防护的参数，包括总开关、IP 维度最大失败次数、用户名维度最大失败次数、
 * 验证码升级阈值和计数窗口大小。
 *
 * <p><b>配置诚实性</b>：{@code enabled} 字段真实控制 {@code AuthService#login} 中的
 * 暴力破解检查与验证码升级逻辑。当生产环境关闭时（{@code enabled: false}），
 * 服务启动后首次登录会输出 WARN 日志提示，避免「配置写了但没生效」的隐性安全风险。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 3.0.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.security.login-protection")
public class LoginProtectionProperties {

    /** 登录保护总开关，默认开启；关闭后跳过暴力破解检查与验证码升级 */
    private Boolean enabled = true;

    /** IP 维度最大登录失败次数，默认 20 次 */
    private Integer ipMaxAttempts = 20;

    /** 用户名维度最大登录失败次数，默认 5 次 */
    private Integer usernameMaxAttempts = 5;

    /** 失败计数窗口大小（秒），默认 900 秒（15 分钟） */
    private Long windowSize = 900L;

    /**
     * 连续失败多少次后强制要求图形验证码（验证码升级阈值）。
     *
     * <p>当客户端 IP 在窗口内的失败次数达到该阈值（且尚未触发锁定）时，
     * 后续登录必须携带 {@code captchaId} + {@code captchaCode}，否则返回
     * {@code CAPTCHA_REQUIRED}。该机制在锁定前引入「人机校验」拦截层，
     * 缓解同一 IP 对多个账号的撞库尝试。0 或负值表示不启用升级。</p>
     */
    private Integer captchaAfterFailures = 3;
}
