package com.apex.scaffold.common.util.ratelimit.algorithm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流算法策略工厂
 *
 * <p>根据 {@link RateLimitAlgorithm} 枚举创建对应的算法策略实例，
 * 封装底层 {@link TokenBucket}、{@link LeakyBucket}、{@link SlidingWindow}
 * 的构造差异，对上层提供统一的创建接口。
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li><strong>工厂模式</strong>：封装算法实例的创建逻辑</li>
 *   <li><strong>策略模式</strong>：返回 {@link RateLimitStrategy} 接口，上层透明使用</li>
 *   <li><strong>享元模式</strong>：缓存已创建的限流器实例，相同参数复用</li>
 * </ul>
 *
 * <h2>参数映射</h2>
 * <table border="1">
 *   <tr><th>算法</th><th>capacity</th><th>rate</th><th>windowMs</th></tr>
 *   <tr><td>令牌桶</td><td>桶容量</td><td>补充速率（令牌/秒）</td><td>不使用</td></tr>
 *   <tr><td>漏桶</td><td>桶容量</td><td>漏水速率（请求/秒）</td><td>不使用</td></tr>
 *   <tr><td>滑动窗口</td><td>不使用</td><td>不使用</td><td>窗口大小</td></tr>
 * </table>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建令牌桶：容量100，速率10/s
 * RateLimitStrategy limiter = RateLimitAlgorithmFactory.create(
 *     RateLimitAlgorithm.TOKEN_BUCKET, "api:users", 100, 10, 0);
 *
 * // 创建漏桶：容量50，速率10/s
 * RateLimitStrategy limiter2 = RateLimitAlgorithmFactory.create(
 *     RateLimitAlgorithm.LEAKY_BUCKET, "mq:consume", 50, 10, 0);
 *
 * // 创建滑动窗口：1秒窗口，100许可
 * RateLimitStrategy limiter3 = RateLimitAlgorithmFactory.create(
 *     RateLimitAlgorithm.SLIDING_WINDOW, "ip:1.2.3.4", 100, 0, 1000);
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
@lombok.NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class RateLimitAlgorithmFactory {

    /** 实例缓存：key → 策略实例 */
    private static final Map<String, RateLimitStrategy> CACHE = new ConcurrentHashMap<>();

    /**
     * 创建或获取限流策略实例。
     *
     * <p>以 {@code key} 为唯一标识，相同 key 复用已创建的实例。
     *
     * @param algorithm 算法类型
     * @param key       限流键（用于实例缓存）
     * @param capacity  容量（令牌桶/漏桶使用）
     * @param rate      速率（令牌/秒或请求/秒，令牌桶/漏桶使用）
     * @param windowMs  窗口大小（毫秒，滑动窗口使用）
     * @return 限流策略实例
     */
    public static RateLimitStrategy create(
            RateLimitAlgorithm algorithm, String key, long capacity, double rate, long windowMs) {
        return CACHE.computeIfAbsent(key, k -> {
            log.info(
                    "创建限流策略, algorithm: {}, key: {}, capacity: {}, rate: {}, windowMs: {}",
                    algorithm,
                    k,
                    capacity,
                    rate,
                    windowMs);
            return createInternal(algorithm, k, capacity, rate, windowMs);
        });
    }

    /**
     * 创建限流策略实例（不使用缓存，每次创建新实例）。
     *
     * @param algorithm 算法类型
     * @param capacity  容量
     * @param rate      速率
     * @param windowMs  窗口大小（毫秒）
     * @return 限流策略实例
     */
    public static RateLimitStrategy createNew(RateLimitAlgorithm algorithm, long capacity, double rate, long windowMs) {
        return createInternal(algorithm, null, capacity, rate, windowMs);
    }

    /**
     * 内部创建逻辑。
     */
    private static RateLimitStrategy createInternal(
            RateLimitAlgorithm algorithm, String key, long capacity, double rate, long windowMs) {
        return switch (algorithm) {
            case TOKEN_BUCKET -> new TokenBucketAdapter(capacity, rate);
            case LEAKY_BUCKET -> new LeakyBucketAdapter(capacity, rate);
            case SLIDING_WINDOW -> new SlidingWindowAdapter(windowMs, (int) capacity);
        };
    }

    /**
     * 清理空闲限流器实例。
     *
     * <p>清空缓存中所有实例，下次访问时重新创建。
     */
    public static void clearAll() {
        int size = CACHE.size();
        CACHE.clear();
        log.info("清理所有限流策略实例, 清理数量: {}", size);
    }

    /**
     * 获取当前缓存的实例数量。
     *
     * @return 实例数量
     */
    public static int getCacheSize() {
        return CACHE.size();
    }

    // ==================== 适配器实现 ====================

    /**
     * 令牌桶策略适配器
     */
    private static final class TokenBucketAdapter implements RateLimitStrategy {
        private final TokenBucket bucket;

        TokenBucketAdapter(long capacity, double rate) {
            this.bucket = new TokenBucket(capacity, rate);
        }

        @Override
        public boolean tryAcquire() {
            return bucket.tryAcquire();
        }

        @Override
        public boolean tryAcquire(int permits) {
            return bucket.tryAcquire(permits);
        }

        @Override
        public long getCurrentCount() {
            return bucket.getCapacity() - bucket.getAvailableTokens();
        }

        @Override
        public long getMaxPermits() {
            return bucket.getCapacity();
        }

        @Override
        public RateLimitAlgorithm getAlgorithmType() {
            return RateLimitAlgorithm.TOKEN_BUCKET;
        }
    }

    /**
     * 漏桶策略适配器
     */
    private static final class LeakyBucketAdapter implements RateLimitStrategy {
        private final LeakyBucket bucket;

        LeakyBucketAdapter(long capacity, double rate) {
            this.bucket = new LeakyBucket(capacity, rate);
        }

        @Override
        public boolean tryAcquire() {
            return bucket.tryAcquire();
        }

        @Override
        public boolean tryAcquire(int permits) {
            return bucket.tryAcquire(permits);
        }

        @Override
        public long getCurrentCount() {
            return bucket.getWaterLevel();
        }

        @Override
        public long getMaxPermits() {
            return bucket.getCapacity();
        }

        @Override
        public RateLimitAlgorithm getAlgorithmType() {
            return RateLimitAlgorithm.LEAKY_BUCKET;
        }
    }

    /**
     * 滑动窗口策略适配器
     */
    private static final class SlidingWindowAdapter implements RateLimitStrategy {
        private final SlidingWindow window;

        SlidingWindowAdapter(long windowMs, int maxPermits) {
            this.window = new SlidingWindow(windowMs, maxPermits);
        }

        @Override
        public boolean tryAcquire() {
            return window.tryAcquire();
        }

        @Override
        public boolean tryAcquire(int permits) {
            return window.tryAcquire(permits);
        }

        @Override
        public long getCurrentCount() {
            return window.getCurrentCount();
        }

        @Override
        public long getMaxPermits() {
            return window.getMaxPermits();
        }

        @Override
        public RateLimitAlgorithm getAlgorithmType() {
            return RateLimitAlgorithm.SLIDING_WINDOW;
        }
    }
}
