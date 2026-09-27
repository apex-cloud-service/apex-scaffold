package com.apex.scaffold.infrastructure.cache.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.cache.config.CacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import reactor.test.StepVerifier;

/**
 * 多级缓存服务集成测试（真实 L1 + 内存化 L2）
 *
 * <p>说明：apex-infrastructure 模块内没有 SpringBootConfiguration，{@code @SpringBootTest}
 * 在本模块无法加载上下文；且模块级测试不应依赖外部 Redis 实例。因此本测试采用手工装配：
 *
 * <ul>
 *   <li><strong>L1</strong>：真实 {@link LocalCacheService}（真实 CaffeineCacheManager），
 *       覆盖本地缓存的写入、回填与命中路径</li>
 *   <li><strong>L2</strong>：Mockito 桩化的 RedissonClient，RBucket 的 get/set 由内存
 *       Map 承载，语义等价于真实 Redis 的 bucket 读写，使测试在无 Redis 环境下常驻可跑</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MultiLevelCacheService
 */
@DisplayName("MultiLevelCacheService 集成测试（真实 L1 + 内存化 L2）")
class MultiLevelCacheServiceIntegrationTest {

    /** 模拟 Redis 的存储：bucket name（即缓存 key）→ 字符串值 */
    private final Map<String, String> redisStore = new ConcurrentHashMap<>();

    /** 按 bucket name 记录的 RBucket 桩实例，便于测试中对具体 bucket 做 verify */
    private final Map<String, RBucket<String>> buckets = new ConcurrentHashMap<>();

    private RedissonClient redissonClient;

    private MultiLevelCacheService multiLevelCacheService;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        when(redissonClient.getBucket(anyString(), any()))
                .thenAnswer(invocation -> bucketStub(invocation.getArgument(0, String.class)));

        ObjectMapper objectMapper = new ObjectMapper();
        org.springframework.cache.caffeine.CaffeineCacheManager cacheManager =
                new org.springframework.cache.caffeine.CaffeineCacheManager();
        LocalCacheService localCacheService = new LocalCacheService(cacheManager, objectMapper);
        CacheProperties cacheProperties = new CacheProperties();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        multiLevelCacheService = new MultiLevelCacheService(
                localCacheService, redissonClient, objectMapper, cacheProperties, meterRegistry);
    }

    /**
     * 构建绑定内存存储的 RBucket 桩
     *
     * <p>get 从 {@link #redisStore} 读取，set(String, Duration) 写入 {@link #redisStore}，
     * 语义等价于真实 Redis bucket 读写，无需外部 Redis。
     *
     * @param name bucket 名称（即缓存 key）
     * @return 桩化的 RBucket 实例
     */
    @SuppressWarnings("unchecked")
    private RBucket<String> bucketStub(String name) {
        return buckets.computeIfAbsent(name, key -> {
            RBucket<String> bucket = mock(RBucket.class);
            when(bucket.get()).thenAnswer(inv -> redisStore.get(key));
            doAnswer(inv -> {
                        redisStore.put(key, inv.getArgument(0));
                        return null;
                    })
                    .when(bucket)
                    .set(any(), any(Duration.class));
            return bucket;
        });
    }

    /**
     * 验证 set 将调用方传入的 TTL 原样透传给 L2
     *
     * <p>MultiLevelCacheService 本身不做 TTL 抖动（±10% 抖动由 CacheConsistencyService 负责），
     * 因此断言 L2 收到的是未修改的原始 TTL。
     */
    @Test
    @DisplayName("set 将 TTL 原样透传 L2（抖动逻辑由 CacheConsistencyService 负责）")
    void testTtlJitter() {
        String key1 = "test:jitter:1";
        String key2 = "test:jitter:2";
        Duration originalTtl = Duration.ofMinutes(10);

        StepVerifier.create(multiLevelCacheService.set(key1, "value1", originalTtl))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(multiLevelCacheService.set(key2, "value2", originalTtl))
                .expectNext(true)
                .verifyComplete();

        verify(buckets.get(key1)).set("value1", originalTtl);
        verify(buckets.get(key2)).set("value2", originalTtl);
    }

    /**
     * 验证 set → get 的完整读写链路，以及 L2 命中后回填 L1 的多级缓存行为
     *
     * <p>首次 get 从 L2 读取并异步回填 L1；第二次 get 应命中 L1，不再访问 Redis。
     */
    @Test
    @DisplayName("set 写入 L2 后 get 可读取，L2 命中回填 L1 后再次读取不再访问 Redis")
    void testSetAndGet() {
        String key = "test:basic";
        String value = "test-value";

        StepVerifier.create(multiLevelCacheService.set(key, value, Duration.ofMinutes(5)))
                .expectNext(true)
                .verifyComplete();

        StepVerifier.create(multiLevelCacheService.get(key, String.class))
                .expectNext(value)
                .verifyComplete();

        // 第二次读取命中回填后的 L1；L2 的 getBucket 共被调用 2 次（set 一次 + 首次 get 一次）
        StepVerifier.create(multiLevelCacheService.get(key, String.class))
                .expectNext(value)
                .verifyComplete();
        verify(redissonClient, times(2)).getBucket(eq(key), any());
    }
}
