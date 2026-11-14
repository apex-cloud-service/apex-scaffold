package com.apex.scaffold.infrastructure.cache.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import reactor.core.publisher.Mono;

/**
 * {@link CaffeineCacheMetricsBinder} 的真实缓存统计单元测试。
 *
 * <p>通过底层 Caffeine 的命中与未命中操作验证绑定器读取的是 {@code CacheStats} 累计值，而不是
 * 注册时拍摄的固定零值或业务层手工维护的计数。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
class CaffeineCacheMetricsBinderTest {

    /** 验证绑定器发布真实的命中/未命中累计值。 */
    @Test
    void bindToReadsCaffeineCacheStats() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder().recordStats());
        Cache cache = cacheManager.getCache("localCache");
        cache.put("hit", "value");
        cache.get("hit");
        cache.get("miss");

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new CaffeineCacheMetricsBinder(cacheManager).bindTo(registry);

        FunctionCounter hitCounter = registry.find("apex.cache.gets")
                .tags("cache", "localCache", "result", "hit")
                .functionCounter();
        FunctionCounter missCounter = registry.find("apex.cache.gets")
                .tags("cache", "localCache", "result", "miss")
                .functionCounter();

        assertThat(hitCounter).isNotNull();
        assertThat(missCounter).isNotNull();
        assertThat(hitCounter.count()).isEqualTo(1.0);
        assertThat(missCounter.count()).isEqualTo(1.0);
    }

    /**
     * 验证生产缓存配置会启用响应式 {@link Cacheable} 拦截，并将真实读取写入
     * Caffeine {@link CacheStats} 和 Micrometer {@link FunctionCounter}。
     *
     * <p>第一次订阅必须调用数据加载器并形成一次未命中；相同键的第二次订阅必须直接
     * 返回缓存值，不再调用加载器，并形成一次命中。该测试防止删除
     * {@code @EnableCaching}、异步缓存模式或指标绑定后，业务缓存与监控计数器同时
     * 静默失效。</p>
     */
    @Test
    void multiLevelCacheConfigEnablesReactiveCaching() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(MultiLevelCacheConfig.class, ReactiveCacheProbe.class);
            context.refresh();

            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            context.getBean(MeterBinder.class).bindTo(registry);

            ReactiveCacheProbe probe = context.getBean(ReactiveCacheProbe.class);
            assertThat(probe.findUser(1L).block()).isEqualTo("user-1");
            assertThat(probe.findUser(1L).block()).isEqualTo("user-1");
            assertThat(probe.getLoadCount()).isEqualTo(1);

            CaffeineCacheManager cacheManager = context.getBean(CaffeineCacheManager.class);
            Cache cache = cacheManager.getCache("user:id");
            assertThat(cache).isInstanceOf(CaffeineCache.class);
            CacheStats stats = ((CaffeineCache) cache).getNativeCache().stats();
            assertThat(stats.missCount()).isEqualTo(1L);
            assertThat(stats.hitCount()).isEqualTo(1L);

            FunctionCounter hitCounter = registry.find("apex.cache.gets")
                    .tags("cache", "user:id", "result", "hit")
                    .functionCounter();
            FunctionCounter missCounter = registry.find("apex.cache.gets")
                    .tags("cache", "user:id", "result", "miss")
                    .functionCounter();
            assertThat(hitCounter).isNotNull();
            assertThat(missCounter).isNotNull();
            assertThat(hitCounter.count()).isEqualTo(1.0);
            assertThat(missCounter.count()).isEqualTo(1.0);
        }
    }

    /** 用于验证响应式缓存命中行为和真实加载次数的测试服务。 */
    static class ReactiveCacheProbe {

        /** 实际执行数据加载逻辑的累计次数。 */
        private final AtomicInteger loadCount = new AtomicInteger();

        /**
         * 按用户 ID 加载测试值。
         *
         * <p>返回值由 Reactor {@link Mono} 延迟产生，只有真正订阅数据源时才增加加载
         * 次数；相同用户 ID 命中缓存后不应再次执行 supplier。</p>
         *
         * @param userId 用户 ID，作为 {@code user:id} 缓存键
         * @return 延迟产生的用户测试值
         */
        @Cacheable(cacheNames = "user:id", key = "#userId")
        public Mono<String> findUser(Long userId) {
            return Mono.fromSupplier(() -> {
                loadCount.incrementAndGet();
                return "user-" + userId;
            });
        }

        /**
         * 返回数据加载器的实际执行次数。
         *
         * <p>通过方法读取可确保调用由 Spring CGLIB 代理转发到真实目标对象，避免直接
         * 读取代理子类中未初始化的同名字段。</p>
         *
         * @return 数据加载器实际执行次数
         */
        public int getLoadCount() {
            return loadCount.get();
        }
    }
}
