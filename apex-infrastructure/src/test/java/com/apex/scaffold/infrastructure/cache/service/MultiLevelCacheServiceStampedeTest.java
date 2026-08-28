package com.apex.scaffold.infrastructure.cache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
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
        cacheService = new MultiLevelCacheService(localCacheService, redissonClient, new ObjectMapper());

        // L1 恒未命中
        lenient().when(localCacheService.get(anyString(), eq(String.class))).thenReturn(Mono.empty());
        lenient().when(localCacheService.set(anyString(), any(), any(Duration.class)))
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

        Mono<String> first =
                cacheService.getWithLoader("user:1", String.class, () -> load, Duration.ofMinutes(10));
        Mono<String> second =
                cacheService.getWithLoader("user:1", String.class, () -> load, Duration.ofMinutes(10));

        StepVerifier.create(Mono.zip(first, second, (a, b) -> a + "|" + b))
                .assertNext(result -> {
                    assertThat(result).isEqualTo("db-value|db-value");
                    // 击穿防护：两个并发请求只触发一次 DB 回源
                    assertThat(loaderCount.get()).isEqualTo(1);
                })
                .verifyComplete();
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
