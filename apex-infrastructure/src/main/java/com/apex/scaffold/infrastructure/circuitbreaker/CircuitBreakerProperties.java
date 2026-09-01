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

    // 手动 getter：配置绑定优先使用显式声明的方法
    /**
     * 获取默认模式的熔断器配置。
     *
     * @return 默认模式配置
     */
    public ModeConfig getDefaultConfig() {
        return defaultConfig;
    }

    /**
     * 获取严格模式的熔断器配置。
     *
     * @return 严格模式配置
     */
    public ModeConfig getStrict() {
        return strict;
    }

    /**
     * 获取宽松模式的熔断器配置。
     *
     * @return 宽松模式配置
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

        /**
         * 获取失败率阈值（百分比），超过此值触发熔断。
         *
         * @return 失败率阈值（百分比）
         */
        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        /**
         * 设置失败率阈值（百分比），超过此值触发熔断。
         *
         * @param failureRateThreshold 失败率阈值（百分比）
         */
        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        /**
         * 获取熔断打开后进入半开状态的等待时长。
         *
         * @return 等待时长
         */
        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        /**
         * 设置熔断打开后进入半开状态的等待时长。
         *
         * @param waitDurationInOpenState 等待时长
         */
        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        /**
         * 获取滑动窗口大小。
         *
         * @return 滑动窗口大小
         */
        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        /**
         * 设置滑动窗口大小。
         *
         * @param slidingWindowSize 滑动窗口大小
         */
        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        /**
         * 获取最小调用次数，低于此值不触发熔断。
         *
         * @return 最小调用次数
         */
        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        /**
         * 设置最小调用次数，低于此值不触发熔断。
         *
         * @param minimumNumberOfCalls 最小调用次数
         */
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        /**
         * 获取半开状态允许通过的最大调用次数。
         *
         * @return 半开状态允许通过的最大调用次数
         */
        public int getPermittedCallsInHalfOpen() {
            return permittedCallsInHalfOpen;
        }

        /**
         * 设置半开状态允许通过的最大调用次数。
         *
         * @param permittedCallsInHalfOpen 半开状态允许通过的最大调用次数
         */
        public void setPermittedCallsInHalfOpen(int permittedCallsInHalfOpen) {
            this.permittedCallsInHalfOpen = permittedCallsInHalfOpen;
        }
    }
}
