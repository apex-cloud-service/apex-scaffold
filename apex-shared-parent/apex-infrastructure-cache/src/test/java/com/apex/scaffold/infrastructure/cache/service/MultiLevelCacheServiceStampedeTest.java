package com.apex.scaffold.infrastructure.cache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.cache.config.CacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 多级缓存服务击穿防护（Single-Flight）单元测试
 *
 * <p>验证 MultiLevelCacheService.getWithLoader 在缓存未命中时，
 * 同一 key 的并发回源只执行一次 DB loader，其余请求共享结果。
 *
 * @author Apex Team
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MultiLevelCacheService 缓存击穿防护测试")
class MultiLevelCacheServiceStampedeTest {

    private LocalCacheService localCacheService;

    private RedissonClient redissonClient;

    private MultiLevelCacheService cacheService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        localCacheService = mock(LocalCacheService.class);
        redissonClient = mock(RedissonClient.class);
        CacheProperties cacheProperties = new CacheProperties();
        MeterRegistry meterRegistry = new SimpleMeterRegistry();
        cacheService = new MultiLevelCacheService(
                localCacheService, redissonClient, new ObjectMapper(), cacheProperties, meterRegistry);

        // L1 恒未命中
        lenient().when(localCacheService.get(anyString(), eq(String.class))).thenReturn(Mono.empty());
        lenient()
                .when(localCacheService.set(anyString(), any(), any(Duration.class)))
                .thenReturn(Mono.just(true));
        lenient().when(localCacheService.delete(any(String[].class))).thenReturn(Mono.just(1L));

