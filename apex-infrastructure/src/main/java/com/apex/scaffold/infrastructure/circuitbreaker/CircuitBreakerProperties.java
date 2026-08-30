package com.apex.scaffold.infrastructure.circuitbreaker;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Resilience4j 熔断器默认配置属性。
 *
 * <p>配置前缀为 {@code apex.circuit-breaker}，提供 {@link CircuitBreakerService} 中
 * 默认/严格/宽松三种模式的参数外部化能力。未显式配置时回退到代码中的合理默认值。
 *
 * <h2>使用示例</h2>
 * <pre>{@code * apex:
 *   circuit-breaker:
 *     default-config:
 *       failure-rate-threshold: 50
 *       wait-duration-in-open-state: 60s
 *       sliding-window-size: 100
 *       minimum-number-of-calls: 10
 *       permitted-calls-in-half-open: 5
 *     strict:
 *       failure-rate-threshold: 30
 *       wait-duration-in-open-state: 30s
 *       sliding-window-size: 50
 *       minimum-number-of-calls: 5
 *     lenient:
 *       failure-rate-threshold: 80
 *       wait-duration-in-open-state: 120s
 *       sliding-window-size: 200
 *       minimum-number-of-calls: 20
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "apex.circuit-breaker")
public class CircuitBreakerProperties {

    /** 默认模式配置 */
    private ModeConfig defaultConfig = new ModeConfig();

    /** 严格模式配置 */
    private ModeConfig strict = new ModeConfig();

    /** 宽松模式配置 */
    private ModeConfig lenient = new ModeConfig();

    {
        // 默认模式：均衡的保护阈值
        defaultConfig.setFailureRateThreshold(50f);
        defaultConfig.setWaitDurationInOpenState(Duration.ofSeconds(60));
        defaultConfig.setSlidingWindowSize(100);
        defaultConfig.setMinimumNumberOfCalls(10);
        defaultConfig.setPermittedCallsInHalfOpen(5);

        // 严格模式：低阈值快熔断，适用于关键服务
        strict.setFailureRateThreshold(30f);
        strict.setWaitDurationInOpenState(Duration.ofSeconds(30));
        strict.setSlidingWindowSize(50);
        strict.setMinimumNumberOfCalls(5);
        strict.setPermittedCallsInHalfOpen(5);

        // 宽松模式：高阈值长等待，适用于非关键服务
        lenient.setFailureRateThreshold(80f);
        lenient.setWaitDurationInOpenState(Duration.ofSeconds(120));
        lenient.setSlidingWindowSize(200);
        lenient.setMinimumNumberOfCalls(20);
        lenient.setPermittedCallsInHalfOpen(15);
    }

    // 手动 getter（Lombok @Data 在某些场景不生成方法）
    /**
     * 获取DefaultConfig
     *
     * @return 结果
     */
    public ModeConfig getDefaultConfig() {
        return defaultConfig;
    }

    /**
     * 获取Strict
     *
     * @return 结果
     */
    public ModeConfig getStrict() {
        return strict;
    }

    /**
     * 获取Lenient
     *
     * @return 结果
     */
    public ModeConfig getLenient() {
        return lenient;
    }

    /**
     * 单种模式的熔断器配置。
     */
    public static class ModeConfig {
        /** 失败率阈值（百分比），超过此值触发熔断 */
        private float failureRateThreshold;

        /** 熔断打开后等待多久进入半开状态 */
        private Duration waitDurationInOpenState;

        /** 滑动窗口大小 */
        private int slidingWindowSize;

        /** 最小调用次数，低于此值不触发熔断 */
        private int minimumNumberOfCalls;

        /** 半开状态允许通过的最大调用次数 */
        private int permittedCallsInHalfOpen;

        // 手动 getter/setter（Lombok @Data 在某些场景不生成静态内部类方法）
        /**
         * 获取FailureRateThreshold
         *
         * @return 结果
         */
        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        /**
         * 设置FailureRateThreshold
         *
         * @param failureRateThreshold 参数
         */
        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        /**
         * 获取WaitDurationInOpenState
         *
         * @return 结果
         */
        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        /**
         * 设置WaitDurationInOpenState
         *
         * @param waitDurationInOpenState 参数
         */
        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        /**
         * 获取SlidingWindowSize
         *
         * @return 结果
         */
        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        /**
         * 设置SlidingWindowSize
         *
         * @param slidingWindowSize 参数
         */
        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        /**
         * 获取MinimumNumberOfCalls
         *
         * @return 结果
         */
        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        /**
         * 设置MinimumNumberOfCalls
         *
         * @param minimumNumberOfCalls 参数
         */
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        /**
         * 获取PermittedCallsInHalfOpen
         *
         * @return 结果
         */
        public int getPermittedCallsInHalfOpen() {
            return permittedCallsInHalfOpen;
        }

        /**
         * 设置PermittedCallsInHalfOpen
         *
         * @param permittedCallsInHalfOpen 参数
         */
        public void setPermittedCallsInHalfOpen(int permittedCallsInHalfOpen) {
            this.permittedCallsInHalfOpen = permittedCallsInHalfOpen;
        }
    }
}
