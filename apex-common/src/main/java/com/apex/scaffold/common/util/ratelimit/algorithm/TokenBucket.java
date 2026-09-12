package com.apex.scaffold.common.util.ratelimit.algorithm;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 令牌桶限流算法实现
 *
 * <p>经典的令牌桶算法，以固定速率向桶中放入令牌，请求到达时消耗令牌。
 * 支持突发流量（桶满时可一次性消耗多个令牌），适用于允许短时突发但需
 * 控制平均速率的场景。
 *
 * <h2>算法原理</h2>
 * <ul>
 *   <li><strong>令牌生成</strong>：以 {@code refillRate} 速率（令牌/秒）持续向桶中添加令牌，
 *       直到达到桶容量上限</li>
 *   <li><strong>令牌消耗</strong>：每次请求消耗 1 个（或指定数量）令牌，
 *       令牌不足时拒绝请求</li>
 *   <li><strong>突发容忍</strong>：桶容量决定最大突发量，
 *       容量越大允许的瞬时突发流量越高</li>
 *   <li><strong>惰性补充</strong>：采用惰性计算模式，仅在请求到达时根据时间差
 *       计算应补充的令牌数，避免后台线程开销</li>
 * </ul>
 *
 * <h2>线程安全</h2>
 * <p>使用 {@link ReentrantLock} 保护令牌补充与消耗的原子性，
 * 确保高并发下不会超发令牌。</p>
 *
 * <h2>适用场景</h2>
 * <ul>
 *   <li>API 限流（允许短时突发，控制平均 QPS）</li>
 *   <li>缓存重建保护（控制回源速率，防止 DB 过载）</li>
 *   <li>SMS/邮件发送频率控制</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 创建令牌桶：容量100，每秒补充10个令牌
 * TokenBucket bucket = new TokenBucket(100, 10);
 *
 * // 尝试获取1个令牌
 * if (bucket.tryAcquire()) {
 *     // 处理请求
 * }
 *
 * // 尝试获取5个令牌（批量操作）
 * if (bucket.tryAcquire(5)) {
 *     // 批量处理
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
public class TokenBucket {
    private static final Logger log = LoggerFactory.getLogger(TokenBucket.class);

    /** 桶容量（最大令牌数） */
    private final long capacity;

    /** 令牌补充速率（令牌/秒） */
    private final double refillRate;

    /** 公平锁，保护令牌补充与消耗的原子性 */
    private final ReentrantLock lock = new ReentrantLock(true);

    /** 当前可用令牌数（惰性计算） */
    private final AtomicLong availableTokens;

    /** 上次令牌补充的时间戳（纳秒） */
    private final AtomicLong lastRefillTimeNanos;

    /**
     * 构造令牌桶。
     *
     * @param capacity   桶容量（最大令牌数），决定突发流量上限
     * @param refillRate 令牌补充速率（令牌/秒），决定平均速率
     */
    public TokenBucket(long capacity, double refillRate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("桶容量必须大于0, 实际值: " + capacity);
        }
        if (refillRate <= 0) {
            throw new IllegalArgumentException("补充速率必须大于0, 实际值: " + refillRate);
        }
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.availableTokens = new AtomicLong(capacity);
        this.lastRefillTimeNanos = new AtomicLong(System.nanoTime());
        log.debug("令牌桶初始化, capacity: {}, refillRate: {}/s", capacity, refillRate);
    }

    /**
     * 尝试获取 1 个令牌（非阻塞）。
     *
     * <p>先执行惰性令牌补充，再检查令牌是否充足。
     * 令牌不足时立即返回 false，不阻塞等待。
     *
     * @return 获取成功返回 true，令牌不足返回 false
     */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 尝试获取指定数量的令牌（非阻塞）。
     *
     * <p>先执行惰性令牌补充，再检查令牌是否充足。
     * 请求数量超过桶容量时直接拒绝。
     *
     * @param requiredPermits 需要获取的令牌数
     * @return 获取成功返回 true，令牌不足返回 false
     */
    public boolean tryAcquire(int requiredPermits) {
        if (requiredPermits <= 0) {
            return true;
        }
        if (requiredPermits > capacity) {
            log.debug("请求令牌数 {} 超过桶容量 {}, 直接拒绝", requiredPermits, capacity);
            return false;
        }

        lock.lock();
        try {
            // Step 1: 惰性补充令牌
            refillTokens();

            // Step 2: 检查并消耗令牌
            long current = availableTokens.get();
            if (current >= requiredPermits) {
                availableTokens.set(current - requiredPermits);
                return true;
            }
            log.debug("令牌不足, 需要: {}, 可用: {}", requiredPermits, current);
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 惰性补充令牌。
     *
     * <p>根据当前时间与上次补充时间的差值，计算应补充的令牌数。
     * 补充后不超过桶容量上限。
     */
    private void refillTokens() {
        long now = System.nanoTime();
        long last = lastRefillTimeNanos.get();
        long elapsedNanos = now - last;

        if (elapsedNanos <= 0) {
            return;
        }

        // 计算应补充的令牌数
        long tokensToAdd = (long) (elapsedNanos * refillRate / 1_000_000_000L);
        if (tokensToAdd <= 0) {
            return;
        }

        // CAS 更新补充时间，成功者执行补充
        if (lastRefillTimeNanos.compareAndSet(last, now)) {
            long current = availableTokens.get();
            long newTokens = Math.min(capacity, current + tokensToAdd);
            availableTokens.set(newTokens);
            if (log.isTraceEnabled()) {
                log.trace("令牌补充: +{} → {}, elapsed: {}ns", tokensToAdd, newTokens, elapsedNanos);
            }
        }
    }

    /**
     * 获取当前可用令牌数。
     *
     * <p>注意：此方法会触发惰性补充，返回值包含已补充但未消耗的令牌。
     *
     * @return 可用令牌数
     */
    public long getAvailableTokens() {
        lock.lock();
        try {
            refillTokens();
            return availableTokens.get();
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
     * 获取令牌补充速率。
     *
     * @return 补充速率（令牌/秒）
     */
    public double getRefillRate() {
        return refillRate;
    }
}
