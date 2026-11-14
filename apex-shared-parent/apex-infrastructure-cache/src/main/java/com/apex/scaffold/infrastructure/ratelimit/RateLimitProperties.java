package com.apex.scaffold.infrastructure.ratelimit;

import com.apex.scaffold.common.util.ratelimit.algorithm.RateLimitAlgorithm;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 限流算法配置属性
 *
 * <p>统一管理令牌桶、漏桶、滑动窗口三种限流算法的全局配置参数，
 * 支持通过 {@code application.yml} 的 {@code apex.ratelimit} 前缀进行外部化配置。
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * apex:
 *   ratelimit:
 *     local-fallback:
 *       enabled: true
 *       algorithm: SLIDING_WINDOW
 *     token-bucket:
 *       default-capacity: 100
 *       default-refill-rate: 10
 *     leaky-bucket:
 *       default-capacity: 50
 *       default-leak-rate: 10
 *     sliding-window:
 *       default-window-ms: 1000
 *       default-bucket-count: 10
 *     adaptive:
 *       cpu-threshold: 0.7
 *       memory-threshold: 0.85
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.ratelimit")
public class RateLimitProperties {

    /** 本地降级限流配置 */
    private LocalFallbackConfig localFallback = new LocalFallbackConfig();

    /** 令牌桶算法配置 */
    private TokenBucketConfig tokenBucket = new TokenBucketConfig();

    /** 漏桶算法配置 */
    private LeakyBucketConfig leakyBucket = new LeakyBucketConfig();

    /** 滑动窗口算法配置 */
    private SlidingWindowConfig slidingWindow = new SlidingWindowConfig();

    /** 自适应限流配置 */
    private AdaptiveConfig adaptive = new AdaptiveConfig();

    /**
     * 本地降级限流配置
     */
    @Data
    public static class LocalFallbackConfig {
        /** 是否启用本地限流降级 */
        private boolean enabled = true;

        /** 本地降流算法类型 */
        private RateLimitAlgorithm algorithm = RateLimitAlgorithm.SLIDING_WINDOW;
    }

    /**
     * 令牌桶算法配置
     */
    @Data
    public static class TokenBucketConfig {
        /** 默认桶容量（最大令牌数） */
        private long defaultCapacity = 100;

        /** 默认令牌补充速率（令牌/秒） */
        private double defaultRefillRate = 10.0;
    }

    /**
     * 漏桶算法配置
     */
    @Data
    public static class LeakyBucketConfig {
        /** 默认桶容量（最大缓冲请求数） */
        private long defaultCapacity = 50;

        /** 默认漏水速率（请求/秒） */
        private double defaultLeakRate = 10.0;
    }

    /**
     * 滑动窗口算法配置
     */
    @Data
    public static class SlidingWindowConfig {
        /** 默认窗口大小（毫秒） */
        private long defaultWindowMs = 1000L;

        /** 默认子桶数量 */
        private int defaultBucketCount = 10;
    }

    /**
     * 自适应限流配置
     */
    @Data
    public static class AdaptiveConfig {
        /** CPU 使用率阈值（0-1） */
        private double cpuThreshold = 0.7;

        /** 内存使用率阈值（0-1） */
        private double memoryThreshold = 0.85;

        /** 线程使用率阈值（0-1） */
        private double threadThreshold = 0.8;

        /** 自适应降级系数 */
        private double degradeFactor = 0.2;
    }
}
