package com.apex.scaffold.infrastructure.cache.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

import com.apex.scaffold.infrastructure.cache.config.CacheProperties;
import com.apex.scaffold.infrastructure.cache.prewarm.CachePrewarmStrategy;
import com.apex.scaffold.infrastructure.ratelimit.distributed.DistributedRateLimitProperties;
import com.apex.scaffold.infrastructure.ratelimit.distributed.DistributedRateLimitService;
import com.apex.scaffold.infrastructure.ratelimit.distributed.DistributedTokenBucketRateLimiter;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

class CachePrewarmServiceTest {

    @Test
    void startupDelayMustNotRunStrategyOnTimerThread() throws Exception {
        CacheProperties properties = new CacheProperties();
        properties.getPrewarm().setStartupDelaySeconds(0);
        CompletableFuture<String> thread = new CompletableFuture<>();
        CachePrewarmStrategy strategy = mock(CachePrewarmStrategy.class);
        when(strategy.getName()).thenReturn("test");
        when(strategy.prewarm()).thenAnswer(invocation -> {
            thread.complete(Thread.currentThread().getName());
            return Mono.just(3);
        });
        new CachePrewarmService(List.of(strategy), properties).run(null);
        assertTrue(thread.get(5, TimeUnit.SECONDS).startsWith("apex.general-"));
    }

    @Test
    void manualAndAllPrewarmMustBeDeferredAndIsolated() {
        CachePrewarmStrategy strategy = mock(CachePrewarmStrategy.class);
        when(strategy.getName()).thenReturn("test");
        when(strategy.prewarm()).thenAnswer(invocation -> {
            assertTrue(Thread.currentThread().getName().startsWith("apex.general-"));
            return Mono.just(3);
        });
        CachePrewarmService service = new CachePrewarmService(List.of(strategy), new CacheProperties());
        Mono<Integer> all = service.prewarmAll();
        Mono<Integer> single = service.prewarm("test");
        verify(strategy, never()).prewarm();
        assertEquals(3, all.subscribeOn(Schedulers.parallel()).block(Duration.ofSeconds(5)));
        assertEquals(3, single.subscribeOn(Schedulers.parallel()).block(Duration.ofSeconds(5)));
        verify(strategy, times(2)).prewarm();
    }

    @Test
    void tokenBatchMustIsolateRedisWaitOnCachePool() {
        ReactiveStringRedisTemplate redis = mock(ReactiveStringRedisTemplate.class);
        when(redis.execute(any(), anyList(), anyList())).thenAnswer(invocation -> {
            assertTrue(Thread.currentThread().getName().startsWith("apex.cache-"));
            return Flux.just(1L);
        });
        Mono<Long> batch = new DistributedTokenBucketRateLimiter(redis).tryAcquireBatch("test", 10, 1, 1, 2);
        verifyNoInteractions(redis);
        assertEquals(2L, batch.subscribeOn(Schedulers.parallel()).block(Duration.ofSeconds(5)));
    }

    @Test
    void warmupAllMustIsolateRedisWaitOnCachePool() {
        DistributedTokenBucketRateLimiter limiter = mock(DistributedTokenBucketRateLimiter.class);
        DistributedRateLimitProperties properties = new DistributedRateLimitProperties();
        DistributedRateLimitProperties.RateLimitRule rule = new DistributedRateLimitProperties.RateLimitRule();
        rule.setResource("test");
        rule.setCapacity(10);
        rule.setEnabled(true);
        properties.setRules(List.of(rule));
        when(limiter.warmup("test", 5, 10)).thenAnswer(invocation -> {
            assertTrue(Thread.currentThread().getName().startsWith("apex.cache-"));
            return Mono.just(true);
        });
        Mono<Long> batch = new DistributedRateLimitService(limiter, properties).warmupAll();
        verifyNoInteractions(limiter);
        assertEquals(1L, batch.subscribeOn(Schedulers.parallel()).block(Duration.ofSeconds(5)));
    }
}
