package com.apex.scaffold.infrastructure.cache.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.cache.config.CacheProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * 多级缓存服务穿透防护行为测试（原 BloomFilter 集成测试）
 *
 * <p><b>与原测试意图的差异说明</b>：当前 {@link MultiLevelCacheService} 的读路径并未接入
 * {@code com.apex.scaffold.infrastructure.cache.filter.BloomFilterService}——布隆过滤器是
 * 独立的可选组件（{@code @ConditionalOnProperty}），仅由 BloomFilterCacheWarmer /
 * BloomFilterRebuildTask 维护，生产类既无该依赖也无 extractNamespace 逻辑；缓存穿透防护
 * 由 L2 的空值标记（{@code __EMPTY__}）实现。因此本测试类按生产类真实行为验证：
 *
 * <ul>
 *   <li>L2 空值标记命中时 get 返回空且不回填 L1（穿透防护的真实实现）</li>
 *   <li>读路径无布隆前置拦截：get 无条件先查 L1，命中即返回且不访问 Redis</li>
 *   <li>getWithLoader 回源成功后写回 L2 并失效 L1（Write-Through），无布隆过滤器更新动作</li>
 *   <li>缓存 key 原样透传 L2，不存在命名空间拆解（无冒号 key 也不会映射到默认命名空间）</li>
 * </ul>
 *
 * <p>若后续将 BloomFilterService 接入读路径，应在本类补充对应的布隆判定/回写用例。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("多级缓存穿透防护行为测试（BloomFilter 未接入读路径）")
class MultiLevelCacheServiceBloomFilterIntegrationTest {

    @Mock
    private LocalCacheService localCacheService;

    @Mock
    private RedissonClient redissonClient;

    private MultiLevelCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new MultiLevelCacheService(
                localCacheService,
                redissonClient,
                new ObjectMapper(),
                new CacheProperties(),
                new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("L2 命中空值标记时 get 返回空且不回填 L1（穿透防护的真实实现）")
    void shouldReturnEmptyWithoutL1BackfillWhenEmptyMarkerExists() {
        // Given: L1 未命中，L2 存储的是空值标记 __EMPTY__
        String key = "user:999999";
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(localCacheService.get(key, String.class)).thenReturn(Mono.empty());
        doReturn(bucket).when(redissonClient).getBucket(eq(key), any());
        when(bucket.get()).thenReturn("__EMPTY__");

        // When & Then: get 返回空 Mono（不抛错、不产生值）
        StepVerifier.create(cacheService.get(key, String.class)).verifyComplete();

        // 空值标记命中后仅查询了一次 L2，且不回填 L1，避免污染本地缓存
        verify(redissonClient).getBucket(eq(key), any());
        verify(localCacheService, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    @DisplayName("读路径无布隆前置拦截：get 无条件先查 L1，命中即返回且不访问 Redis")
    void shouldQueryL1FirstAndReturnWithoutRedisAccessOnL1Hit() {
        // Given: L1 命中（生产类不依赖布隆判定，L1 即读路径首层）
        String key = "user:123";
        when(localCacheService.get(key, String.class)).thenReturn(Mono.just("l1-value"));

        // When & Then: 直接返回 L1 值
        StepVerifier.create(cacheService.get(key, String.class))
                .expectNext("l1-value")
                .verifyComplete();

        // L1 查询被发起，且未触发任何 L2 访问
        verify(localCacheService).get(key, String.class);
        verify(redissonClient, never()).getBucket(anyString(), any());
    }

    @Test
    @DisplayName("getWithLoader 回源成功后写回 L2 并失效 L1（Write-Through），无布隆过滤器更新")
    void shouldWriteBackL2AndEvictL1AfterSuccessfulLoader() {
        // Given: L1/L2 均未命中，DB 回源返回新用户数据
        String key = "user:new-user-123";
        Duration ttl = Duration.ofMinutes(10);
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(localCacheService.get(key, String.class)).thenReturn(Mono.empty());
        doReturn(bucket).when(redissonClient).getBucket(eq(key), any());
        when(bucket.get()).thenReturn(null);
        when(localCacheService.delete(key)).thenReturn(Mono.just(1L));

        // When & Then: 返回回源数据
        StepVerifier.create(cacheService.getWithLoader(key, String.class, () -> Mono.just("db-value"), ttl))
                .expectNext("db-value")
                .verifyComplete();

        // 真实回写行为：L2 写入回源值（含 TTL）+ 失效 L1；
        // 生产类未调用 BloomFilterService.put，故此处不声明布隆更新断言
        verify(bucket).set("db-value", ttl);
        verify(localCacheService).delete(key);
    }

    @Test
    @DisplayName("缓存 key 原样透传 L2：无命名空间提取逻辑")
    void shouldPassCacheKeyToL2AsIsWithoutNamespaceExtraction() {
        // Given: 各种格式的 key（user:123 / order:456 / product:789 / no-colon）。
        // 生产类没有 extractNamespace：key 不会被拆解为 namespace + 剩余部分，
        // 而是整串作为 Redis bucket name 透传，无冒号 key 也不会映射到默认命名空间。
        @SuppressWarnings("unchecked")
        RBucket<String> bucket = mock(RBucket.class);
        when(localCacheService.get(anyString(), eq(String.class))).thenReturn(Mono.empty());
        doReturn(bucket).when(redissonClient).getBucket(anyString(), any());
        when(bucket.get()).thenReturn(null);

        // When: 批量读取四种 key
        List<String> keys = List.of("user:123", "order:456", "product:789", "no-colon");
        StepVerifier.create(cacheService.getBatch(keys, String.class))
                .assertNext(result -> assertThat(result).isEmpty())
                .verifyComplete();

        // Then: 每个完整 key 原样作为 bucket name 访问 L2
        keys.forEach(k -> verify(redissonClient).getBucket(eq(k), any()));
    }
}
