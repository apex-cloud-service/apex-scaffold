package com.apex.scaffold.infrastructure.ratelimit.local;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 本地限流器（Redis 降级兜底）
 *
 * <p>当 Redis 或 Redisson 不可用时，作为分布式限流的本地降级方案。
 * 基于 {@link ReentrantLock} + 滑动窗口计数实现进程内限流，确保核心限流能力
 * 不因外部依赖故障而完全失效。
 *
 * <h2>设计原理</h2>
 * <ul>
 *   <li><strong>滑动窗口</strong>：使用 {@link AtomicLong} 数组记录最近时间窗内的请求时间戳，
 *       通过遍历统计当前窗口内的有效请求数</li>
 *   <li><strong>公平锁保护</strong>：{@link ReentrantLock#tryLock()} 保护 check-then-record 原子性，
 *       消除高并发下的竞态条件（v1.2.0 从 {@code Semaphore} 重构）</li>
 *   <li><strong>快速失败</strong>：超限时立即拒绝，不阻塞等待</li>
 *   <li><strong>自动恢复</strong>：窗口滑动后自动释放容量</li>
 * </ul>
 *
 * <h2>内存管理</h2>
 * <p>INSTANCES Map 使用 {@link ConcurrentHashMap} 存储限流器实例。
 * 调用 {@link #clearIdle(long idleThresholdMs)} 可清理超过空闲阈值的实例，
 * 推荐通过定时任务（如每10分钟）调用以防止内存泄漏。</p>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
public class LocalRateLimiter {
    private static final Logger log = LoggerFactory.getLogger(LocalRateLimiter.class);

    /** 默认窗口大小（毫秒） */
    private static final long DEFAULT_WINDOW_MS = 1000L;

    /** 时间戳滚动条最大长度 */
    private static final int MAX_RECORD_SIZE = 10000;

    /** 限流键 → 限流器实例映射 */
    private static final Map<String, LocalRateLimiter> INSTANCES = new ConcurrentHashMap<>(16);

    /** 窗口内许可总数 */
    private final int permits;

    /** 窗口大小（毫秒） */
    private final long windowMs;

    /** 公平锁，保护 check-then-record 原子性，消除竞态条件 */
    private final ReentrantLock lock = new ReentrantLock(true);

    /** 窗口内请求时间戳滚动数组 */
    private final AtomicLong[] timestamps;

    /** 当前写入位置 */
    private final AtomicInteger cursor = new AtomicInteger(0);

    /** 最后访问时间，用于空闲检测 */
    private final AtomicLong lastAccessTime = new AtomicLong(System.currentTimeMillis());

    /**
     * 构造本地限流器。
     *
     * @param permits  窗口内允许的最大许可数
     * @param windowMs 窗口大小（毫秒）
     */
    LocalRateLimiter(int permits, long windowMs) {
        this.permits = permits;
        this.windowMs = windowMs > 0 ? windowMs : DEFAULT_WINDOW_MS;
        this.timestamps = new AtomicLong[MAX_RECORD_SIZE];
        for (int i = 0; i < MAX_RECORD_SIZE; i++) {
            timestamps[i] = new AtomicLong(0);
        }
    }

    /**
     * 获取或创建本地限流器实例
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
        return INSTANCES.computeIfAbsent(key, k -> {
            log.debug("创建本地限流器, key: {}, permits: {}, windowMs: {}", k, permits, windowMs);
            return new LocalRateLimiter(permits, windowMs);
        });
    }

    /**
     * 尝试获取一个许可（非阻塞）。
     *
     * <p>通过 {@link ReentrantLock} 保护 check-then-record 的原子性，
     * 消除高并发下多线程同时通过计数检查的竞态条件。
     *
     * @return 获取成功返回 true，限流拒绝返回 false
     */
    public boolean tryAcquire() {
        lastAccessTime.set(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        long cutoff = now - windowMs;

        lock.lock();
        try {
            // Step 1: 统计滑动窗口内的有效请求数
            int count = 0;
            for (int i = 0; i < MAX_RECORD_SIZE; i++) {
                long ts = timestamps[i].get();
                if (ts > cutoff && ts <= now) {
                    count++;
                }
            }

            // Step 2: 判断是否超限
            if (count >= permits) {
                log.debug("本地限流器拒绝, 当前窗口计数: {}, 上限: {}", count, permits);
                return false;
            }

            // Step 3: 记录本次请求时间戳（写入当前位置并轮转）
            int pos = cursor.getAndUpdate(c -> (c + 1) % MAX_RECORD_SIZE);
            timestamps[pos].set(now);
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 尝试获取指定数量的许可（非阻塞）。
     *
     * <p>通过 {@link ReentrantLock} 保护批量 check-then-record 的原子性。
     *
     * @param requiredPermits 需要获取的许可数
     * @return 获取成功返回 true，限流拒绝返回 false
     */
    public boolean tryAcquire(int requiredPermits) {
        if (requiredPermits <= 0) {
            return true;
        }
        lastAccessTime.set(System.currentTimeMillis());
        long now = System.currentTimeMillis();
        long cutoff = now - windowMs;

        lock.lock();
        try {
            int count = 0;
            for (int i = 0; i < MAX_RECORD_SIZE; i++) {
                long ts = timestamps[i].get();
                if (ts > cutoff && ts <= now) {
                    count++;
                }
            }

            if (count + requiredPermits > permits) {
                log.debug("本地限流器拒绝, 当前窗口计数: {}, 请求: {}, 上限: {}", count, requiredPermits, permits);
                return false;
            }

            for (int j = 0; j < requiredPermits; j++) {
                int pos = cursor.getAndUpdate(c -> (c + 1) % MAX_RECORD_SIZE);
                timestamps[pos].set(now);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取当前窗口内的请求计数
     *
     * @return 当前窗口内的请求数
     */
    public int getCurrentCount() {
        long now = System.currentTimeMillis();
        long cutoff = now - windowMs;
        int count = 0;
        for (int i = 0; i < MAX_RECORD_SIZE; i++) {
            long ts = timestamps[i].get();
            if (ts > cutoff && ts <= now) {
                count++;
            }
        }
        return count;
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
     * 清理空闲限流器实例
     *
     * <p>定期调用以释放不再使用的限流器资源。
     * 推荐使用 {@link #clearIdle(long idleThresholdMs)} 进行精确的空闲清理。
     *
     * @see #clearIdle(long)
     */
    public static void clearIdleInstances() {
        INSTANCES.clear();
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
     * <pre>{@code * scheduler.scheduleAtFixedRate(
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
        int removed = 0;
        for (Map.Entry<String, LocalRateLimiter> entry : INSTANCES.entrySet()) {
            long lastAccess = entry.getValue().lastAccessTime.get();
            if (now - lastAccess > idleThresholdMs) {
                INSTANCES.remove(entry.getKey());
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("清理空闲限流器实例, 移除: {}, 剩余: {}", removed, INSTANCES.size());
        }
        return removed;
    }
}
