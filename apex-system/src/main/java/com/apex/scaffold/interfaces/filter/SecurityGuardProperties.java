package com.apex.scaffold.interfaces.filter;

import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * 安全防护配置属性类
 *
 * <p>用于配置 {@link GlobalGuardWebFilter} 的相关参数，包括 IP 黑名单缓存、
 * User-Agent 检查等安全防护功能的开关与阈值。
 *
 * <h2>设计说明</h2>
 * <p>在 {@code application.yml} 中使用 {@code apex.security.guard} 前缀进行配置：
 * <pre>{@code * apex:
 *   security:
 *     guard:
 *       ip-blacklist-cache-size: 10000
 *       ip-blacklist-cache-ttl: 300
 *       ua-check-enabled: true
 *       ua-allow-empty-paths:
 *         - /actuator/health
 *         - /health
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see GlobalGuardWebFilter
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "apex.security.guard")
public class SecurityGuardProperties {

    /**
     * IP 黑名单本地缓存的最大容量
     *
     * <p>当缓存条目数超过此值时，旧的条目将被淘汰。
     * 默认值为 10000。
     */
    private int ipBlacklistCacheSize = 10000;

    /**
     * IP 黑名单本地缓存的存活时间（秒）
     *
     * <p>缓存条目在此时间后自动过期失效。
     * 默认值为 300 秒（5 分钟）。
     */
    private int ipBlacklistCacheTtl = 300;

    /** 普通 IP 未命中黑名单时的本地负缓存时间（秒）。 */
    private int ipBlacklistNegativeCacheTtl = 3;

    /**
     * 是否启用 User-Agent 检查
     *
     * <p>当启用时，对于 User-Agent 为空的请求会标记为可疑。
     * 默认值为 true。
     */
    private boolean uaCheckEnabled = true;

    /**
     * User-Agent 允许为空的路径白名单
     *
     * <p>当这些路径的请求 User-Agent 为空时，不会被视为可疑。
     * 默认包含健康检查端点。
     */
    private List<String> uaAllowEmptyPaths = List.of("/actuator/health", "/health");
}
