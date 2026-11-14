package com.apex.scaffold.auth.config;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证域可信代理配置。
 *
 * <p>仅当 TCP 直连来源命中 {@link #trustedProxyCidrs} 时，认证域才会采信
 * {@code X-Forwarded-For}、{@code X-Real-IP} 转发头识别真实客户端地址，
 * 默认不信任任何代理，避免客户端伪造转发头绕过按 IP 限流、登录风控与审计。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apex.security.ip-rate-limit")
@SuppressWarnings({"PMD.ClassNamingShouldBeCamelRule", "PMD.ClassMustHaveAuthorRule"})
public class AuthTrustedProxyProperties {

    /** 可信反向代理地址或 CIDR；仅来自这些代理的转发头才会被采信，默认不信任任何代理 */
    private List<String> trustedProxyCidrs = List.of();
}
