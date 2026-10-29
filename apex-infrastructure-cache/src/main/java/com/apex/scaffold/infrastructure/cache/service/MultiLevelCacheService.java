package com.apex.scaffold.infrastructure.cache.service;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.ratelimit.algorithm.TokenBucket;
import com.apex.scaffold.domain.service.CacheService;
import com.apex.scaffold.infrastructure.cache.config.CacheProperties;
import com.apex.scaffold.infrastructure.cache.support.InFlightRequestMerge;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 多级缓存服务（L1 Caffeine + L2 Redis）
 *
 * <p>实现两级缓存架构，提供高性能的缓存访问能力：
 * <ul>
 *   <li><strong>L1（一级缓存）</strong>：基于 Caffeine 的进程内本地缓存，访问延迟为微秒级。
 *       适用于存储高频热点数据，各服务实例独立维护</li>
 *   <li><strong>L2（二级缓存）</strong>：基于 Redis/Redisson 的分布式缓存，数据跨实例共享。
 *       适用于存储需要一致性的共享数据，或 L1 未命中的兜底</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <pre>{@code * get(key) → L1(Caffeine) 命中? → 是 → 返回
 *                                → 否 → L2(Redis) 命中? → 是 → 回填 L1 → 返回
 *                                                       → 否 → 返回空 (调用方回源 DB)
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>写入</strong>：Write-Through 模式，同步写入 L2（Redis），同时失效 L1</li>
 *   <li><strong>删除</strong>：同步删除 L2，同时失效 L1</li>
 *   <li><strong>过期</strong>：L1 使用较短 TTL（默认 5 分钟），L2 使用较长 TTL（默认 1 小时）</li>
 *   <li><strong>淘汰</strong>：L1 基于 W-TinyLFU 算法自动淘汰低频条目</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <p>当数据库不可用且缓存未命中时，启用读降级策略可从过期缓存中读取旧数据作为兜底，
 * 避免服务完全不可用。通过 {@code apex.cache.read-degradation.enabled=true} 启用。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>L1 缓存是进程级的，不同实例间缓存数据独立，存在短暂的不一致窗口</li>
 *   <li>写操作始终优先更新 L2，确保分布式一致性，再失效 L1</li>
 *   <li>L1 适用于读多写少的热点数据场景</li>
 *   <li>读降级返回的是过期数据，仅适用于可用性优先、一致性要求不严格的场景</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 * @see LocalCacheService
 * @see CacheService
 * @see RedissonClient
 */
@Slf4j
@Service
public class MultiLevelCacheService {

    /** 空值标记：用于缓存穿透防护 */
    private static final String EMPTY_VALUE = "__EMPTY__";

    /** 空值标记 TTL：5 分钟 */
    private static final Duration EMPTY_TTL = Duration.ofMinutes(5);

    /** 回源请求合并器：缓存击穿防护（Single-Flight），同一 key 并发回源只执行一次 */
    private final InFlightRequestMerge inFlight = new InFlightRequestMerge();

    private final TokenBucket rebuildBucket = new TokenBucket(100, 20.0);

    private final LocalCacheService localCacheService;

    private final RedissonClient redissonClient;

    private final ObjectMapper objectMapper;

    private final CacheProperties cacheProperties;

    /** 读降级计数器：记录 DB 故障时返回过期缓存的次数 */
    private final Counter readDegradationCounter;

    /**
     * 构造多级缓存服务
     *
     * @param localCacheService 本地缓存服务（L1）
     * @param redissonClient    Redisson 客户端（L2）
     * @param objectMapper      JSON 序列化器
     * @param cacheProperties   缓存配置属性
     * @param meterRegistry     指标注册表
     */
    public MultiLevelCacheService(
            LocalCacheService localCacheService,
            RedissonClient redissonClient,
            ObjectMapper objectMapper,
            CacheProperties cacheProperties,
            MeterRegistry meterRegistry) {
        this.localCacheService = localCacheService;
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
        this.readDegradationCounter = Counter.builder("apex_cache_read_degradation_total")
                .description("DB 故障读降级次数")
                .register(meterRegistry);
    }

    /**
     * 从多级缓存中获取单个值
     *
     * <p>访问路径：L1（Caffeine）→ L2（Redis）→ 返回空。
     * 如果 L1 未命中但 L2 命中，自动将结果回填到 L1。
     * 如果 L2 存储的是空值标记，直接返回空以穿透缓存。
     *
     * @param <T>   返回值类型
     * @param key   缓存键
     * @param clazz 目标类型 Class 对象
     * @return 缓存值，不存在时返回空 Mono
     */
    public <T> Mono<T> get(String key, Class<T> clazz) {
        if (key == null) {
            return Mono.empty();
        }
        return lookup(key, clazz).flatMap(value -> Mono.justOrEmpty(value));
    }

    /** 区分未命中（空信号）与负缓存命中（空 Optional），防止重复回源。 */
    private <T> Mono<Optional<T>> lookup(String key, Class<T> clazz) {
        // Step 1: 查询 L1 本地缓存
        return localCacheService
                .get(key, clazz)
                .flatMap(value -> {
                    if (value != null) {
                        log.debug("L1 缓存命中, key: {}", key);
                        return Mono.just(Optional.of(value));
                    }
                    return Mono.empty();
                })
                // Step 2: L1 未命中，查询 L2 Redis 缓存
                .switchIfEmpty(Mono.defer(() -> queryL2AndBackfillL1(key, clazz)));
    }

    /**
     * 查询 L2（Redis）缓存并将结果回填到 L1（Caffeine）
     *
     * <p>如果 L2 命中，将结果异步写入 L1 以加速后续访问。
     * 如果 L2 存储的是空值标记（缓存穿透防护），直接返回空。
     *
     * @param <T>   返回值类型
     * @param key   缓存键
     * @param clazz 目标类型 Class 对象
     * @return 缓存值，不存在时返回空 Mono
     */
    private <T> Mono<Optional<T>> queryL2AndBackfillL1(String key, Class<T> clazz) {
        return Mono.fromCallable(() -> {
                    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                    String value = bucket.get();
                    if (value == null) {
                        log.debug("L2 缓存未命中, key: {}", key);
                        return null;
                    }
                    // 检测空值标记（缓存穿透防护）
                    if (EMPTY_VALUE.equals(value)) {
                        log.debug("L2 缓存命中空值标记, key: {}", key);
                        return Optional.<T>empty();
                    }
                    log.debug("L2 缓存命中, key: {}", key);
                    T result = clazz == String.class ? clazz.cast(value) : objectMapper.readValue(value, clazz);
                    return Optional.of(result);
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .flatMap(result -> {
                    if (result.isPresent()) {
                        return localCacheService
                                .set(key, result.get(), Duration.ofMinutes(5))
                                .thenReturn(result);
                    }
                    return Mono.just(result);
                });
    }

    /**
     * 向多级缓存中写入值
     *
     * <p>采用 Write-Through 策略：同步写入 L2（Redis），同时失效 L1（Caffeine）
     * 以确保分布式一致性。L1 将在下次读取时从 L2 重新加载。
     *
     * @param <T>   缓存值的类型
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   过期时间
     * @return 写入成功返回 true，键为 null 时返回 false
     */
    public <T> Mono<Boolean> set(String key, T value, Duration ttl) {
        if (key == null) {
            return Mono.just(false);
        }
        // 同步写入 L2（Redis），同时失效 L1
        return Mono.fromCallable(() -> {
                    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                    bucket.set(toStoreValue(value), ttl);
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .flatMap(success -> {
                    if (success) {
                        // 失效 L1 缓存（Write-Through 策略）
                        return localCacheService.delete(key).thenReturn(true);
                    }
                    return Mono.just(false);
                });
    }

    /**
     * 从多级缓存中删除一个或多个键
     *
     * <p>同步删除 L2（Redis）和失效 L1（Caffeine）。
     *
     * @param keys 要删除的缓存键数组
     * @return 成功删除的键数量
     */
    public Mono<Long> delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return Mono.just(0L);
        }
        // 同步删除 L2（Redis），同时失效 L1
        return Mono.fromCallable(() -> {
                    RKeys rKeys = redissonClient.getKeys();
                    return rKeys.delete(keys);
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .flatMap(deletedCount -> {
                    // 失效 L1 本地缓存
                    return localCacheService.delete(keys).thenReturn(deletedCount);
                });
    }

    /**
     * 带数据回源的多级缓存读取
     *
     * <p>提供完整的三级读取路径：L1（Caffeine）→ L2（Redis）→ DB（回调）。
     * 如果所有缓存层均未命中，则执行 {@code loader} 回调从数据库加载数据，
     * 并将结果自动回填到 L2 和 L1。
     *
     * <p><b>缓存击穿防护（Single-Flight）</b>：对同一 key 的并发回源请求做进程内合并，
     * 热点 key 过期瞬间只有一个请求真正访问数据库，其余并发请求共享同一次回源结果。</p>
     *
     * <p><b>DB 故障读降级（v1.3.0）</b>：当启用读降级开关（{@code apex.cache.read-degradation.enabled=true}）
     * 且数据库查询失败（抛出 {@link DataAccessException}）时，会尝试从过期的 Redis 缓存中读取旧数据返回，
     * 并记录降级指标 {@code apex_cache_read_degradation_total}。适用于可用性优先的场景。</p>
     *
     * @param <T>   返回值类型
     * @param key   缓存键
     * @param clazz 目标类型 Class 对象
     * @param loader 数据回源加载器（数据库查询回调）
     * @param ttl    写入 L2 缓存的过期时间
     * @return 缓存值或回源的数据库数据
     */
    public <T> Mono<T> getWithLoader(String key, Class<T> clazz, Supplier<Mono<T>> loader, Duration ttl) {
        if (key == null) {
            return Mono.empty();
        }
        // 三级读取：L1 → L2 → DB(loader)，回源段做 Single-Flight 合并防击穿
        return lookup(key, clazz)
                .switchIfEmpty(Mono.defer(() ->
                        // 缓存未命中，从 DB 回源（同 key 并发请求合并为一次）
                        inFlight.join(key, () -> {
                            if (!rebuildBucket.tryAcquire()) {
                                return Mono.error(new IllegalStateException("缓存回源速率受限"));
                            }
                            return Mono.defer(loader)
                                    .flatMap(dbValue -> set(key, dbValue, ttl).thenReturn(Optional.of(dbValue)))
                                    .switchIfEmpty(
                                            Mono.defer(() -> setEmptyMarker(key).thenReturn(Optional.<T>empty())))
                                    .onErrorResume(this::isDataAccessException, ex -> {
                                        // DB 故障读降级：尝试从过期缓存中读取旧数据
                                        if (cacheProperties.getReadDegradation().isEnabled()) {
                                            log.warn("DB 查询失败，尝试读降级从过期缓存返回, key: {}", key, ex);
                                            return tryReadStaleCache(key, clazz).map(Optional::of);
                                        }
                                        // 降级未启用，直接传播异常
                                        return Mono.error(ex);
                                    });
                        })))
                .flatMap(value -> Mono.justOrEmpty(value));
    }

    /**
     * 判断异常是否为数据访问异常（DB 故障）
     *
     * @param ex 异常对象
     * @return 如果是 DataAccessException 或其子类返回 true
     */
    private boolean isDataAccessException(Throwable ex) {
        return ex instanceof DataAccessException;
    }

    /**
     * 从过期缓存中读取旧数据（读降级兜底）
     *
     * <p>当 DB 不可用时，尝试从 Redis 中读取已过期但仍存在的缓存数据作为降级返回。
     * 只有在配置的最大过期时间窗口（{@code apex.cache.read-degradation.max-stale-seconds}）
     * 内的数据才会被返回。成功降级时会增加降级计数指标。
     *
     * @param <T>   返回值类型
     * @param key   缓存键
     * @param clazz 目标类型 Class 对象
     * @return 过期缓存中的旧数据，不存在时返回空 Mono
     */
    private <T> Mono<T> tryReadStaleCache(String key, Class<T> clazz) {
        return Mono.fromCallable(() -> {
                    // 尝试从 Redis 读取（即使已过期，只要键还在就能读到）
                    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                    String value = bucket.get();
                    if (value == null || EMPTY_VALUE.equals(value)) {
                        log.warn("读降级失败：过期缓存不存在, key: {}", key);
                        return null;
                    }
                    log.info("读降级成功：从过期缓存返回旧数据, key: ", key);
                    readDegradationCounter.increment();
                    if (clazz == String.class) {
                        return clazz.cast(value);
                    }
                    return objectMapper.readValue(value, clazz);
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .onErrorResume(ex -> {
                    log.error("读降级异常, key: {}", key, ex);
                    return Mono.empty();
                });
    }

    /**
     * 写入空值标记（缓存穿透防护）
     *
     * <p>当数据库中不存在对应数据时，在缓存中写入一个短 TTL 的空值标记，
     * 避免短时间内大量请求穿透到数据库。
     *
     * @param key 缓存键
     */
    private Mono<Void> setEmptyMarker(String key) {
        return Mono.fromCallable(() -> {
                    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                    bucket.set(EMPTY_VALUE, EMPTY_TTL);
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .then();
    }

    /**
     * 批量从多级缓存中获取值
     *
     * <p>先查询 L1 本地缓存，未命中的键再查询 L2 Redis 缓存，
     * 并将结果回填到 L1。
     *
     * @param <T>   返回值类型
     * @param keys  缓存键列表
     * @param clazz 目标类型 Class 对象
     * @return 非空的缓存值列表
     */
    public <T> Mono<List<T>> getBatch(List<String> keys, Class<T> clazz) {
        if (keys == null || keys.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        List<String> validKeys = keys.stream().filter(Objects::nonNull).toList();
        return Flux.fromIterable(validKeys).concatMap(key -> get(key, clazz)).collectList();
    }

    /**
     * 批量向多级缓存中写入值
     *
     * <p>同步写入 L2（Redis），同时失效 L1（Caffeine）中对应的条目。
     *
     * @param <T>         缓存值的类型
     * @param keyValueMap 键值对映射
     * @param ttl         过期时间
     * @return 写入完成返回 true，键值对映射为空时返回 false
     */
    public <T> Mono<Boolean> setBatch(Map<String, T> keyValueMap, Duration ttl) {
        if (keyValueMap == null || keyValueMap.isEmpty()) {
            return Mono.just(false);
        }
        return Mono.fromCallable(() -> {
                    keyValueMap.forEach((key, value) -> {
                        try {
                            RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                            bucket.set(toStoreValue(value), ttl);
                        } catch (Exception e) {
                            log.warn("批量写入 L2 缓存失败, key: {}", key, e);
                        }
                    });
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.cache())
                .flatMap(success -> {
                    if (success) {
                        return Flux.fromIterable(keyValueMap.keySet())
                                .flatMap(localCacheService::delete)
                                .then(Mono.just(true));
                    }
                    return Mono.just(false);
                });
    }

    /**
     * 清空本地缓存（L1）
     *
     * <p>仅清空当前实例的 Caffeine 本地缓存，不清除 Redis（L2）中的数据。
     * 通常用于缓存刷新或数据变更后的本地缓存重置。
     */
    public Mono<Void> clearLocal() {
        return localCacheService.clear();
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
