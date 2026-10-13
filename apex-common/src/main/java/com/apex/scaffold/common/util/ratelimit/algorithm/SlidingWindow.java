package com.apex.scaffold.common.util.ratelimit.algorithm;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * 高效滑动窗口限流算法实现
 *
 * <p>基于桶分片的滑动窗口算法，将时间窗口划分为多个细粒度的子桶，
 * 通过子桶计数累加实现 O(1) 时间复杂度的窗口计数。
 *
 * <h2>算法原理</h2>
 * <ul>
 *   <li><strong>子桶分片</strong>：将时间窗口 {@code windowMs} 划分为
 *       {@code bucketCount} 个等宽子桶，每个子桶记录该时间段内的请求计数</li>
 *   <li><strong>滑动窗口</strong>：请求到达时，根据当前时间定位到子桶索引，
 *       过期子桶自动清零，窗口内有效子桶计数之和即为当前请求数</li>
 *   <li><strong>时间复杂度</strong>：O(1) 计数（仅需遍历 bucketCount 个子桶，
 *       通常 ≤ 10），优于时间戳数组的 O(n) 遍历</li>
 *   <li><strong>内存效率</strong>：固定 {@code bucketCount} 个 long 槽位，
 *       不随请求量增长</li>
 * </ul>
 *
 * <h2>与朴素滑动窗口的对比</h2>
 * <table border="1">
 *   <tr><th>维度</th><th>朴素滑动窗口（旧）</th><th>桶分片滑动窗口（本实现）</th></tr>
 *   <tr><td>时间复杂度</td><td>O(n)，n=10000</td><td>O(1)，n=bucketCount</td></tr>
 *   <tr><td>内存占用</td><td>10000 个 AtomicLong</td><td>bucketCount 个 long</td></tr>
 *   <tr><td>精度</td><td>精确（时间戳级）</td><td>近似（子桶粒度）</td></tr>
 *   <tr><td>适用场景</td><td>低 QPS</td><td>高 QPS、生产环境</td></tr>
 * </table>
 *
 * <h2>线程安全</h2>
 * <p>使用 {@link ReentrantLock} 保护子桶定位、过期清理和计数的原子性。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建滑动窗口：1秒窗口，10个子桶，最大100请求
 * SlidingWindow window = new SlidingWindow(1000, 10, 100);
 *
 * // 尝试通过1个请求
 * if (window.tryAcquire()) {
 *     // 处理请求
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
public class SlidingWindow {

    /** 默认子桶数量 */
    private static final int DEFAULT_BUCKET_COUNT = 10;

    private static final int MIN_BUCKET_COUNT = 2;

    /** 窗口大小（毫秒） */
    private final long windowMs;

    /** 子桶数量 */
    private final int bucketCount;

    /** 每个子桶的时间跨度（毫秒） */
    private final long bucketTimeMs;

    /** 窗口内最大许可数 */
    private final int maxPermits;

    /** 子桶计数数组 */
    private final long[] buckets;

    /** 子桶对应的时间戳数组（记录每个子桶的起始时间） */
    private final long[] bucketStartTimes;

    /** 公平锁，保护子桶操作的原子性 */
    private final ReentrantLock lock = new ReentrantLock(true);

    /** 当前窗口内的总请求数（惰性计算） */
    private final AtomicLong currentCount;

    /** 窗口起始时间戳 */
    private final long windowStartTime;

    /** 时间源（毫秒），默认使用 {@link System#currentTimeMillis()}，可注入以便确定性测试 */
    private final LongSupplier timeSource;

    /**
     * 构造滑动窗口（使用默认子桶数量 10）。
     *
     * @param windowMs   窗口大小（毫秒）
     * @param maxPermits 窗口内最大许可数
     */
    public SlidingWindow(long windowMs, int maxPermits) {
        this(windowMs, DEFAULT_BUCKET_COUNT, maxPermits);
    }

    /**
     * 构造滑动窗口。
     *
     * @param windowMs    窗口大小（毫秒）
     * @param bucketCount 子桶数量（建议 5-20，越大精度越高但开销略增）
     * @param maxPermits  窗口内最大许可数
     */
    public SlidingWindow(long windowMs, int bucketCount, int maxPermits) {
        this(windowMs, bucketCount, maxPermits, System::currentTimeMillis);
    }

    /**
     * 构造滑动窗口（可注入时间源，便于测试）。
     *
     * @param windowMs    窗口大小（毫秒）
     * @param bucketCount 子桶数量
     * @param maxPermits  窗口内最大许可数
     * @param timeSource  毫秒时间源
     */
    SlidingWindow(long windowMs, int bucketCount, int maxPermits, LongSupplier timeSource) {
        if (windowMs <= 0) {
            throw new IllegalArgumentException("窗口大小必须大于0, 实际值: " + windowMs);
        }
        if (bucketCount < MIN_BUCKET_COUNT) {
            throw new IllegalArgumentException("子桶数量至少为2, 实际值: " + bucketCount);
        }
        if (maxPermits <= 0) {
            throw new IllegalArgumentException("最大许可数必须大于0, 实际值: " + maxPermits);
        }
        this.windowMs = windowMs;
        this.bucketCount = bucketCount;
        this.bucketTimeMs = windowMs / bucketCount;
        this.maxPermits = maxPermits;
        this.buckets = new long[bucketCount];
        this.bucketStartTimes = new long[bucketCount];
        this.currentCount = new AtomicLong(0);
        this.timeSource = timeSource;
        this.windowStartTime = timeSource.getAsLong();

        long now = this.windowStartTime;
        for (int i = 0; i < bucketCount; i++) {
            bucketStartTimes[i] = now;
        }
        log.debug("滑动窗口初始化, windowMs: {}, bucketCount: {}, maxPermits: {}", windowMs, bucketCount, maxPermits);
    }

    /**
     * 尝试通过 1 个请求（非阻塞）。
     *
     * <p>先清理过期子桶，再检查窗口内总请求数是否超限。
     * 超限时立即返回 false。
     *
     * @return 通过返回 true，超限返回 false
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 尝试通过指定数量的请求（非阻塞）。
     *
     * @param requiredPermits 需要的许可数
     * @return 通过返回 true，超限返回 false
     */
    public boolean tryAcquire(int requiredPermits) {
        if (requiredPermits <= 0) {
            return true;
        }
        if (requiredPermits > maxPermits) {
            log.debug("请求许可数 {} 超过窗口上限 {}, 直接拒绝", requiredPermits, maxPermits);
            return false;
        }

        lock.lock();
        try {
            // Step 1: 清理过期子桶并重算窗口内总计数
            long now = timeSource.getAsLong();
            long total = slideAndClean(now);

            // Step 2: 检查是否超限
            if (total + requiredPermits > maxPermits) {
                log.debug("滑动窗口拒绝, 当前计数: {}, 请求: {}, 上限: {}", total, requiredPermits, maxPermits);
                return false;
            }

            // Step 3: 定位当前子桶并累加计数
            int idx = locateBucketIndex(now);
            buckets[idx] += requiredPermits;
            currentCount.set(total + requiredPermits);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 清理过期子桶并计算窗口内有效总请求数。
     *
     * <p>遍历所有子桶，将时间戳超出窗口范围的子桶计数清零，
     * 累加有效子桶的计数。
     *
     * @param now 当前时间戳
     * @return 窗口内有效总请求数
     */
    private long slideAndClean(long now) {
        long windowStart = now - windowMs;
        long total = 0;

        for (int i = 0; i < bucketCount; i++) {
            if (bucketStartTimes[i] < windowStart) {
                // 子桶过期，清零计数
                if (buckets[i] > 0) {
                    if (log.isTraceEnabled()) {
                        log.trace("子桶 {} 过期清零, oldCount: {}, bucketTime: {}", i, buckets[i], bucketStartTimes[i]);
                    }
                    buckets[i] = 0;
                }
                // 更新子桶起始时间为当前时间（延迟初始化）
                bucketStartTimes[i] = now;
            } else {
                total += buckets[i];
            }
        }

        return total;
    }

    /**
     * 定位当前时间对应的子桶索引。
     *
     * @param now 当前时间戳
     * @return 子桶索引
     */
    private int locateBucketIndex(long now) {
        long elapsed = now - windowStartTime;
        long bucketIndex = (elapsed / bucketTimeMs) % bucketCount;
        return (int) bucketIndex;
    }

    /**
     * 获取当前窗口内的有效请求数。
     *
     * <p>触发惰性清理，返回清理后的准确计数。
     *
     * @return 当前窗口内请求数
     */
    public long getCurrentCount() {
        lock.lock();
        try {
            return slideAndClean(timeSource.getAsLong());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取窗口大小。
     *
     * @return 窗口大小（毫秒）
     */
    public long getWindowMs() {
        return windowMs;
    }

    /**
     * 获取最大许可数。
     *
     * @return 最大许可数
     */
    public int getMaxPermits() {
        return maxPermits;
    }

    /**
     * 获取子桶数量。
     *
     * @return 子桶数量
     */
    public int getBucketCount() {
        return bucketCount;
    }
}
