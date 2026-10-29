package com.apex.scaffold.infrastructure.ratelimit.adaptive;

import com.apex.scaffold.common.util.ratelimit.algorithm.RateLimitAlgorithm;
import com.apex.scaffold.common.util.ratelimit.algorithm.RateLimitAlgorithmFactory;
import com.apex.scaffold.common.util.ratelimit.algorithm.RateLimitStrategy;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 自适应限流管理器
 *
 * <p>基于系统运行时指标（CPU 使用率、内存使用率、线程数、平均响应时间等）
 * 动态调整限流阈值，实现"系统负载越高，限流越严格"的自我保护机制。
 *
 * <h2>动态调整策略</h2>
 * <ol>
 *   <li><strong>CPU 使用率</strong>：当 CPU 负载超过阈值时，逐步降低允许的 QPS</li>
 *   <li><strong>内存使用率</strong>：当堆内存使用率过高时，降低准入速率</li>
 *   <li><strong>活跃线程数</strong>：线程数接近上限时收紧限流</li>
 *   <li><strong>平滑调整</strong>：使用衰减系数防止阈值剧烈抖动</li>
 * </ol>
 *
 * <h2>使用方式</h2>
 * <pre>{@code * // 获取当前自适应 QPS 上限
 * AdaptiveRateLimiter limiter = adaptiveRateLimiter;
 * int allowedQps = limiter.getAllowedQps("default", 100);
 *
 * // 结合 Sentinel 动态规则更新
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class AdaptiveRateLimiter {

    /** CPU 使用率采样间隔（毫秒） */
    private static final long SAMPLING_INTERVAL_MS = 2000L;

    /** 默认最小 QPS 阈值（无论如何都不会低于此值） */
    private static final int MIN_QPS = 10;

    /** 默认最大 QPS 阈值（无论如何都不会超过此值） */
    private static final int MAX_QPS = 10000;

    /** 衰减系数：负载降低时恢复速度的平滑因子（0-1），越小越平滑 */
    private static final double DECAY_FACTOR = 0.3;

    /** 日志输出阈值：当调整因子超过此值时才记录日志 */
    private static final double LOG_OUTPUT_THRESHOLD = 0.01;

    /** 每个处理器最多处理的线程数 */
    private static final int MAX_THREADS_PER_PROCESSOR = 25;

    /** 上一轮计算的结果缓存 */
    private final AtomicInteger cachedAllowedQps = new AtomicInteger(MAX_QPS);

    /** 上一次采样时间戳 */
    private final AtomicLong lastSampleTime = new AtomicLong(0L);

    /** 资源 → 自适应令牌桶策略实例映射 */
    private final Map<String, RateLimitStrategy> adaptiveLimiters = new ConcurrentHashMap<>();

    /** 操作系统 MXBean */
    private final com.sun.management.OperatingSystemMXBean osBean;

    /** 操作系统 MXBean（基础接口） */
    private final OperatingSystemMXBean osBeanBasic;

    /** 内存 MXBean */
    private final MemoryMXBean memoryBean;

    /** 线程 MXBean */
    private final ThreadMXBean threadBean;

    /** CPU 使用率阈值（超过此值时启动自适应降级） */
    @Value("${apex.ratelimit.adaptive.cpu-threshold:0.7}")
    private double cpuThreshold;

    /** 内存使用率阈值 */
    @Value("${apex.ratelimit.adaptive.memory-threshold:0.85}")
    private double memoryThreshold;

    /** 线程使用率阈值（当前线程数/可用线程数） */
    @Value("${apex.ratelimit.adaptive.thread-threshold:0.8}")
    private double threadThreshold;

    /** 自适应限流降级系数（负载超限时 QPS = baseQPS × (1 - factor)） */
    @Value("${apex.ratelimit.adaptive.degrade-factor:0.2}")
    private double degradeFactor;

    /**
     * 构造自适应限流管理器
     */
    public AdaptiveRateLimiter() {
        this.osBean = (com.sun.management.OperatingSystemMXBean)
                ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
        this.osBeanBasic = ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
    }

    /**
     * 获取自适应调整后的允许 QPS
     *
     * <p>基于系统负载指标动态计算当前的 QPS 上限。
     * 负载越高，返回值越保守；负载正常时返回 baseQps。
     *
     * <h2>计算步骤</h2>
     * <ol>
     *   <li>采样 CPU、内存、线程等系统指标（间隔不低于 {@link #SAMPLING_INTERVAL_MS}）</li>
     *   <li>对每个超限指标计算衰减系数</li>
     *   <li>取所有系数中最严格的一个（即降级幅度最大的）</li>
     *   <li>应用平滑衰减防止阈值抖动</li>
     * </ol>
     *
     * @param category 限流分类（如 {@code "default"}、{@code "login"}），用于日志区分
     * @param baseQps  基准 QPS（系统正常时的最高 QPS）
     * @return 自适应调整后的允许 QPS
     */
    public int getAllowedQps(String category, int baseQps) {
        long now = System.currentTimeMillis();
        long lastSample = lastSampleTime.get();

        // 采样间隔内使用缓存值，减少系统调用开销
        if (now - lastSample < SAMPLING_INTERVAL_MS) {
            int cached = cachedAllowedQps.get();
            return Math.max(MIN_QPS, Math.min(cached, baseQps));
        }

        // 采样系统指标
        if (!lastSampleTime.compareAndSet(lastSample, now)) {
            return Math.max(MIN_QPS, Math.min(cachedAllowedQps.get(), baseQps));
        }

        double cpuLoad = getCpuLoad();
        double memoryUsage = getHeapMemoryUsage();
        double threadUsage = getThreadUsage();

        // 计算各维度的降级系数
        double cpuFactor = cpuLoad > cpuThreshold ? (cpuLoad - cpuThreshold) / (1.0 - cpuThreshold) : 0.0;
        double memoryFactor =
                memoryUsage > memoryThreshold ? (memoryUsage - memoryThreshold) / (1.0 - memoryThreshold) : 0.0;
        double threadFactor =
                threadUsage > threadThreshold ? (threadUsage - threadThreshold) / (1.0 - threadThreshold) : 0.0;

        // 取最严格的降级系数
        double maxFactor = Math.max(cpuFactor, Math.max(memoryFactor, threadFactor));
        maxFactor = Math.min(1.0, maxFactor);

        // 计算新 QPS
        double degradeAmount = baseQps * maxFactor * degradeFactor;
        int newQps = Math.max(MIN_QPS, (int) Math.round(baseQps - degradeAmount));

        // 应用平滑衰减（避免抖动）
        int currentCached = cachedAllowedQps.get();
        int smoothedQps = (int) Math.round(currentCached * (1 - DECAY_FACTOR) + newQps * DECAY_FACTOR);
        smoothedQps = Math.max(MIN_QPS, Math.min(smoothedQps, baseQps));

        cachedAllowedQps.set(smoothedQps);

        if (maxFactor > LOG_OUTPUT_THRESHOLD) {
            log.info(
                    "自适应限流调整, category: {}, CPU: {}%, Mem: {}%, Thread: {}%, " + "baseQps: {}, allowedQps: {}",
                    category,
                    String.format("%.1f", cpuLoad * 100),
                    String.format("%.1f", memoryUsage * 100),
                    String.format("%.1f", threadUsage * 100),
                    baseQps,
                    smoothedQps);
        }

        return smoothedQps;
    }

    /**
     * 获取当前系统 CPU 使用率
     *
     * @return CPU 使用率（0.0 ~ 1.0），获取失败时返回 0.0
     */
    private double getCpuLoad() {
        try {
            double load = osBean.getCpuLoad();
            return load >= 0 ? load : 0.0;
        } catch (Exception e) {
            try {
                double load = osBeanBasic.getSystemLoadAverage();
                int processors = osBeanBasic.getAvailableProcessors();
                return load > 0 ? Math.min(load / processors, 1.0) : 0.0;
            } catch (Exception ex) {
                log.trace("获取 CPU 负载失败", ex);
                return 0.0;
            }
        }
    }

    /**
     * 获取当前堆内存使用率
     *
     * @return 堆内存使用率（0.0 ~ 1.0）
     */
    private double getHeapMemoryUsage() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long used = heapUsage.getUsed();
        long max = heapUsage.getMax();
        if (max > 0) {
            return (double) used / max;
        }
        return 0.0;
    }

    /**
     * 获取当前线程使用率
     *
     * <p>基于当前活跃线程数与可用处理器数的比值。
     *
     * @return 线程使用率（0.0 ~ 1.0）
     */
    private double getThreadUsage() {
        int activeThreads = threadBean.getThreadCount();
        int processors = osBeanBasic.getAvailableProcessors();
        // 估算可用线程数（假设每个处理器最多处理 MAX_THREADS_PER_PROCESSOR 个线程）
        int maxThreads = processors * MAX_THREADS_PER_PROCESSOR;
        return maxThreads > 0 ? Math.min((double) activeThreads / maxThreads, 1.0) : 0.0;
    }

    /**
     * 获取系统可用处理器数
     *
     * @return 可用处理器核心数
     */
    public int getAvailableProcessors() {
        return osBeanBasic.getAvailableProcessors();
    }

    /**
     * 重置自适应限流状态
     *
     * <p>清除缓存 QPS，下次调用时将基于最新系统指标重新计算。
     */
    public void reset() {
        cachedAllowedQps.set(MAX_QPS);
        lastSampleTime.set(0L);
        log.info("自适应限流状态已重置");
    }

    /**
     * 尝试获取自适应限流许可
     *
     * <p>基于系统负载指标动态调整令牌桶参数后执行限流判定。
     * 负载越高，令牌桶容量和补充速率越低。
     *
     * @param resource 资源标识
     * @param baseQps 基准 QPS
     * @return 获取成功返回 true，限流拒绝返回 false
     * @since 1.1.0
     */
    public boolean tryAcquireAdaptive(String resource, int baseQps) {
        int allowedQps = getAllowedQps(resource, baseQps);
        RateLimitStrategy limiter = adaptiveLimiters.computeIfAbsent(resource, k -> {
            log.info("创建自适应令牌桶, resource: {}, capacity: {}, refillRate: {}", k, allowedQps, (double) allowedQps);
            return RateLimitAlgorithmFactory.createNew(
                    RateLimitAlgorithm.TOKEN_BUCKET, allowedQps, (double) allowedQps, 0);
        });

        boolean acquired = limiter.tryAcquire();
        if (!acquired) {
            log.debug("自适应限流拒绝, resource: {}, allowedQps: {}", resource, allowedQps);
        }
        return acquired;
    }

    /**
     * 清理指定资源的自适应限流器
     *
     * @param resource 资源标识
     * @since 1.1.0
     */
    public void clearResource(String resource) {
        adaptiveLimiters.remove(resource);
        log.debug("清理自适应限流器, resource: {}", resource);
    }

    /**
     * 清理所有自适应限流器
     *
     * @since 1.1.0
     */
    public void clearAll() {
        int size = adaptiveLimiters.size();
        adaptiveLimiters.clear();
        log.info("清理所有自适应限流器, 数量: {}", size);
    }
}
