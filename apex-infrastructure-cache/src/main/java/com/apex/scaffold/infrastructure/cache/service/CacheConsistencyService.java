package com.apex.scaffold.infrastructure.cache.service;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.service.CacheConsistencyPort;
import com.apex.scaffold.infrastructure.cache.config.CacheProperties;
import com.apex.scaffold.infrastructure.cache.support.InFlightRequestMerge;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 缓存一致性服务
 *
 * <p>统一管理多级缓存（L1 Caffeine + L2 Redis）的同步更新，确保数据一致性。
 * 提供缓存写入、删除、批量操作等功能，自动处理两级缓存的同步。
 *
 * <h2>设计目标</h2>
 * <ul>
 *   <li><strong>一致性保障</strong>：写入/删除操作同时更新L1和L2缓存，避免脏数据</li>
 *   <li><strong>原子性操作</strong>：确保两级缓存操作的原子性，避免部分更新</li>
 *   <li><strong>性能优化</strong>：采用异步写入策略，减少主业务延迟</li>
 *   <li><strong>统一接口</strong>：提供简洁的API，隐藏多级缓存的复杂性</li>
 * </ul>
 *
 * <h2>缓存策略</h2>
 * <ul>
 *   <li><strong>写入策略</strong>：先写L2（Redis），再失效L1（Caffeine）</li>
 *   <li><strong>删除策略</strong>：同时删除L2和L1</li>
 *   <li><strong>读取策略</strong>：先读L1，未命中则读L2并回填L1</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>用户信息更新后同步失效缓存</li>
 *   <li>积分账户变更后的缓存刷新</li>
 *   <li>配置数据更新后的全局缓存同步</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see LocalCacheService
 * @see InFlightRequestMerge
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheConsistencyService implements CacheConsistencyPort {

    /** 空值标记：用于缓存穿透防护 */
    private static final String EMPTY_VALUE = "__EMPTY__";

    /** 空值标记 TTL：5 分钟 */
    private static final Duration EMPTY_TTL = Duration.ofMinutes(5);

    /** 默认L1缓存TTL：5分钟 */
    private static final Duration DEFAULT_L1_TTL = Duration.ofMinutes(5);

    /** 默认L2缓存TTL：1小时 */
    private static final Duration DEFAULT_L2_TTL = Duration.ofHours(1);

    /** TTL jitter 因子：±10% */
    private static final double JITTER_FACTOR = 0.1;

    /** 回源请求合并器：缓存击穿防护（Single-Flight），同 key 并发回源只执行一次 */
    private final InFlightRequestMerge inFlight = new InFlightRequestMerge();

    private final CacheManager cacheManager;

    private final RedissonClient redissonClient;

    private final ObjectMapper objectMapper;

    private final CacheProperties cacheProperties;

    /**
     * 向两级缓存写入值（带自定义TTL）
     *
     * <p>写入策略：先写L2（Redis）保证分布式一致性，再失效L1（Caffeine）。
     * L1将在下次读取时从L2重新加载，确保数据一致性。
     *
     * @param <T>       缓存值类型
     * @param cacheName 缓存名称/前缀
     * @param key       缓存键
     * @param value     缓存值
     * @param l1Ttl     L1缓存过期时间
     * @param l2Ttl     L2缓存过期时间
     * @return 写入成功返回true
     */
    public <T> Mono<Boolean> put(String cacheName, String key, T value, Duration l1Ttl, Duration l2Ttl) {
        if (key == null || cacheName == null) {
            log.warn("缓存键或缓存名称为空，跳过写入");
            return Mono.just(false);
        }

        String fullKey = buildFullKey(cacheName, key);
        // 对 L2 TTL 应用 jitter，防止缓存雪崩
        Duration jitteredL2Ttl = jitter(l2Ttl, JITTER_FACTOR);

        return Mono.fromCallable(() -> {
                    // Step 1: 写入 L2（Redis）
                    RBucket<String> bucket = redissonClient.getBucket(fullKey, StringCodec.INSTANCE);
                    bucket.set(toStoreValue(value), jitteredL2Ttl);
                    log.debug("L2缓存写入成功, cacheName: {}, key: {}", cacheName, key);
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .flatMap(success -> {
                    if (success) {
                        // Step 2: 失效 L1（Caffeine）- 使用异步写入策略
                        return evictL1Cache(cacheName, key).thenReturn(true);
                    }
                    return Mono.just(false);
                })
                .doOnSuccess(result -> log.info("缓存写入完成, cacheName: {}, key: {}, success: {}", cacheName, key, result))
                .doOnError(e -> log.error("缓存写入失败, cacheName: {}, key: {}", cacheName, key, e));
    }

    /**
     * 向两级缓存写入值（使用默认TTL）
     *
     * <p>使用默认TTL：L1为5分钟，L2为1小时。
     *
     * @param <T>       缓存值类型
     * @param cacheName 缓存名称/前缀
     * @param key       缓存键
     * @param value     缓存值
     * @return 写入成功返回true
     */
    public <T> Mono<Boolean> put(String cacheName, String key, T value) {
        return put(cacheName, key, value, DEFAULT_L1_TTL, DEFAULT_L2_TTL);
    }

    /**
     * 从两级缓存删除指定键
     *
     * <p>删除策略：同时删除L2（Redis）和L1（Caffeine），确保数据一致性。
     *
     * @param cacheName 缓存名称/前缀
     * @param key       缓存键
     * @return 删除成功返回true
     */
    @Override
    public Mono<Boolean> evict(String cacheName, String key) {
        if (key == null || cacheName == null) {
            log.warn("缓存键或缓存名称为空，跳过删除");
            return Mono.just(false);
        }

        return deleteBothLevels(cacheName, key)
                .doOnSuccess(result -> log.info("缓存删除完成, cacheName: {}, key: {}, success: {}", cacheName, key, result))
                .doOnError(e -> log.error("缓存删除失败, cacheName: {}, key: {}", cacheName, key, e));
    }

    /**
     * 删除单个键的两级缓存（L2 Redis → L1 Caffeine）
     *
     * <p>抽取自 {@link #evict(String, String)} 的核心逻辑，供单次删除与延迟双删复用。
     *
     * @param cacheName 缓存名称/前缀
     * @param key       缓存键
     * @return L2 是否删除了至少一个键
     */
    private Mono<Boolean> deleteBothLevels(String cacheName, String key) {
        String fullKey = buildFullKey(cacheName, key);
        return Mono.fromCallable(() -> {
                    // Step 1: 删除 L2（Redis）
                    RKeys rKeys = redissonClient.getKeys();
                    long deleted = rKeys.delete(fullKey);
                    log.debug("L2缓存删除成功, cacheName: {}, key: {}, deleted: {}", cacheName, key, deleted);
                    return deleted > 0;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .flatMap(l2Deleted -> {
                    // Step 2: 删除 L1（Caffeine）
                    return evictL1Cache(cacheName, key).thenReturn(l2Deleted);
                });
    }

    /**
     * 失效指定缓存键（异步延迟双删）。
     *
     * <p>针对并发读写导致缓存脏数据的经典场景（第八部分 §2.4 问题 1）：
     * 线程 A 读取 DB 旧值 → 线程 B 更新 DB 并删除缓存 → 线程 A 将旧值写回缓存，
     * 导致缓存脏数据。</p>
     *
     * <p>本方法在「立即删除」两级缓存之后，延迟 {@code delayMillis} 毫秒
     * <strong>异步</strong>再执行一次删除，以覆盖延迟写回的旧值。第二次删除采用
     * fire-and-forget 方式在 {@link BlockingIoSchedulers#cache()} 线程池上执行，
     * 不阻塞主业务响应，避免给更新接口引入额外延迟。</p>
     *
     * <p>每次删除失败时按配置重试，重试仍失败则记录日志交由补偿机制兜底
     * （对应 §2.4 问题 3）。当 {@code apex.cache.double-delete.enabled=false} 时
     * 退化为单次删除，行为与 {@link #evict(String, String)} 一致。</p>
     *
     * @param cacheName 缓存名称/前缀
     * @param key       缓存键
     * @return 首次删除是否成功（第二次删除在后台异步执行，不参与返回值）
     */
    @Override
    public Mono<Boolean> evictWithDelayedDoubleDelete(String cacheName, String key) {
        if (key == null || cacheName == null) {
            log.warn("缓存键或缓存名称为空，跳过延迟双删");
            return Mono.just(false);
        }

        CacheProperties.DoubleDelete config = cacheProperties.getDoubleDelete();
        if (config == null || !config.isEnabled()) {
            return evict(cacheName, key);
        }

        int retryTimes = Math.max(0, config.getRetryTimes());
        long delayMillis = Math.max(0, config.getDelayMillis());

        return deleteBothLevels(cacheName, key)
                .retry(retryTimes)
                .doOnSuccess(deleted -> {
                    scheduleSecondDelete(cacheName, key, delayMillis, retryTimes);
                    log.info("延迟双删：首次删除完成，已调度 {}ms 后异步二次删除, cacheName: {}, key: {}", delayMillis, cacheName, key);
                })
                .doOnError(e ->
                        log.error("缓存首次删除失败（重试 {} 次后），需补偿机制兜底, cacheName: {}, key: {}", retryTimes, cacheName, key, e));
    }

    /**
     * 调度第二次删除（异步，fire-and-forget）
     *
     * <p>延迟 {@code delayMillis} 毫秒后在缓存线程池上执行第二次删除，
     * 覆盖并发场景下延迟写回的旧值。异常仅记录日志，不向调用方传播。
     *
     * @param cacheName   缓存名称/前缀
     * @param key         缓存键
     * @param delayMillis 延迟毫秒数
     * @param retryTimes  重试次数
     */
    private void scheduleSecondDelete(String cacheName, String key, long delayMillis, int retryTimes) {
        Mono.delay(Duration.ofMillis(delayMillis), BlockingIoSchedulers.cache())
                .then(deleteBothLevels(cacheName, key).retry(retryTimes))
                .subscribe(
                        deleted ->
                                log.debug("延迟双删：二次删除完成, cacheName: {}, key: {}, deleted: {}", cacheName, key, deleted),
                        e -> log.error(
                                "延迟双删：二次删除失败（重试 {} 次后），需补偿机制兜底, cacheName: {}, key: {}",
                                retryTimes,
                                cacheName,
                                key,
                                e));
    }

    /**
     * 批量删除两级缓存中的多个键
     *
     * <p>批量操作优化：减少网络往返次数，提升性能。
     *
     * @param cacheName 缓存名称/前缀
     * @param keys      要删除的键列表
     * @return 成功删除的键数量
     */
    public Mono<Long> evictBatch(String cacheName, List<String> keys) {
        if (keys == null || keys.isEmpty() || cacheName == null) {
            log.warn("缓存键列表或缓存名称为空，跳过批量删除");
            return Mono.just(0L);
        }

        // 构建完整的缓存键
        String[] fullKeys = keys.stream()
                .filter(k -> k != null && !k.isBlank())
                .map(k -> buildFullKey(cacheName, k))
                .toArray(String[]::new);

        return Mono.fromCallable(() -> {
                    // Step 1: 批量删除 L2（Redis）
                    RKeys rKeys = redissonClient.getKeys();
                    long deleted = rKeys.delete(fullKeys);
                    log.debug("L2缓存批量删除成功, cacheName: {}, deleted: {}", cacheName, deleted);
                    return deleted;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .flatMap(l2Deleted -> {
                    // Step 2: 批量删除 L1（Caffeine）
                    keys.forEach(k -> evictL1Cache(cacheName, k).subscribe());
                    return Mono.just(l2Deleted);
                })
                .doOnSuccess(result -> log.info("缓存批量删除完成, cacheName: {}, deleted: {}", cacheName, result))
                .doOnError(e -> log.error("缓存批量删除失败, cacheName: {}", cacheName, e));
    }

    /**
     * 清空指定缓存名称下的所有缓存
     *
     * <p>注意：此操作会清空整个缓存名称空间下的所有数据，谨慎使用。
     *
     * @param cacheName 缓存名称/前缀
     * @return 清空成功返回true
     */
    public Mono<Boolean> clear(String cacheName) {
        if (cacheName == null) {
            log.warn("缓存名称为空，跳过清空");
            return Mono.just(false);
        }

        String pattern = cacheName + ":*";

        return Mono.fromCallable(() -> {
                    // Step 1: 清空 L2（Redis）中匹配模式的所有键
                    RKeys rKeys = redissonClient.getKeys();
                    Iterable<String> matchedKeys = rKeys.getKeysByPattern(pattern);
                    long deleted = 0;
                    for (String key : matchedKeys) {
                        rKeys.delete(key);
                        deleted++;
                    }
                    log.debug("L2缓存清空成功, cacheName: {}, deleted: {}", cacheName, deleted);
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .flatMap(success -> {
                    // Step 2: 清空 L1（Caffeine）
                    Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        cache.clear();
                        log.debug("L1缓存清空成功, cacheName: {}", cacheName);
                    }
                    return Mono.just(success);
                })
                .doOnSuccess(result -> log.info("缓存清空完成, cacheName: {}, success: {}", cacheName, result))
                .doOnError(e -> log.error("缓存清空失败, cacheName: {}", cacheName, e));
    }

    /**
     * 获取缓存值（两级缓存读取）
     *
     * <p>读取策略：先读L1（Caffeine），未命中则读L2（Redis）并回填L1。
     *
     * @param <T>       返回值类型
     * @param cacheName 缓存名称/前缀
     * @param key       缓存键
     * @param clazz     目标类型
     * @return 缓存值，不存在时返回空Mono
     */
    public <T> Mono<T> get(String cacheName, String key, Class<T> clazz) {
        if (key == null || cacheName == null) {
            return Mono.empty();
        }

        String fullKey = buildFullKey(cacheName, key);

        // Step 1: 先读 L1（Caffeine）
        return Mono.defer(() -> {
                    Cache cache = cacheManager.getCache(cacheName);
                    if (cache != null) {
                        T value = cache.get(key, clazz);
                        if (value != null) {
                            log.debug("L1缓存命中, cacheName: {}, key: {}", cacheName, key);
                            return Mono.just(value);
                        }
                    }
                    return Mono.empty();
                })
                // Step 2: L1未命中，读L2（Redis）
                .switchIfEmpty(Mono.fromCallable(() -> {
                            RBucket<String> bucket = redissonClient.getBucket(fullKey, StringCodec.INSTANCE);
                            String value = bucket.get();
                            if (value == null) {
                                log.debug("L2缓存未命中, cacheName: {}, key: {}", cacheName, key);
                                return null;
                            }
                            // 检测空值标记
                            if (EMPTY_VALUE.equals(value)) {
                                log.debug("L2缓存命中空值标记, cacheName: {}, key: {}", cacheName, key);
                                return null;
                            }
                            log.debug("L2缓存命中, cacheName: {}, key: {}", cacheName, key);
                            // 反序列化
                            if (clazz == String.class) {
                                return clazz.cast(value);
                            }
                            return objectMapper.readValue(value, clazz);
                        })
                        .subscribeOn(BlockingIoSchedulers.cache())
                        .flatMap(result -> {
                            if (result != null) {
                                // 回填 L1 缓存
                                return putL1Cache(cacheName, key, result, DEFAULT_L1_TTL)
                                        .thenReturn(result);
                            }
                            return Mono.empty();
                        }));
    }

    /**
     * 带数据回源的缓存读取
     *
     * <p>三级读取路径：L1 → L2 → DB（loader回调）。
     * 如果缓存未命中，执行loader从数据库加载数据，并自动回填到两级缓存。
     *
     * <p><b>缓存击穿防护（Single-Flight）</b>：对同一 cacheName+key 的并发回源请求
     * 做进程内合并，热点 key 过期瞬间只有一个请求真正访问数据库。</p>
     *
     * @param <T>       返回值类型
     * @param cacheName 缓存名称/前缀
     * @param key       缓存键
     * @param clazz     目标类型
     * @param loader    数据回源加载器
     * @return 缓存值或数据库数据
     */
    public <T> Mono<T> getWithLoader(String cacheName, String key, Class<T> clazz, Mono<T> loader) {
        return get(cacheName, key, clazz)
                .switchIfEmpty(Mono.defer(() -> inFlight.join(
                        cacheName + ":" + key,
                        () -> loader.flatMap(dbValue -> {
                            if (dbValue == null) {
                                // 写入空值标记防止缓存穿透
                                return setEmptyMarker(cacheName, key).then(Mono.empty());
                            }
                            // 回填两级缓存，并向下游返回加载到的值
                            return put(cacheName, key, dbValue).thenReturn(dbValue);
                        }))));
    }

    /**
     * 写入空值标记（缓存穿透防护）
     *
     * <p>当数据库中不存在对应数据时，写入一个短TTL的空值标记，
     * 避免短时间内大量请求穿透到数据库。
     *
     * @param cacheName 缓存名称/前缀
     * @param key       缓存键
     * @return 写入完成信号
     */
    private Mono<Void> setEmptyMarker(String cacheName, String key) {
        String fullKey = buildFullKey(cacheName, key);
        Duration jitteredEmptyTtl = jitter(EMPTY_TTL, JITTER_FACTOR);
        return Mono.fromCallable(() -> {
                    RBucket<String> bucket = redissonClient.getBucket(fullKey, StringCodec.INSTANCE);
                    bucket.set(EMPTY_VALUE, jitteredEmptyTtl);
                    log.debug("写入空值标记, cacheName: {}, key: {}", cacheName, key);
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .then();
    }

    /**
     * 写入L1（Caffeine）缓存
     *
     * @param <T>       缓存值类型
     * @param cacheName 缓存名称
     * @param key       缓存键
     * @param value     缓存值
     * @param ttl       过期时间
     * @return 写入完成信号
     */
    private <T> Mono<Void> putL1Cache(String cacheName, String key, T value, Duration ttl) {
        return Mono.defer(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.put(key, value);
                log.debug("L1缓存写入成功, cacheName: {}, key: {}", cacheName, key);
            }
            return Mono.empty();
        });
    }

    /**
     * 失效L1（Caffeine）缓存
     *
     * @param cacheName 缓存名称
     * @param key       缓存键
     * @return 失效完成信号
     */
    private Mono<Void> evictL1Cache(String cacheName, String key) {
        return Mono.defer(() -> {
            Cache cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
                log.debug("L1缓存失效成功, cacheName: {}, key: {}", cacheName, key);
            }
            return Mono.empty();
        });
    }

    /**
     * 构建完整的缓存键
     *
     * @param cacheName 缓存名称/前缀
     * @param key       缓存键
     * @return 完整的缓存键（格式：cacheName:key）
     */
    private String buildFullKey(String cacheName, String key) {
        return cacheName + ":" + key;
    }

    /**
     * 对 TTL 添加随机抖动，防止缓存雪崩
     *
     * <p>在原始 TTL 的基础上增加 ±10% 的随机浮动，使不同 key 的过期时间分散在不同时刻，
     * 避免大量缓存同时过期导致的缓存雪崩问题。
     *
     * <h3>算法</h3>
     * <pre>{@code * jittered = original * (1 + jitterFactor * (random - 0.5) * 2)
     * // 即 original * [0.9, 1.1] 范围内的随机值（jitterFactor=0.1 时）
     * }</pre>
     *
     * @param duration     原始 TTL
     * @param jitterFactor 抖动因子（如 0.1 表示 ±10%）
     * @return 抖动后的 TTL
     */
    private Duration jitter(Duration duration, double jitterFactor) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            return duration;
        }
        double factor = 1.0 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 2 * jitterFactor;
        long jitteredMillis = (long) (duration.toMillis() * factor);
        return Duration.ofMillis(Math.max(1, jitteredMillis));
    }

    /**
     * 将任意类型的值转换为适合 Redis 存储的字符串。
     *
     * <p>基本类型（String、Number、Boolean）通过 {@link String#valueOf} 转换；
     * 复杂对象通过 Jackson 序列化为 JSON 字符串。</p>
     *
     * @param value 待转换的值
     * @param <T>   值类型
     * @return 字符串表示，用于存入 Redis
     */
    private <T> String toStoreValue(T value) {
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize value for cache", e);
        }
    }
}
