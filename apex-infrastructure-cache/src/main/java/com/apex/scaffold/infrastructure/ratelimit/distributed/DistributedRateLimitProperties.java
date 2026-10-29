package com.apex.scaffold.infrastructure.ratelimit.distributed;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 分布式限流配置属性
 *
 * <p>管理分布式令牌桶限流的全局配置和各资源的限流规则。
 *
 * <h2>配置示例</h2>
 * <pre>{@code
 * apex:
 *   ratelimit:
 *     distributed:
 *       enabled: true
 *       redis:
 *         key-prefix: "apex:ratelimit:"
 *       fallback-to-local: true
 *       rules:
 *         - resource: "/api/v1/users"
 *           capacity: 1000
 *           refill-rate: 100
 *           description: "用户接口全局限流"
 *         - resource: "/api/v1/orders"
 *           capacity: 500
 *           refill-rate: 50
 *           description: "订单接口全局限流"
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.ratelimit.distributed")
public class DistributedRateLimitProperties {

    /** 是否启用分布式限流 */
    private boolean enabled = false;

    /** Redis 配置 */
    private RedisConfig redis = new RedisConfig();

    /** 是否在 Redis 不可用时降级到本地限流 */
    private boolean fallbackToLocal = true;

    /** 限流规则列表 */
    private List<RateLimitRule> rules = new ArrayList<>();

    /**
     * Redis 配置
     */
    @Data
    public static class RedisConfig {
        /** Redis key 前缀 */
        private String keyPrefix = "apex:ratelimit:";

        /** Key 过期时间（秒），防止僵尸 key */
        private long keyExpirationSeconds = 60L;
    }

    /**
     * 限流规则
     */
    @Data
    public static class RateLimitRule {
        /** 资源标识（如 API 路径） */
        private String resource;

        /** 桶容量（最大令牌数） */
        private long capacity;

        /** 补充速率（令牌/秒） */
        private double refillRate;

        /** 规则描述 */
        private String description;

        /** 是否启用此规则 */
        private boolean enabled = true;
    }

    /**
     * 根据资源标识查找限流规则
     *
     * @param resource 资源标识
     * @return 匹配的规则，如果未找到则返回 null
     */
    public RateLimitRule findRule(String resource) {
        return rules.stream()
                .filter(rule -> rule.isEnabled() && rule.getResource().equals(resource))
                .findFirst()
                .orElse(null);
    }

    /**
     * 检查是否有任何启用的规则
     *
     * @return 是否有启用的规则
     */
    public boolean hasEnabledRules() {
        return rules.stream().anyMatch(RateLimitRule::isEnabled);
    }
}
