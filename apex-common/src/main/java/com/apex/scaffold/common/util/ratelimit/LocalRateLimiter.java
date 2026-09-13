package com.apex.scaffold.common.util.ratelimit;

import com.apex.scaffold.common.util.ratelimit.algorithm.RateLimitAlgorithm;
import com.apex.scaffold.common.util.ratelimit.algorithm.RateLimitAlgorithmFactory;
import com.apex.scaffold.common.util.ratelimit.algorithm.RateLimitStrategy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;

/**
 * 本地限流器（Redis 降级兜底）
 *
 * <p>当 Redis 或 Redisson 不可用时，作为分布式限流的本地降级方案。
 * 基于 {@link RateLimitStrategy} 策略接口实现进程内限流，支持三种算法：
 * 滑动窗口（默认）、令牌桶、漏桶，确保核心限流能力不因外部依赖故障而完全失效。
 *
 * <h2>设计原理</h2>
 * <ul>
 *   <li><strong>算法可插拔</strong>：v1.3.0 重构，内部委托给
 *       {@link RateLimitStrategy} 策略接口，支持
 *       {@link RateLimitAlgorithm#SLIDING_WINDOW}（默认）、
 *       {@link RateLimitAlgorithm#TOKEN_BUCKET}、
 *       {@link RateLimitAlgorithm#LEAKY_BUCKET} 三种算法</li>
 *   <li><strong>高效滑动窗口</strong>：从 v1.2.0 的 O(n) 时间戳数组遍历
 *       升级为 O(1) 桶分片计数，内存从 10000 个 AtomicLong 降至 10 个 long 槽位</li>
 *   <li><strong>公平锁保护</strong>：策略内部使用 {@link java.util.concurrent.locks.ReentrantLock}
 *       保护 check-then-record 原子性，消除高并发下的竞态条件</li>
 *   <li><strong>快速失败</strong>：超限时立即拒绝，不阻塞等待</li>
 *   <li><strong>自动恢复</strong>：窗口滑动后自动释放容量</li>
 * </ul>
 *
 * <h2>性能对比（v1.2.0 → v1.3.0）</h2>
 * <table border="1">
 *   <tr><th>指标</th><th>v1.2.0（时间戳数组）</th><th>v1.3.0（桶分片滑动窗口）</th></tr>
 *   <tr><td>tryAcquire 时间复杂度</td><td>O(n), n=10000</td><td>O(1)</td></tr>
 *   <tr><td>内存占用/实例</td><td>~80KB</td><td>~80B</td></tr>
 *   <tr><td>1000 实例内存</td><td>~80MB</td><td>~80KB</td></tr>
 * </table>
 *
 * <h2>内存管理</h2>
 * <p>INSTANCES Map 使用 {@link ConcurrentHashMap} 存储限流器实例。
 * 调用 {@link #clearIdle(long idleThresholdMs)} 可清理超过空闲阈值的实例，
 * 推荐通过定时任务（如每10分钟）调用以防止内存泄漏。</p>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 * @see RateLimitStrategy
 * @see RateLimitAlgorithmFactory
 * @see com.apex.scaffold.common.util.ratelimit.algorithm.SlidingWindow
 */
@Slf4j
public class LocalRateLimiter {

    /** 默认窗口大小（毫秒） */
    private static final long DEFAULT_WINDOW_MS = 1000L;

    /** 默认算法类型 */
    private static final RateLimitAlgorithm DEFAULT_ALGORITHM = RateLimitAlgorithm.SLIDING_WINDOW;

    /** 限流键 → 限流器实例映射 */
    private static final Map<String, LocalRateLimiter> INSTANCES = new ConcurrentHashMap<>(16);

    /** 窗口内许可总数 */
    private final int permits;

    /** 窗口大小（毫秒） */
    private final long windowMs;

    /** 算法类型 */
    private final RateLimitAlgorithm algorithm;

    /** 底层限流策略实例 */
    private final RateLimitStrategy strategy;

    /** 最后访问时间，用于空闲检测 */
    private final AtomicLong lastAccessTime = new AtomicLong(System.currentTimeMillis());

    /**
     * 构造本地限流器（使用默认滑动窗口算法）。
     *
     * @param permits  窗口内允许的最大许可数
     * @param windowMs 窗口大小（毫秒）
     */
    LocalRateLimiter(int permits, long windowMs) {
        this(permits, windowMs, DEFAULT_ALGORITHM);
    }

    /**
     * 构造本地限流器（指定算法类型）。
     *
     * @param permits  窗口内允许的最大许可数
     * @param windowMs 窗口大小（毫秒）
     * @param algorithm 算法类型
     */
    LocalRateLimiter(int permits, long windowMs, RateLimitAlgorithm algorithm) {
        this.permits = permits;
        this.windowMs = windowMs > 0 ? windowMs : DEFAULT_WINDOW_MS;
        this.algorithm = algorithm != null ? algorithm : DEFAULT_ALGORITHM;
        this.strategy = RateLimitAlgorithmFactory.createNew(this.algorithm, permits, permits, this.windowMs);
        log.debug("本地限流器初始化, algorithm: {}, permits: {}, windowMs: {}", this.algorithm, permits, this.windowMs);
    }