        // L2 恒未命中（首次回源前缓存为空）
        RBucket<String> bucket = mock(RBucket.class);
        lenient().doReturn(bucket).when(redissonClient).getBucket(anyString(), any());
        lenient().when(bucket.get()).thenReturn(null);
        lenient().doNothing().when(bucket).set(any(), any(Duration.class));
    }

    @Test
    @DisplayName("缓存未命中时并发回源只执行一次 DB loader")
    void shouldExecuteLoaderOnceForConcurrentMisses() {
        AtomicInteger loaderCount = new AtomicInteger();

        Mono<String> load = Mono.fromSupplier(() -> {
                    loaderCount.incrementAndGet();
                    return "db-value";
                })
                .delayElement(Duration.ofMillis(50));

        Mono<String> first = cacheService.getWithLoader("user:1", String.class, () -> load, Duration.ofMinutes(10));
        Mono<String> second = cacheService.getWithLoader("user:1", String.class, () -> load, Duration.ofMinutes(10));

        StepVerifier.create(Mono.zip(first, second, (a, b) -> a + "|" + b))
                .assertNext(result -> {
                    assertThat(result).isEqualTo("db-value|db-value");
                    // 击穿防护：两个并发请求只触发一次 DB 回源
                    assertThat(loaderCount.get()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("批量读取时 L1 命中不访问 Redis")
    void shouldNotAccessRedisForL1Hits() {
        when(localCacheService.get("user:1", String.class)).thenReturn(Mono.just("l1-value"));

        StepVerifier.create(cacheService.getBatch(java.util.List.of("user:1"), String.class))
                .assertNext(result -> assertThat(result).containsExactly("l1-value"))
                .verifyComplete();

        verify(redissonClient, never()).getBucket(anyString(), any());
    }

    @Test
    @DisplayName("批量读取时 L1 未命中查询 L2 并按输入顺序回填 L1")
    void shouldQueryL2ForL1MissesAndBackfillInInputOrder() {
        when(localCacheService.get("user:1", String.class)).thenReturn(Mono.just("l1-value"));
        when(localCacheService.get("user:2", String.class)).thenReturn(Mono.empty());
        when(localCacheService.get("user:3", String.class)).thenReturn(Mono.empty());
        @SuppressWarnings("unchecked")
        RBucket<Object> user2Bucket = mock(RBucket.class);
        @SuppressWarnings("unchecked")
        RBucket<Object> user3Bucket = mock(RBucket.class);
        when(redissonClient.getBucket(eq("user:2"), any())).thenReturn(user2Bucket);
        when(redissonClient.getBucket(eq("user:3"), any())).thenReturn(user3Bucket);
        when(user2Bucket.get()).thenReturn("l2-value");
        when(user3Bucket.get()).thenReturn("__EMPTY__");

        StepVerifier.create(cacheService.getBatch(
                        java.util.Arrays.asList("user:3", null, "user:1", "user:2"), String.class))
                .assertNext(result -> assertThat(result).containsExactly("l1-value", "l2-value"))
                .verifyComplete();

        verify(redissonClient, never()).getBucket(eq("user:1"), any());
        verify(redissonClient).getBucket(eq("user:2"), any());
        verify(redissonClient).getBucket(eq("user:3"), any());
        verify(localCacheService).set(eq("user:2"), eq("l2-value"), eq(Duration.ofMinutes(5)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void emptyLoaderIsCachedAcrossSequentialRequests() {
        RBucket<String> bucket = mock(RBucket.class);
        java.util.concurrent.atomic.AtomicReference<String> stored =
                new java.util.concurrent.atomic.AtomicReference<>();
        org.mockito.Mockito.doReturn(bucket).when(redissonClient).getBucket(eq("tenant:missing"), any());
        when(bucket.get()).thenAnswer(invocation -> stored.get());
        org.mockito.Mockito.doAnswer(invocation -> {
                    stored.set(invocation.getArgument(0));
                    return null;
                })
                .when(bucket)
                .set(any(), any(Duration.class));
        AtomicInteger count = new AtomicInteger();
        java.util.function.Supplier<Mono<String>> loader = () -> {
            count.incrementAndGet();
            return Mono.empty();
        };
        StepVerifier.create(cacheService.getWithLoader("tenant:missing", String.class, loader, Duration.ofMinutes(10)))
                .verifyComplete();
        assertThat(stored.get()).isEqualTo("__EMPTY__");
        StepVerifier.create(cacheService.getWithLoader("tenant:missing", String.class, loader, Duration.ofMinutes(10)))
                .verifyComplete();
        assertThat(count).hasValue(1);
    }

    @Test
    void concurrentEmptyLoadsAreMerged() {
        AtomicInteger count = new AtomicInteger();
        java.util.function.Supplier<Mono<String>> loader = () -> Mono.delay(Duration.ofMillis(100))
                .doOnNext(ignored -> count.incrementAndGet())
                .then(Mono.empty());
        StepVerifier.create(Mono.when(
                        cacheService.getWithLoader("missing", String.class, loader, Duration.ofMinutes(10)),
                        cacheService.getWithLoader("missing", String.class, loader, Duration.ofMinutes(10))))
                .verifyComplete();
        assertThat(count).hasValue(1);
    }

    @Test
    void failedLoaderDoesNotCreateNegativeCacheAndCanRetry() {
        AtomicInteger count = new AtomicInteger();
        java.util.function.Supplier<Mono<String>> loader = () -> {
            count.incrementAndGet();
            return Mono.error(new IllegalStateException("load failed"));
        };
        for (int attempt = 0; attempt < 2; attempt++) {
            StepVerifier.create(cacheService.getWithLoader("failed", String.class, loader, Duration.ofMinutes(10)))
                    .expectErrorMessage("load failed")
                    .verify();
        }
        assertThat(count).hasValue(2);
        RBucket<String> bucket = redissonClient.getBucket("failed", org.redisson.client.codec.StringCodec.INSTANCE);
        verify(bucket, never()).set(any(), any(Duration.class));
    }

    @Test
    @DisplayName("DB 无数据时写入空值标记并返回空")
    void shouldWriteEmptyMarkerWhenDbReturnsNull() {
        Mono<String> first =
                cacheService.getWithLoader("user:missing", String.class, Mono::empty, Duration.ofMinutes(10));
        Mono<String> second =
                cacheService.getWithLoader("user:missing", String.class, Mono::empty, Duration.ofMinutes(10));

        StepVerifier.create(Mono.zip(first.flatMap(v -> Mono.just("v")), second.flatMap(v -> Mono.just("v"))))
                .verifyComplete();
    }
}
