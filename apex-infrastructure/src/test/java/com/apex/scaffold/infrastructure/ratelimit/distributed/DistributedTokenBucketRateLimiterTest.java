package com.apex.scaffold.infrastructure.ratelimit.distributed;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 分布式令牌桶限流器单元测试
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@ExtendWith(MockitoExtension.class)
class DistributedTokenBucketRateLimiterTest {

    @Mock
    private ReactiveStringRedisTemplate reactiveRedisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    private DistributedTokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new DistributedTokenBucketRateLimiter(reactiveRedisTemplate);
    }

    @Test
    void testTryAcquire_Success() {
        // 模拟 Lua 脚本返回成功（1）
        when(reactiveRedisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L));

        StepVerifier.create(rateLimiter.tryAcquire("test-resource", 100, 10))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testTryAcquire_Rejected() {
        // 模拟 Lua 脚本返回失败（0）
        when(reactiveRedisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(0L));

        StepVerifier.create(rateLimiter.tryAcquire("test-resource", 100, 10))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testTryAcquire_RedisError_ReturnsFalse() {
        // 模拟 Redis 故障
        when(reactiveRedisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.error(new RuntimeException("Redis connection error")));

        StepVerifier.create(rateLimiter.tryAcquire("test-resource", 100, 10))
                .expectNext(false) // 错误时默认拒绝
                .verifyComplete();
    }

    @Test
    void testTryAcquire_WithMultiplePermits() {
        // 模拟获取多个令牌成功
        when(reactiveRedisTemplate.execute(any(RedisScript.class), anyList(), anyList()))
                .thenReturn(Flux.just(1L));

        StepVerifier.create(rateLimiter.tryAcquire("test-resource", 100, 10, 5))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testTryAcquire_InvalidPermits_ReturnsFalse() {
        // 请求 0 个或负数令牌
        StepVerifier.create(rateLimiter.tryAcquire("test-resource", 100, 10, 0))
                .expectNext(false)
                .verifyComplete();

        StepVerifier.create(rateLimiter.tryAcquire("test-resource", 100, 10, -1))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testTryAcquire_PermitsExceedCapacity_ReturnsFalse() {
        // 请求的令牌数超过桶容量
        StepVerifier.create(rateLimiter.tryAcquire("test-resource", 100, 10, 150))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testGetAvailableTokens_Success() {
        when(reactiveRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.just("50"));

        StepVerifier.create(rateLimiter.getAvailableTokens("test-resource"))
                .expectNext(50L)
                .verifyComplete();
    }

    @Test
    void testGetAvailableTokens_NotFound() {
        when(reactiveRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.empty());

        StepVerifier.create(rateLimiter.getAvailableTokens("test-resource"))
                .expectNext(-1L)
                .verifyComplete();
    }

    @Test
    void testGetAvailableTokens_InvalidValue() {
        when(reactiveRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.just("invalid"));

        StepVerifier.create(rateLimiter.getAvailableTokens("test-resource"))
                .expectNext(-1L)
                .verifyComplete();
    }

    @Test
    void testReset_Success() {
        when(reactiveRedisTemplate.delete(anyString(), anyString())).thenReturn(Mono.just(2L));

        StepVerifier.create(rateLimiter.reset("test-resource")).expectNext(true).verifyComplete();
    }

    @Test
    void testReset_NoKeysDeleted() {
        when(reactiveRedisTemplate.delete(anyString(), anyString())).thenReturn(Mono.just(0L));

        StepVerifier.create(rateLimiter.reset("test-resource"))
                .expectNext(false)
                .verifyComplete();
    }

    @Test
    void testWarmup_Success() {
        when(reactiveRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));

        StepVerifier.create(rateLimiter.warmup("test-resource", 50, 100))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void testWarmup_TokensExceedCapacity_AdjustedToCapacity() {
        when(reactiveRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.set(anyString(), anyString(), any())).thenReturn(Mono.just(true));

        // 请求预热 150 个令牌，但容量只有 100，应该调整为 100
        StepVerifier.create(rateLimiter.warmup("test-resource", 150, 100))
                .expectNext(true)
                .verifyComplete();
    }
}
