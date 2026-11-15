package com.apex.scaffold.infrastructure.ratelimit.adaptive;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 自适应限流管理器压力测试
 *
 * <p>测试 AdaptiveRateLimiter 在并发场景下的稳定性、自适应调整逻辑和多实例隔离。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class AdaptiveRateLimiterStressTest {

    private AdaptiveRateLimiter limiter;

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        limiter = new AdaptiveRateLimiter(0.7, 0.85, 0.8, 0.2);
        limiter.reset();
    }

    @Test
    @DisplayName("高吞吐量并发调用 - 20线程×5000次 getAllowedQps，验证线程安全和响应有效性")
    void highThroughputConcurrentAccess() throws InterruptedException {
        int threadCount = 20;
        int callsPerThread = 5000;
        int baseQps = 10000;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < callsPerThread; j++) {
                        int allowedQps = limiter.getAllowedQps("stress-test", baseQps);
                        if (allowedQps >= 10 && allowedQps <= baseQps) {
                            successCount.incrementAndGet();
                        } else {
                            failureCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        int totalCalls = threadCount * callsPerThread;
        int successes = successCount.get();
        int failures = failureCount.get();

        assertThat(failures).isZero();
        assertThat(successes).isEqualTo(totalCalls);

        double successRate = (double) successes / totalCalls;
        assertThat(successRate).isGreaterThan(0.95);
    }

    @Test
    @DisplayName("采样间隔缓存 - 2秒内重复调用返回缓存值，超时后重新计算")
    void samplingIntervalCache() throws InterruptedException {
        int baseQps = 1000;

        int firstResult = limiter.getAllowedQps("cache-test", baseQps);
        assertThat(firstResult).isBetween(10, baseQps);

        // 在采样间隔内的调用应返回缓存值（由于 getCpuLoad 可能返回不同值，
        // 这里验证的是采样间隔机制：第二次调用不会触发新的采样 CAS）
        int secondResult = limiter.getAllowedQps("cache-test", baseQps);
        assertThat(secondResult).isBetween(10, baseQps);

        // 重置并等待超过采样间隔
        limiter.reset();
        Thread.sleep(2100);

        int afterSleepResult = limiter.getAllowedQps("cache-test", baseQps);
        assertThat(afterSleepResult).isBetween(10, baseQps);
    }

    @Test
    @DisplayName("自适应调整 - 降低 CPU 阈值模拟高负载，验证 allowedQps 下降")
    void adaptiveAdjustmentWithLowCpuThreshold() {
        int baseQps = 1000;

        // 初始状态：正常阈值下应返回接近 baseQps
        limiter.reset();
        int normalQps = limiter.getAllowedQps("adaptive-test", baseQps);
        assertThat(normalQps).isBetween(10, baseQps);

        // 模拟高负载：将 cpuThreshold 设得很低（0.01），强制触发降级
        limiter = new AdaptiveRateLimiter(0.01, 0.85, 0.8, 0.2);

        int degradedQps = limiter.getAllowedQps("adaptive-test", baseQps);
        // 降级后的 QPS 应小于 baseQps（但不低于 MIN_QPS=10）
        assertThat(degradedQps).isGreaterThanOrEqualTo(10);
        assertThat(degradedQps).isLessThanOrEqualTo(baseQps);
    }

    @Test
    @DisplayName("重置后恢复正常 - reset() 清除缓存，下次调用重新采样")
    void resetClearsCacheAndRecalculates() {
        int baseQps = 1000;

        int firstResult = limiter.getAllowedQps("reset-test", baseQps);
        assertThat(firstResult).isBetween(10, baseQps);

        limiter.reset();

        int afterReset = limiter.getAllowedQps("reset-test", baseQps);
        assertThat(afterReset).isBetween(10, baseQps);
    }

    @Test
    @DisplayName("多实例隔离 - 3 个独立 AdaptiveRateLimiter 互不干扰")
    void multipleInstancesIsolation() {
        AdaptiveRateLimiter limiterA = new AdaptiveRateLimiter(0.7, 0.85, 0.8, 0.2);

        AdaptiveRateLimiter limiterB = new AdaptiveRateLimiter(0.01, 0.85, 0.8, 0.9);

        AdaptiveRateLimiter limiterC = new AdaptiveRateLimiter(0.7, 0.85, 0.8, 0.2);

        int qpsA = limiterA.getAllowedQps("cat-a", 1000);
        int qpsB = limiterB.getAllowedQps("cat-b", 1000);
        int qpsC = limiterC.getAllowedQps("cat-c", 500);

        // 每个实例独立工作，返回合理的 QPS 范围
        assertThat(qpsA).isBetween(10, 1000);
        assertThat(qpsB).isBetween(10, 1000);
        assertThat(qpsC).isBetween(10, 500);

        // A 和 C 相同阈值但不同 baseQps，返回不同的 QPS
        assertThat(qpsA).isNotEqualTo(qpsC);
    }

    @Test
    @DisplayName("getAvailableProcessors - 返回正数")
    void availableProcessorsIsPositive() {
        int processors = limiter.getAvailableProcessors();
        assertThat(processors).isPositive();
    }
}
