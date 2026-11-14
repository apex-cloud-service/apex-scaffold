package com.apex.scaffold.infrastructure.cache.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.concurrent.TimeUnit;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;

/**
 * 多级缓存配置类
 *
 * <p>配置两级缓存体系：一级本地缓存（Caffeine）+ 二级分布式缓存（Redis/Redisson）。
 * 一级缓存提供微秒级的访问速度，适合存储高频访问的热点数据；
 * 二级缓存提供跨进程共享的分布式缓存能力。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>读取策略（Read-Through）</strong>：优先查 L1（Caffeine），未命中则查 L2（Redis），
 *       仍未命中则回源到数据库，并将结果逐级写入 L1 和 L2</li>
 *   <li><strong>写入策略（Write-Through）</strong>：更新时同步写入 L2（Redis），
 *       并失效 L1（Caffeine）的对应条目</li>
 *   <li><strong>失效策略</strong>：L1 使用较短 TTL（秒级）配合最大条目限制，
 *       L2 使用较长 TTL（分钟级）</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>用户基础信息缓存（热点数据，高频读取，低频更新）</li>
 *   <li>字典数据、配置数据（几乎不更新，读取频率极高）</li>
 *   <li>会话数据（可接受秒级不一致）</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>{@link EnableCaching} 激活项目中 {@code @Cacheable} 等 Spring 缓存注解。当前
 * Spring 缓存拦截器会缓存 Reactor {@code Mono} 实际产生的值，而不是缓存 Publisher
 * 对象；{@link CaffeineCacheManager} 的异步缓存模式为响应式返回值提供
 * {@link java.util.concurrent.CompletableFuture} 读取能力，缓存命中时直接返回已保存的
 * 业务值。Caffeine 的 {@code recordStats()} 据此记录真实的命中和未命中读取。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@AutoConfiguration
@EnableCaching
@ConditionalOnProperty(prefix = "apex.features", name = "cache", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(CaffeineCacheManager.class)
public class MultiLevelCacheConfig {

    /** 默认本地缓存最大条目数 */
    private static final int DEFAULT_LOCAL_MAX_SIZE = 10000;

    /** 默认本地缓存过期时间（秒） */
    private static final int DEFAULT_LOCAL_EXPIRE_SECONDS = 300;

    /** 默认本地缓存初始容量 */
    private static final int DEFAULT_LOCAL_INITIAL_CAPACITY = 500;

    /**
     * 创建 Caffeine 本地缓存管理器。
     *
     * <p>配置基于 Caffeine 的一级缓存，支持最大条目数、过期时间和初始容量。
     * 异步缓存模式是 Spring 缓存拦截器适配 Reactor {@code Mono} 的必要条件，否则
     * 首次调用响应式 {@code @Cacheable} 方法时会因缺少 Caffeine {@code AsyncCache}
     * 失败。真实命中/未命中统计由 {@link CaffeineCacheMetricsBinder} 直接读取
     * Caffeine {@code CacheStats}，因此面板不会依赖手工计数或固定的初始值。</p>
     *
     * @return 配置完成的 CaffeineCacheManager
     */
    @Bean
    public CaffeineCacheManager caffeineCacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        // Spring 6.2 通过异步 Cache.retrieve(...) 适配 Mono 返回值；同步模式会在
        // 第一次调用 UserService 的响应式 @Cacheable 方法时抛出配置异常。
        cacheManager.setAsyncCacheMode(true);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                // 设置最大缓存条目数，超出时基于 W-TinyLFU 算法淘汰
                .maximumSize(DEFAULT_LOCAL_MAX_SIZE)
                // 写入后固定过期时间
                .expireAfterWrite(DEFAULT_LOCAL_EXPIRE_SECONDS, TimeUnit.SECONDS)
                // 设置初始容量，减少扩容开销
                .initialCapacity(DEFAULT_LOCAL_INITIAL_CAPACITY)
                // 启用统计信息收集（用于 Micrometer 监控）
                .recordStats());

        log.info(
                "Caffeine 本地缓存已初始化, asyncCacheMode: true, maxSize: {}, " + "expireAfterWrite: {}s, initialCapacity: {}",
                DEFAULT_LOCAL_MAX_SIZE,
                DEFAULT_LOCAL_EXPIRE_SECONDS,
                DEFAULT_LOCAL_INITIAL_CAPACITY);
        return cacheManager;
    }

    /**
     * 创建 Caffeine 缓存指标绑定器。
     *
     * <p>绑定器只读取项目实际使用的 {@code localCache}、{@code user:openId} 和
     * {@code user:id} 缓存的累计命中/未命中统计，并以 FunctionCounter 暴露给 Prometheus。
     * Prometheus 抓取线程只读取本地 Caffeine 统计，不执行数据库或 Redis 查询。</p>
     *
     * @param cacheManager Caffeine 缓存管理器
     * @return Caffeine 指标绑定器
     */
    @Bean
    public MeterBinder caffeineCacheMetrics(CaffeineCacheManager cacheManager) {
        return new CaffeineCacheMetricsBinder(cacheManager);
    }

    /**
     * 缓存配置属性类
     *
     * <p>通过 {@code apex.cache} 前缀配置多级缓存的相关参数，
     * 支持在 application.yml 中灵活调整各层缓存的行为。
     */
    @Data
    @ConfigurationProperties(prefix = "apex.cache")
    public static class CacheProperties {

        /** 一级本地缓存（Caffeine）配置 */
        private Local local = new Local();

        /** 二级远程缓存（Redis）配置 */
        private Remote remote = new Remote();

        /**
         * 本地缓存（Caffeine）配置
         *
         * <p>用于控制一级本地缓存的容量、过期时间和并发级别。
         */
        @Data
        public static class Local {

            /** 最大缓存条目数 */
            private int maxSize = 10000;

            /** 写入后过期时间（秒） */
            private int expireAfterWrite = 300;

            /** 初始容量 */
            private int initialCapacity = 500;
        }

        /**
         * 远程缓存（Redis）配置
         *
         * <p>用于控制二级分布式缓存的默认过期时间和批量操作大小。
         */
        @Data
        public static class Remote {

            /** 默认过期时间（秒） */
            private long defaultTtl = 3600;

            /** 批量操作最大条目数 */
            private int batchSize = 100;
        }
    }
}
