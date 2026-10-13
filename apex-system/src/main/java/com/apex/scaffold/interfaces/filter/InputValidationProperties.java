package com.apex.scaffold.interfaces.filter;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 输入验证配置属性类
 *
 * <p>用于配置 {@link InputValidationFilter} 的各项验证开关与参数，包括
 * XSS 防护、路径遍历检测、SQL 注入预检、参数长度限制等。
 *
 * <h2>配置前缀</h2>
 * <p>在 {@code application.yml} 中使用 {@code apex.security.input-validation} 前缀进行配置：
 * <pre>{@code * apex:
 *   security:
 *     input-validation:
 *       enabled: true
 *       xss-enabled: true
 *       path-traversal-enabled: true
 *       sql-injection-precheck: false
 *       max-param-length: 2000
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see InputValidationFilter
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "apex.security.input-validation")
public class InputValidationProperties {

    /** 是否启用输入验证功能，默认启用 */
    private boolean enabled = true;

    /** 是否启用 XSS 攻击检测，默认启用 */
    private boolean xssEnabled = true;

    /** 是否启用路径遍历检测，默认启用 */
    private boolean pathTraversalEnabled = true;

    /**
     * 是否启用 SQL 注入预检，默认启用
     *
     * <p>启用后将检测常见 SQL 注入特征，如 SELECT/INSERT/UPDATE/DELETE/DROP/UNION 等关键字。
     * 注意：此检测为预检性质，可能存在误报，建议配合参数化查询使用。
     */
    private boolean sqlInjectionPrecheck = true;

    /** 请求参数最大允许长度，默认 2000 字符 */
    private int maxParamLength = 2000;
}
