package com.apex.scaffold.gateway.ratelimit;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关限流配置属性.
 *
 * <p>绑定 {@code apex.gateway.rate-limit.*}，统一驱动网关限流内核
 * （{@link TokenBucketRateLimitFilter}）。既可经 {@code application.yml} 启动兜底，
 * 也可经 Nacos「服务私有配置」热更新，无需重启网关。
 *
 * <p>典型配置示例：
 * <pre>{@code
 * apex:
 *   gateway:
 *     rate-limit:
 *       enabled: true
 *       algorithm: token-bucket        # token-bucket | fixed-window
 *       default-capacity: 1000
 *       default-refill-rate: 100        # 令牌/秒
 *       key-prefix: "apex:gateway:rl:"
 *       rules:
 *         - resource: "/api/v1/workflows/**"
 *           capacity: 2000
 *           refill-rate: 200
 * }</pre>
 *
 * <p><b>Key 约定</b>：{@code {key-prefix}{scope}:{identity}:{path}}，
 * {@code scope ∈ {tenant,user,ip}}（维度优先级 tenant → user → ip）。
 *
 * @author Apex Team
 * @since 1.3.1
 * @see com.apex.scaffold.gateway.filter.RequestRateLimitFilter
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apex.gateway.rate-limit")
public class GatewayRateLimitProperties {

    /** 限流总开关（关闭后退化为纯透传） */
    private boolean enabled = true;

    /** 限流算法：{@code token-bucket}（默认）或 {@code fixed-window} */
    private String algorithm = "token-bucket";

    /** 默认桶容量（未命中 {@link #rules} 时生效） */
    private long defaultCapacity = 1000L;

    /** 默认令牌补充速率（令牌/秒，未命中 {@link #rules} 时生效） */
    private double defaultRefillRate = 100.0d;

    /** Redis Key 统一前缀 */
    private String keyPrefix = "apex:gateway:rl:";

    /** 固定窗口长度（毫秒，仅 {@code fixed-window} 算法使用） */
    private long windowMs = 60_000L;

    /** 按资源（路径通配）覆盖的限流规则，命中后覆盖默认容量与速率 */
    private List<Rule> rules = new ArrayList<>();

    public void setRules(List<Rule> rules) {
        this.rules = rules == null ? new ArrayList<>() : rules;
    }

    /**
     * 单条按资源覆盖的限流规则（{@code null} 字段表示沿用默认值）.
     */
    @Getter
    @Setter
    public static class Rule {

        /** 资源匹配表达式（Ant 风格路径通配，如 {@code /api/v1/workflows/**}） */
        private String resource;

        /** 桶容量（覆盖 {@link #defaultCapacity}） */
        private Long capacity;

        /** 令牌补充速率（令牌/秒，覆盖 {@link #defaultRefillRate}） */
        private Double refillRate;
    }
}
