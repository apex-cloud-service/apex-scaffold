package com.apex.scaffold.common.util.ratelimit.algorithm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RateLimitAlgorithmTest {
    @Test
    void tokenBucketLimitsBurstAndRefills() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(2, 1000.0);
        assertTrue(bucket.tryAcquire(2));
        assertFalse(bucket.tryAcquire());
        Thread.sleep(5);
        assertTrue(bucket.tryAcquire());
    }

    @Test
    void leakyBucketRejectsWhenFullAndLeaks() throws InterruptedException {
        LeakyBucket bucket = new LeakyBucket(1, 1000.0);
        assertTrue(bucket.tryAcquire());
        assertFalse(bucket.tryAcquire());
        Thread.sleep(5);
        assertTrue(bucket.tryAcquire());
    }

    @Test
    void slidingWindowExpiresRequests() throws InterruptedException {
        SlidingWindow window = new SlidingWindow(30, 3, 1);
        assertTrue(window.tryAcquire());
        assertFalse(window.tryAcquire());
        Thread.sleep(45);
        assertTrue(window.tryAcquire());
    }
}
