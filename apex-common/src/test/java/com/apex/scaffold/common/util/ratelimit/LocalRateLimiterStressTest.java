package com.apex.scaffold.common.util.ratelimit;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 本地限流器压力测试
 *
 * <p>测试 LocalRateLimiter 在滑动窗口精度、突发流量处理、空闲清理和内存稳定性方面的表现。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class LocalRateLimiterStressTest {

    /**
     * 设置{@code up}。
     */
    @BeforeEach
    void setUp() {
        LocalRateLimiter.clearIdleInstances();
    }

    @AfterEach
    void tearDown() {
        LocalRateLimiter.clearIdleInstances();
    }

    @Test
    @DisplayName("滑动窗口精度 - 10 permits/s 下每秒发送11个请求持续5秒，接受量 ≈ 50（±10%）")
    void slidingWindowAccuracy() throws InterruptedException {
        int permits = 10;
        long windowMs = 1000;
        int requestsPerSecond = 11;
        int durationSeconds = 5;
        String key = "stress-sliding-window";

        LocalRateLimiter limiter = LocalRateLimiter.of(key, permits, windowMs);
        AtomicInteger accepted = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        for (int s = 0; s < durationSeconds; s++) {
            for (int i = 0; i < requestsPerSecond; i++) {
                if (limiter.tryAcquire()) {
                    accepted.incrementAndGet();
                } else {
                    rejected.incrementAndGet();
                }
                // 均匀分布每秒的请求（~91ms 间隔）
                Thread.sleep(1000L / requestsPerSecond);
            }
        }

        int expectedAccepted = permits * durationSeconds; // 50
        int totalSent = requestsPerSecond * durationSeconds; // 55

        assertThat(accepted.get() + rejected.get()).isEqualTo(totalSent);
        // 接受量在期望值的 ±10% 范围内
        assertThat(accepted.get()).isBetween((int) (expectedAccepted * 0.9), (int) (expectedAccepted * 1.1));
        assertThat(rejected.get()).isPositive();
    }

    @Test
    @DisplayName("突发流量处理 - 100 permits/s 窗口，150个请求在100ms内突发达成，约100接受50拒绝")
    void burstHandling() {
        int permits = 100;
        long windowMs = 1000;
        int burstSize = 150;
        String key = "stress-burst";

        LocalRateLimiter limiter = LocalRateLimiter.of(key, permits, windowMs);
        AtomicInteger accepted = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        long start = System.currentTimeMillis();
        for (int i = 0; i < burstSize; i++) {
            if (limiter.tryAcquire()) {
                accepted.incrementAndGet();
            } else {
                rejected.incrementAndGet();
            }
        }
        long elapsed = System.currentTimeMillis() - start;

        // 应在极短时间内完成（突发）
        assertThat(elapsed).isLessThan(500);

        assertThat(accepted.get() + rejected.get()).isEqualTo(burstSize);
        assertThat(accepted.get()).isEqualTo(permits);
        assertThat(rejected.get()).isEqualTo(burstSize - permits);
    }

    @Test
    @DisplayName("清理空闲实例并恢复 - 使用后 clearIdle(0) 移除，重建后正常工作")
    void clearIdleRecovery() throws InterruptedException {
        String key = "stress-clear-recovery";

        // 创建并使用
        LocalRateLimiter limiter = LocalRateLimiter.of(key, 10, 1000);
        assertThat(limiter.tryAcquire()).isTrue();

        // 小延迟确保 lastAccessTime 早于 clearIdle 内的 now
        Thread.sleep(1);

        // 清理空闲（阈值为 0，立即清理）
        int removed = LocalRateLimiter.clearIdle(0);
        assertThat(removed).isPositive();

        // 重新创建（of 会新建实例）
        LocalRateLimiter newLimiter = LocalRateLimiter.of(key, 10, 1000);
        assertThat(newLimiter.tryAcquire()).isTrue();
        assertThat(newLimiter.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("内存稳定性 - 创建1000个不同 key 的限流器，使用后清理，验证内存可回收")
    void memoryStability() {
        int keyCount = 1000;

        // 创建 1000 个不同 key，每个使用一次
        for (int i = 0; i < keyCount; i++) {
            String key = "stress-mem-" + i;
            LocalRateLimiter limiter = LocalRateLimiter.of(key, 10, 1000);
            limiter.tryAcquire();
        }

        // 小延迟确保 lastAccessTime 与 clearIdle 内的 now 不在同一毫秒
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证实例存在且可被清理
        int removed = LocalRateLimiter.clearIdle(0);
        assertThat(removed).isEqualTo(keyCount);

        // 触发 GC 并验证清理完成
        System.gc();

        // 再次使用同一个 key，应创建新实例且正常工作
        LocalRateLimiter recreated = LocalRateLimiter.of("stress-mem-0", 10, 1000);
        assertThat(recreated.tryAcquire()).isTrue();
    }

    @Test
    @DisplayName("多线程并发 tryAcquire - 10线程×50次请求共500次，2000 permits下全部接受，验证线程安全")
    void concurrentTryAcquire() throws InterruptedException {
        int threadCount = 10;
        int callsPerThread = 50;
        int permits = 2000;
        String key = "stress-concurrent";

        LocalRateLimiter limiter = LocalRateLimiter.of(key, permits, 1000);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger accepted = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < callsPerThread; j++) {
                        if (limiter.tryAcquire()) {
                            accepted.incrementAndGet();
                        } else {
                            rejected.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(5, TimeUnit.SECONDS);

        int total = threadCount * callsPerThread; // 500
        assertThat(accepted.get() + rejected.get()).isEqualTo(total);
        // 2000 permits >> 500 total requests, all should be accepted
        assertThat(accepted.get()).isEqualTo(total);
        assertThat(rejected.get()).isZero();
    }

    @Test
    @DisplayName("getCurrentCount - 窗口内计数准确反映实际请求数")
    void currentCountAccuracy() {
        String key = "stress-current-count";
        LocalRateLimiter limiter = LocalRateLimiter.of(key, 100, 5000);

        assertThat(limiter.getCurrentCount()).isZero();

        for (int i = 0; i < 5; i++) {
            limiter.tryAcquire();
        }

        assertThat(limiter.getCurrentCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("getPermits 和 getWindowMs - 返回构造时传入的值")
    void permitsAndWindowMsReturnedCorrectly() {
        String key = "stress-config";
        LocalRateLimiter limiter = LocalRateLimiter.of(key, 42, 777);

        assertThat(limiter.getPermits()).isEqualTo(42);
        assertThat(limiter.getWindowMs()).isEqualTo(777);
    }
}
