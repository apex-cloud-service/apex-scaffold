package com.apex.scaffold.interfaces.filter;

import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * IP 速率限制配置属性类
 *
 * <p>用于配置 {@link IpRateLimitWebFilter} 的速率限制参数，包括默认限流阈值、
 * 滑动窗口大小、信誉系统阈值、黑名单白名单等。
 *
 * <h2>设计说明</h2>
 * <p>在 {@code application.yml} 中使用 {@code apex.security.ip-rate-limit} 前缀进行配置：
 * <pre>{@code * apex:
 *   security:
 *     ip-rate-limit:
 *       enabled: true
 *       default-limit: 120
 *       default-window: 60
 *       violation-window: 60
 *       block-threshold: 30
 *       block-duration: 300
 *       ip-whitelist:
 *         - 127.0.0.1
 *         - 10.0.0.0/8
 *       path-limits:
 *         /api/auth/sms/code: 3
 *         /api/auth/login: 10
 *         /api/file/upload: 20
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see IpRateLimitWebFilter
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "apex.security.ip-rate-limit")
public class IpRateLimitProperties {

    /** 是否启用 IP 速率限制功能，默认启用 */
    private boolean enabled = true;

    /** 默认窗口内最大请求数，默认 60 秒内 120 次 */
    @Min(1)
    private int defaultLimit = 120;

    /** 默认滑动窗口大小（秒），默认 60 秒 */
    @Min(1)
    private int defaultWindow = 60;

    /** 速率超限违规统计窗口（秒），默认 60 秒 */
    @Min(1)
    private int violationWindow = 60;

    /** 违规窗口内触发临时封禁所需的速率超限次数，默认 30 次 */
    @Min(1)
    private int blockThreshold = 30;

    /** 临时封禁时长（秒），默认 300 秒（5 分钟） */
    @Min(1)
    private int blockDuration = 300;

    /** IP 白名单列表，支持 CIDR 格式，默认空 */
    private List<String> ipWhitelist = List.of();

    /** 可信反向代理地址或 CIDR；仅来自这些代理的转发头才会被采信，默认不信任任何代理 */
    private List<String> trustedProxyCidrs = List.of();

    /** 路径级限流覆盖配置，Key 为路径或路径模板，Value 为窗口内请求上限 */
    private Map<String, Integer> pathLimits = Map.of(
            "/api/v1/auth/sms/code", 3,
            "/api/v1/auth/cl/sms/code", 3,
            "/api/v1/auth/login", 10,
            "/api/v1/file/upload", 20);

    /** 路径排除列表：以这些前缀开头的路径完全不参与 IP 限流和短时封禁 */
    private List<String> pathExcludes = List.of();
}
