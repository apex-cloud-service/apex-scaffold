package com.apex.scaffold.common.util.ratelimit.algorithm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RateLimitAlgorithmTest {

    /**
     * 可手动推进的测试时钟：内部以纳秒为基准，毫秒视图由纳秒换算，
     * 使限流算法的时间推进完全确定，避免依赖真实时钟导致的偶发失败。
     */
    private static final class MutableClock {
        private static final long NANOS_PER_MILLI = 1_000_000L;

        private final AtomicLong nanos;

        MutableClock(long startNanos) {
            this.nanos = new AtomicLong(startNanos);
        }

        long nanoTime() {
            return nanos.get();
        }

        long currentTimeMillis() {
            return nanos.get() / NANOS_PER_MILLI;
        }

        void advanceMillis(long millis) {
            nanos.addAndGet(millis * NANOS_PER_MILLI);
        }
    }

    @Test
    void tokenBucketLimitsBurstAndRefills() {
        MutableClock clock = new MutableClock(1_000_000_000L);
        TokenBucket bucket = new TokenBucket(2, 1000.0, clock::nanoTime);
        assertTrue(bucket.tryAcquire(2));
        assertFalse(bucket.tryAcquire());
        clock.advanceMillis(5);
        assertTrue(bucket.tryAcquire());
    }

    @Test
    void leakyBucketRejectsWhenFullAndLeaks() {
        MutableClock clock = new MutableClock(1_000_000_000L);
        LeakyBucket bucket = new LeakyBucket(1, 1000.0, clock::nanoTime);
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
        clock.advanceMillis(5);
        assertTrue(bucket.tryAcquire());
    }

    @Test
    void slidingWindowExpiresRequests() {
        MutableClock clock = new MutableClock(1_000_000_000L);
        SlidingWindow window = new SlidingWindow(30, 3, 1, clock::currentTimeMillis);
        assertTrue(window.tryAcquire());
        assertFalse(window.tryAcquire());
        clock.advanceMillis(45);
        assertTrue(window.tryAcquire());
    }
}
