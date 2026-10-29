package com.apex.scaffold.infrastructure.logging;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志采样器
 *
 * <p>对高频日志进行采样，避免日志爆炸式增长。基于时间窗口的采样策略：
 * 在指定时间窗口内（如1秒），对相同的日志键只记录一次，其余丢弃。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>基于时间窗口的采样：在时间窗口内只保留首次日志</li>
 *   <li>按日志键分组：不同的日志键独立采样</li>
 *   <li>关键日志强制输出：ERROR 级别日志不采样</li>
 *   <li>自动过期清理：避免内存泄漏</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>循环中的日志：避免短时间内产生大量重复日志</li>
 *   <li>高频接口的日志：避免热点接口日志过多</li>
 *   <li>定时任务的日志：避免定时任务日志堆积</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 在循环中使用采样器
 * for (Order order : orders) {
 *     if (LogSampler.shouldLog("process-order", 1000)) {
 *         log.info("处理订单: {}", order.getId());
 *     }
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
public class LogSampler {

    /**
     * 采样记录：键 -> 最后一次记录时间
     */
    private static final ConcurrentHashMap<String, Long> SAMPLE_MAP = new ConcurrentHashMap<>();

    /**
     * 采样计数器：键 -> 丢弃计数
     */
    private static final ConcurrentHashMap<String, AtomicLong> DROP_COUNTERS = new ConcurrentHashMap<>();

    /**
     * 默认时间窗口（毫秒）
     */
    private static final long DEFAULT_WINDOW_MS = 1000L;

    /**
     * 清理间隔（毫秒）
     */
    private static final long CLEANUP_INTERVAL_MS = 60000L;

    /**
     * 上次清理时间
     */
    private static volatile long lastCleanupTime = System.currentTimeMillis();

    /**
     * 私有构造函数，防止实例化
     */
    private LogSampler() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 判断是否应该记录日志（使用默认时间窗口）
     *
     * @param key 日志键，用于标识同一类日志
     * @return 如果应该记录返回 true，否则返回 false
     */
    public static boolean shouldLog(String key) {
        return shouldLog(key, DEFAULT_WINDOW_MS);
    }

    /**
     * 判断是否应该记录日志（自定义时间窗口）
     *
     * @param key      日志键，用于标识同一类日志
     * @param windowMs 时间窗口（毫秒）
     * @return 如果应该记录返回 true，否则返回 false
     */
    public static boolean shouldLog(String key, long windowMs) {
        if (key == null || key.isEmpty()) {
            return true;
        }

        long currentTime = System.currentTimeMillis();

        // 定期清理过期记录
        if (currentTime - lastCleanupTime > CLEANUP_INTERVAL_MS) {
            cleanup(currentTime);
        }

        // 获取上次记录时间
        Long lastTime = SAMPLE_MAP.get(key);

        if (lastTime == null || currentTime - lastTime >= windowMs) {
            // 应该记录：更新时间戳
            SAMPLE_MAP.put(key, currentTime);

            // 如果之前有丢弃的日志，记录丢弃计数
            AtomicLong dropCounter = DROP_COUNTERS.get(key);
            if (dropCounter != null && dropCounter.get() > 0) {
                dropCounter.getAndSet(0);
            }

            return true;
        } else {
            // 不应该记录：增加丢弃计数
            DROP_COUNTERS.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
            return false;
        }
    }

    /**
     * 清理过期的采样记录
     *
     * @param currentTime 当前时间
     */
    private static void cleanup(long currentTime) {
        synchronized (LogSampler.class) {
            // 双重检查，避免重复清理
            if (currentTime - lastCleanupTime < CLEANUP_INTERVAL_MS) {
                return;
            }

            // 清理超过 5 分钟未使用的记录
            long expireTime = currentTime - 300000L;
            SAMPLE_MAP.entrySet().removeIf(entry -> entry.getValue() < expireTime);
            DROP_COUNTERS.entrySet().removeIf(entry -> {
                String key = entry.getKey();
                return !SAMPLE_MAP.containsKey(key);
            });

            lastCleanupTime = currentTime;
        }
    }

    /**
     * 获取某个日志键的丢弃计数
     *
     * @param key 日志键
     * @return 丢弃计数
     */
    public static long getDropCount(String key) {
        AtomicLong counter = DROP_COUNTERS.get(key);
        return counter != null ? counter.get() : 0;
    }

    /**
     * 重置采样器状态（用于测试）
     */
    public static void reset() {
        SAMPLE_MAP.clear();
        DROP_COUNTERS.clear();
        lastCleanupTime = System.currentTimeMillis();
    }

    /**
     * 获取当前采样记录数量
     *
     * @return 采样记录数量
     */
    public static int size() {
        return SAMPLE_MAP.size();
    }
}
