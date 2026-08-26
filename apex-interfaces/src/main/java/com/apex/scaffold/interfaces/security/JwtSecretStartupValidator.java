package com.apex.scaffold.interfaces.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * JWT 密钥启动校验器。
 *
 * <p>在应用启动完成后校验 JWT 密钥是否为安全的自定义值。
 * 当前允许使用默认密钥的 Profile 仅限于：</p>
 * <ul>
 *   <li>{@code test} — 单元测试</li>
 *   <li>{@code dev} — 本地开发</li>
 *   <li>{@code it} / {@code it-local} — 集成测试</li>
 * </ul>
 *
 * <p>其余环境（生产及自定义 Profile）必须通过 {@code JWT_SECRET} 环境变量或
 * {@code jwt.secret} 系统属性设置自定义密钥，且密钥长度不得低于
 * {@code jwt.min-secret-length}（默认 32 位），否则启动直接失败。</p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
@Profile("!test & !dev & !it & !it-local")
public class JwtSecretStartupValidator {

    /** 默认密钥中的特征字符串，用于判断是否正在使用不安全的内置密钥 */
    private static final String DEFAULT_SECRET_MARKER = "MUST-CHANGE-IN-PROD";

    /** 默认密钥完整值 */
    private static final String DEFAULT_SECRET = "apex-service-default-secret-key-" + DEFAULT_SECRET_MARKER;

    /** 最小密钥长度的默认值（与 application.yml 中 jwt.min-secret-length 默认值一致） */
    private static final int DEFAULT_MIN_SECRET_LENGTH = 32;

    /** Spring 环境，用于读取 Profile 与最小密钥长度配置 */
    private final Environment environment;

    /**
     * 构造方法，通过构造器注入 Spring 环境。
     *
     * @param environment Spring 环境对象
     */
    public JwtSecretStartupValidator(Environment environment) {
        this.environment = environment;
    }

    /**
     * 应用就绪后校验 JWT 密钥安全性。
     *
     * <p>仅在非测试/非本地开发 Profile 下生效。校验规则：</p>
     * <ol>
     *   <li>密钥不得为内置默认密钥（含 {@link #DEFAULT_SECRET_MARKER} 特征串）；</li>
     *   <li>密钥长度不得低于 {@code jwt.min-secret-length} 配置值（默认 32 位），
     *       防止自定义弱密钥通过校验；</li>
     * </ol>
     *
     * <p>任一项不满足则抛出异常阻止应用继续启动，避免运行期才暴露
     * "全线 401/500" 的弱密钥问题。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validate() {
        String jwtSecret = resolveActualSecret();
        int minSecretLength =
                environment.getProperty("jwt.min-secret-length", Integer.class, DEFAULT_MIN_SECRET_LENGTH);
        String activeProfiles = String.join(",", environment.getActiveProfiles());
        String profileLabel = activeProfiles.length() > 0 ? activeProfiles : "default";

        if (jwtSecret == null || jwtSecret.contains(DEFAULT_SECRET_MARKER)) {
            String errorMsg = String.format(
                    "安全风险：当前 Profile [%s] 未设置自定义 JWT 密钥。"
                            + "任何知晓默认密钥的人都可以伪造任意用户的 Token。"
                            + "请设置 JWT_SECRET 环境变量（建议 %d 位以上）。",
                    profileLabel, minSecretLength);
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        if (jwtSecret.length() < minSecretLength) {
            String errorMsg = String.format(
                    "安全风险：当前 Profile [%s] 的 JWT 密钥长度不足，要求至少 %d 位，当前 %d 位。" + "请配置长度足够的 JWT_SECRET 环境变量。",
                    profileLabel, minSecretLength, jwtSecret.length());
            log.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        log.info("JWT 密钥安全性校验通过，当前使用自定义密钥（长度 {} 位）", jwtSecret.length());
    }

    /**
     * 按优先级解析当前实际使用的 JWT 密钥。
     *
     * <p>与 {@link com.apex.scaffold.common.util.JwtUtil} 内部的优先级一致：
     * 环境变量 {@code JWT_SECRET} → 系统属性 {@code jwt.secret} → 默认密钥。
     * 通过 {@link Environment} 解析，依赖 Spring 属性源顺序
     * （系统环境变量优先级高于系统属性），并便于单元测试隔离。</p>
     *
     * @return 当前 JWT 密钥；未配置时返回默认密钥
     */
    private String resolveActualSecret() {
        String envSecret = environment.getProperty("JWT_SECRET");
        if (envSecret != null && !envSecret.isBlank()) {
            return envSecret;
        }
        String sysSecret = environment.getProperty("jwt.secret", "");
        if (!sysSecret.isBlank()) {
            return sysSecret;
        }
        return DEFAULT_SECRET;
    }
}
