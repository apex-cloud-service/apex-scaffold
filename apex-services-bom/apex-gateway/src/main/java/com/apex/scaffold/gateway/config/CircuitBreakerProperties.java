package com.apex.scaffold.gateway.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 网关熔断配置属性.
 *
 * <p>绑定 {@code apex.gateway.circuit-breaker.*}。基础阈值由 {@code ResilienceConfig}
 * 以硬编码默认值兜底（与 {@code application.yml} 中 resilience4j.configs.default 对齐），
 * 本属性用于按路由实例做「覆盖」，并可通过 Nacos 热更新下发。
 *
 * <p>典型配置示例：
 * <pre>{@code
 * apex:
 *   gateway:
 *     circuit-breaker:
 *       enabled: true
 *       instances:
 *         authService:
 *           failure-rate-threshold: 40
 *           wait-duration-in-open-state: 60s
 * }</pre>
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "apex.gateway.circuit-breaker")
public class CircuitBreakerProperties {

    /** 熔断总开关（关闭后不注册任何 Customizer，退化为纯透传） */
    private boolean enabled = true;

    /** 按路由实例名的覆盖参数，key 为 CircuitBreaker 过滤器 args.name */
    private Map<String, InstanceOverride> instances = new LinkedHashMap<>();

    /** 覆盖配置为 null 时归一为空表，避免绑定缺失导致后续遍历空指针。 */
    public void setInstances(Map<String, InstanceOverride> instances) {
        this.instances = instances == null ? new LinkedHashMap<>() : instances;
    }

    /**
     * 单个熔断实例的可覆盖参数（null 表示沿用默认配置）.
     */
    @Getter
    @Setter
    public static class InstanceOverride {

        /** 滑动窗口大小（COUNT_BASED 下为调用次数） */
        private Integer slidingWindowSize;

        /** 最小采样调用数（低于该值不评估熔断） */
        private Integer minimumNumberOfCalls;

        /** 失败率阈值（%） */
        private Float failureRateThreshold;

        /** 慢调用比例阈值（%） */
        private Float slowCallRateThreshold;

        /** 慢调用判定时长 */
        private Duration slowCallDurationThreshold;

        /** OPEN 状态保持时长 */
        private Duration waitDurationInOpenState;

        /** HALF_OPEN 允许的探测调用数 */
        private Integer permittedNumberOfCallsInHalfOpenState;
    }
}