    /**
     * 获取或创建本地限流器实例（使用默认滑动窗口算法）
     *
     * <p>以 {@code key} 为唯一标识，复用已存在的限流器实例。
     * 适用于与 {@code @UserRateLimit} 注解配合使用。
     *
     * @param key     限流键
     * @param permits 窗口内允许的最大许可数
     * @param windowMs 窗口大小（毫秒）
     * @return 本地限流器实例
     */
    public static LocalRateLimiter of(String key, int permits, long windowMs) {
        return of(key, permits, windowMs, DEFAULT_ALGORITHM);
    }

    /**
     * 获取或创建本地限流器实例（指定算法类型）
     *
     * @param key       限流键
     * @param permits   窗口内允许的最大许可数
     * @param windowMs  窗口大小（毫秒）
     * @param algorithm 算法类型
     * @return 本地限流器实例
     * @since 1.3.0
     */
    public static LocalRateLimiter of(String key, int permits, long windowMs, RateLimitAlgorithm algorithm) {
        return INSTANCES.computeIfAbsent(key, k -> {
            log.debug("创建本地限流器, key: {}, permits: {}, windowMs: {}, algorithm: {}", k, permits, windowMs, algorithm);
            return new LocalRateLimiter(permits, windowMs, algorithm);
        });
    }

    /**
     * 尝试获取一个许可（非阻塞）。
     *
     * <p>委托给底层 {@link RateLimitStrategy} 执行限流判定。
     *
     * @return 获取成功返回 true，限流拒绝返回 false
     */
    public boolean tryAcquire() {
        lastAccessTime.set(System.currentTimeMillis());
        return strategy.tryAcquire();
    }

    /**
     * 尝试获取指定数量的许可（非阻塞）。
     *
     * @param requiredPermits 需要获取的许可数
     * @return 获取成功返回 true，限流拒绝返回 false
     */
    public boolean tryAcquire(int requiredPermits) {
        if (requiredPermits <= 0) {
            return true;
        }
        lastAccessTime.set(System.currentTimeMillis());
        return strategy.tryAcquire(requiredPermits);
    }

    /**
     * 获取当前窗口内的请求计数
     *
     * @return 当前窗口内的请求数
     */
    public int getCurrentCount() {
        return (int) strategy.getCurrentCount();
    }

    /**
     * 获取窗口内许可上限
     *
     * @return 许可上限
     */
    public int getPermits() {
        return permits;
    }

    /**
     * 获取窗口大小（毫秒）
     *
     * @return 窗口大小
     */
    public long getWindowMs() {
        return windowMs;
    }

    /**
     * 获取当前使用的算法类型
     *
     * @return 算法类型枚举
     * @since 1.3.0
     */
    public RateLimitAlgorithm getAlgorithm() {
        return algorithm;
    }

    /**
     * 清理空闲限流器实例
     *
     * <p>定期调用以释放不再使用的限流器资源。
     * 推荐使用 {@link #clearIdle(long idleThresholdMs)} 进行精确的空闲清理。
     *
     * @see #clearIdle(long)
     */
    public static void clearIdleInstances() {
        INSTANCES.clear();
        RateLimitAlgorithmFactory.clearAll();
        log.debug("本地限流器实例已清理");
    }

    /**
     * 清理超过指定空闲时间的限流器实例
     *
     * <p>遍历所有限流器实例，移除最后访问时间超过空闲阈值的实例，
     * 防止 INSTANCES Map 无限增长导致内存泄漏。
     *
     * <h3>使用建议</h3>
     * <p>建议通过定时任务调用，如每 10 分钟清理空闲超过 30 分钟的实例：
     * <pre>{@code
     * scheduler.scheduleAtFixedRate(
     *     () -> LocalRateLimiter.clearIdle(Duration.ofMinutes(30).toMillis()),
     *     10, 10, TimeUnit.MINUTES);
     * }</pre>
     *
     * @param idleThresholdMs 空闲阈值（毫秒），超过此时间未访问的实例将被清理
     * @return 清理的实例数量
     * @since 1.1.0
     */
    public static int clearIdle(long idleThresholdMs) {
        long now = System.currentTimeMillis();

        List<String> keysToRemove = INSTANCES.entrySet().stream()
                .filter(entry -> now - entry.getValue().lastAccessTime.get() > idleThresholdMs)
                .map(Map.Entry::getKey)
                .toList();

        keysToRemove.forEach(INSTANCES::remove);

        if (!keysToRemove.isEmpty()) {
            log.debug("清理空闲限流器实例, 移除: {}, 剩余: {}", keysToRemove.size(), INSTANCES.size());
        }
        return keysToRemove.size();
    }
}
