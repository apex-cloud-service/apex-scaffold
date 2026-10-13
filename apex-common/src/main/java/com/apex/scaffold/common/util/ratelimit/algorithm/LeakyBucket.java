package com.apex.scaffold.common.util.ratelimit.algorithm;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;

/**
 * 漏桶限流算法实现
 *
 * <p>经典的漏桶算法，请求如同水流注入桶中，桶以固定速率漏水（处理请求）。
 * 当注入速率超过漏水速率时，多余的水溢出（拒绝请求），实现严格的平滑流量输出。
 *
 * <h2>算法原理</h2>
 * <ul>
 *   <li><strong>漏水速率</strong>：桶以 {@code leakRate} 速率（请求/秒）漏水，
 *       即系统以恒定速率处理请求</li>
 *   <li><strong>水桶容量</strong>：桶容量决定缓冲队列大小，
 *       超出容量的请求被直接拒绝</li>
 *   <li><strong>惰性漏水</strong>：采用惰性计算模式，仅在请求到达时根据时间差
 *       计算已漏水量，避免后台线程开销</li>
 *   <li><strong>平滑输出</strong>：无论请求到达速率如何波动，
 *       输出速率始终恒定，适合下游处理能力有限的场景</li>
 * </ul>
 *
 * <h2>与令牌桶的区别</h2>
 * <table border="1">
 *   <tr><th>维度</th><th>令牌桶</th><th>漏桶</th></tr>
 *   <tr><td>突发流量</td><td>允许（受桶容量限制）</td><td>不允许（严格平滑）</td></tr>
 *   <tr><td>输出速率</td><td>可变（受请求到达速率影响）</td><td>恒定</td></tr>
 *   <tr><td>适用场景</td><td>API 限流、突发容忍</td><td>MQ 消费、下游保护</td></tr>
 * </table>
 *
 * <h2>线程安全</h2>
 * <p>使用 {@link ReentrantLock} 保护水位计算与更新的原子性。</p>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建漏桶：容量50，每秒漏水10个请求
 * LeakyBucket bucket = new LeakyBucket(50, 10);
 *
 * // 尝试注入1个请求
 * if (bucket.tryAcquire()) {
 *     // 处理请求
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Slf4j
public class LeakyBucket {

    /** 桶容量（最大缓冲请求数） */
    private final long capacity;

    /** 漏水速率（请求/秒） */
    private final double leakRate;

    /** 公平锁，保护水位计算与更新的原子性 */
    private final ReentrantLock lock = new ReentrantLock(true);

    /** 当前桶中水量（待处理请求数） */
    private final AtomicLong waterLevel;

    /** 上次漏水的时间戳（纳秒） */
    private final AtomicLong lastLeakTimeNanos;

    /** 时间源（纳秒），默认使用 {@link System#nanoTime()}，可注入以便确定性测试 */
    private final LongSupplier timeSource;

    /**
     * 构造漏桶。
     *
     * @param capacity 桶容量（最大缓冲请求数）
     * @param leakRate 漏水速率（请求/秒），即恒定处理速率
     */
    public LeakyBucket(long capacity, double leakRate) {
        this(capacity, leakRate, System::nanoTime);
    }

    /**
     * 构造漏桶（可注入时间源，便于测试）。
     *
     * @param capacity   桶容量（最大缓冲请求数）
     * @param leakRate   漏水速率（请求/秒）
     * @param timeSource 纳秒时间源
     */
    LeakyBucket(long capacity, double leakRate, LongSupplier timeSource) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("桶容量必须大于0, 实际值: " + capacity);
        }
        if (leakRate <= 0) {
            throw new IllegalArgumentException("漏水速率必须大于0, 实际值: " + leakRate);
        }
        this.capacity = capacity;
        this.leakRate = leakRate;
        this.waterLevel = new AtomicLong(0);
        this.timeSource = timeSource;
        this.lastLeakTimeNanos = new AtomicLong(timeSource.getAsLong());
        log.debug("漏桶初始化, capacity: {}, leakRate: {}/s", capacity, leakRate);
    }

    /**
     * 尝试注入 1 个请求（非阻塞）。
     *
     * <p>先执行惰性漏水降低水位，再检查桶是否有剩余容量。
     * 桶满时立即返回 false，不阻塞等待。
     *
     * @return 注入成功返回 true，桶满返回 false
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 尝试注入指定数量的请求（非阻塞）。
     *
     * <p>先执行惰性漏水降低水位，再检查桶是否有足够剩余容量。
     * 请求数量超过桶容量时直接拒绝。
     *
     * @param requiredPermits 需要注入的请求数
     * @return 注入成功返回 true，桶满返回 false
     */
    public boolean tryAcquire(int requiredPermits) {
        if (requiredPermits <= 0) {
            return true;
        }
        if (requiredPermits > capacity) {
            log.debug("请求量 {} 超过桶容量 {}, 直接拒绝", requiredPermits, capacity);
            return false;
        }

        lock.lock();
        try {
            // Step 1: 惰性漏水
            leakWater();

            // Step 2: 检查并注入
            long current = waterLevel.get();
            if (current + requiredPermits <= capacity) {
                waterLevel.set(current + requiredPermits);
                return true;
            }
            log.debug("桶满, 水位: {}, 需要: {}, 容量: {}", current, requiredPermits, capacity);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 惰性漏水。
     *
     * <p>根据当前时间与上次漏水时间的差值，计算已漏出的水量。
     * 水位不低于 0。
     */
    private void leakWater() {
        long now = timeSource.getAsLong();
        long last = lastLeakTimeNanos.get();
        long elapsedNanos = now - last;

        if (elapsedNanos <= 0) {
            return;
        }

        // 计算已漏出的水量
        long leakedAmount = (long) (elapsedNanos * leakRate / 1_000_000_000L);
        if (leakedAmount <= 0) {
            return;
        }

        // CAS 更新漏水时间，成功者执行漏水
        if (lastLeakTimeNanos.compareAndSet(last, now)) {
            long current = waterLevel.get();
            long newLevel = Math.max(0, current - leakedAmount);
            waterLevel.set(newLevel);
            if (log.isTraceEnabled()) {
                log.trace("漏水: -{} → {}, elapsed: {}ns", leakedAmount, newLevel, elapsedNanos);
            }
        }
    }

    /**
     * 获取当前桶中水位（待处理请求数）。
     *
     * <p>注意：此方法会触发惰性漏水，返回值是漏水后的当前水位。
     *
     * @return 当前水位
     */
    public long getWaterLevel() {
        lock.lock();
        try {
            leakWater();
            return waterLevel.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取桶容量。
     *
     * @return 桶容量
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * 获取漏水速率。
     *
     * @return 漏水速率（请求/秒）
     */
    public double getLeakRate() {
        return leakRate;
    }
}
