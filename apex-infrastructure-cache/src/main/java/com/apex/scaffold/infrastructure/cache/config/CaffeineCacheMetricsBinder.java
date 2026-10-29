package com.apex.scaffold.infrastructure.cache.config;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.util.List;
import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;

/**
 * 将项目实际使用的 Caffeine L1 缓存统计绑定到 Micrometer。
 *
 * <p>该绑定器针对项目中明确启用的 {@code localCache}、{@code user:openId} 和
 * {@code user:id} 三个缓存注册指标，不依赖可能未启用的自动
 * {@code CacheMetricsRegistrar}，也不要求业务代码手工维护命中/未命中计数。</p>
 *
 * <p>指标值直接从 Caffeine 的 {@link CacheStats} 读取。Caffeine 缓存必须在创建时启用
 * {@code recordStats()}，当前配置由 {@link MultiLevelCacheConfig} 完成。Micrometer
 * 只在抓取指标时读取当前累计统计快照，因此不会向缓存执行额外的 get、put、evict 或
 * 数据库/Redis 操作。</p>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>基础名称为 {@code apex.cache.gets}，Prometheus 导出名称为
 *       {@code apex_cache_gets_total}。</li>
 *   <li>每个缓存包含 {@code result=hit} 和 {@code result=miss} 两个标签值，
 *       分别对应 {@link CacheStats#hitCount()} 和 {@link CacheStats#missCount()}。</li>
 *   <li>{@code cache} 标签对应 Caffeine 缓存实际名称。</li>
 *   <li>指标只描述 Caffeine L1 缓存，不代表 Redis 等 L2 分布式缓存的命中率。</li>
 * </ul>
 *
 * <p>如果缓存管理器对某个名称返回 {@code null} 或返回的实现不是
 * {@link CaffeineCache}，该缓存会被跳过，不会创建虚构的指标样本。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see CaffeineCacheManager
 * @see CacheStats
 * @see FunctionCounter
 * @see MeterRegistry
 */
@lombok.RequiredArgsConstructor
public final class CaffeineCacheMetricsBinder implements MeterBinder {

    /**
     * Micrometer 指标基础名。
     *
     * <p>由于指标通过 {@link FunctionCounter} 注册，Prometheus 适配器会在导出时追加
     * {@code _total} 后缀，并将名称中的点转换为下划线。</p>
     */
    private static final String METRIC_NAME = "apex.cache.gets";

    /**
     * 项目中已确认启用 Caffeine L1 的缓存名称。
     *
     * <p>列表保持不可变，绑定器只对这些名称采集统计，避免把缓存管理器中的其他缓存
     * 或未来新增缓存意外纳入当前监控契约。</p>
     */
    private static final List<String> CACHE_NAMES = List.of("localCache", "user:openId", "user:id");

    /** 用于按名称获取项目 L1 缓存实例的 Caffeine 缓存管理器。 */
    private final CaffeineCacheManager cacheManager;

    /**
     * 将已确认的 Caffeine 缓存统计绑定到 Micrometer 注册表。
     *
     * <p>方法按 {@link #CACHE_NAMES} 中的固定名称从缓存管理器获取缓存，并为每个有效
     * {@link CaffeineCache} 注册一组带有 {@code result=hit|miss} 标签的
     * {@link FunctionCounter}。缓存管理器返回 {@code null} 或其他缓存实现时跳过，
     * 不创建表示虚构缓存流量的指标。</p>
     *
     * <p>注册后的计数器通过函数读取 Caffeine 的累计统计值，而不是在每次绑定时拍摄
     * 固定快照。因此 Prometheus 后续抓取可以观察缓存运行期间真实增长的命中和未命中
     * 数量。面板可直接读取当前实例生命周期累计命中率；需要分析短窗口变化时，也可以
     * 使用 {@code rate()} 或 {@code increase()} 计算 Counter 的窗口增量。</p>
     *
     * @param registry Micrometer 指标注册表，不能为 {@code null}
     * @throws NullPointerException 当缓存管理器或指标注册表为 {@code null} 时
     */
    @Override
    public void bindTo(MeterRegistry registry) {
        CACHE_NAMES.forEach(cacheName -> {
            Cache cache = cacheManager.getCache(cacheName);
            bindCache(cache instanceof CaffeineCache caffeineCache ? caffeineCache : null, registry);
        });
    }

    /**
     * 为单个 Caffeine 缓存注册命中和未命中累计计数器。
     *
     * <p>命中计数器和未命中计数器共用 {@link #METRIC_NAME}，通过 {@code result} 标签
     * 区分结果类型，并通过 {@code cache} 标签区分缓存实例。两个计数器都以缓存对象
     * 作为观测源，Micrometer 抓取时分别调用 {@link #cacheStats(CaffeineCache)} 获取
     * 最新累计值。</p>
     *
     * <p>当缓存为 {@code null} 时直接返回，表示当前缓存管理器没有可用的 Caffeine
     * 缓存实例。</p>
     *
     * @param cache Caffeine 缓存；为 {@code null} 时不注册任何指标
     * @param registry Micrometer 指标注册表，不能为 {@code null}
     */
    private void bindCache(CaffeineCache cache, MeterRegistry registry) {
        if (cache == null) {
            return;
        }
        FunctionCounter.builder(METRIC_NAME, cache, value -> cacheStats(value).hitCount())
                .tag("cache", cache.getName())
                .tag("result", "hit")
                .description("Caffeine L1 缓存读取结果累计次数")
                .register(registry);
        FunctionCounter.builder(METRIC_NAME, cache, value -> cacheStats(value).missCount())
                .tag("cache", cache.getName())
                .tag("result", "miss")
                .description("Caffeine L1 缓存读取结果累计次数")
                .register(registry);
    }

    /**
     * 读取缓存当前的累计统计快照。
     *
     * <p>方法通过 {@link CaffeineCache#getNativeCache()} 访问底层 Caffeine 缓存，
     * 再读取其 {@link com.github.benmanes.caffeine.cache.Cache#stats()} 快照。快照中的
     * 命中和未命中值是从缓存启用统计以来的累计值，适合提供给
     * {@link FunctionCounter}；本方法不修改缓存状态。</p>
     *
     * @param cache 已启用统计的 Caffeine 缓存
     * @return Caffeine 当前累计命中/未命中统计快照
     */
    private CacheStats cacheStats(CaffeineCache cache) {
        return cache.getNativeCache().stats();
    }
}
