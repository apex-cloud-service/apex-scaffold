package com.apex.scaffold.gateway.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
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
@ConfigurationProperties(prefix = "apex.gateway.circuit-breaker")
public class CircuitBreakerProperties {

    /** 熔断总开关（关闭后不注册任何 Customizer，退化为纯透传） */
    private boolean enabled = true;

    /** 按路由实例名的覆盖参数，key 为 CircuitBreaker 过滤器 args.name */
    private Map<String, InstanceOverride> instances = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, InstanceOverride> getInstances() {
        return instances;
    }

    public void setInstances(Map<String, InstanceOverride> instances) {
        this.instances = instances == null ? new LinkedHashMap<>() : instances;
    }

    /**
     * 单个熔断实例的可覆盖参数（null 表示沿用默认配置）.
     */
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

        public Integer getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(Integer slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public Integer getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(Integer minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public Float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(Float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public Float getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        public void setSlowCallRateThreshold(Float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }

        public Duration getSlowCallDurationThreshold() {
            return slowCallDurationThreshold;
        }

        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public Integer getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(Integer permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }
    }
}
