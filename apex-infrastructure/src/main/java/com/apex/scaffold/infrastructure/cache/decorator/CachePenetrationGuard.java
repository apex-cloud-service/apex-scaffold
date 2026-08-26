package com.apex.scaffold.infrastructure.cache.decorator;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import reactor.core.publisher.Mono;

/**
 * 缓存穿透防护装饰器
 *
 * <p>实现缓存穿透防护功能，通过空值标记防止不存在的 key 反复穿透到数据库。
 * 空值标记的 TTL 添加了随机抖动（jitter），防止批量缓存同时过期导致的瞬时穿透。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li><b>空值标记</b>：对不存在的缓存值写入特殊标记，防止缓存穿透</li>
 *   <li><b>TTL 抖动</b>：空标记 TTL 增加 ±20% 随机偏移，防止批量过期雪崩</li>
 *   <li><b>自动过期</b>：空标记值自动在指定时间后过期，允许再次查询数据库</li>
 *   <li><b>批量防护</b>：批量操作同样支持空值过滤</li>
 * </ul>
 *
 * <h2>线程模型</h2>
 * <p>Redisson 的同步阻塞 API 通过 {@code subscribeOn(BlockingIoSchedulers.cache())}
 *    隔离到弹性线程池，避免阻塞 Reactor 的事件循环线程。</p>
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.0.0
 * @see CacheOperation
 * @see RedissonClient
 */
@RequiredArgsConstructor
public class CachePenetrationGuard implements CacheOperation {

    /**
     * 空值标记
     *
     * <p>用于标记缓存中不存在的值，防止缓存穿透攻击
     */
    private static final String EMPTY_VALUE = "__EMPTY__";

    /**
     * 空值标记基础过期时间
     *
     * <p>空标记值的基础存活时间，实际写入时会在基础值上叠加 ±20% 随机抖动。
     */
    private static final Duration EMPTY_VALUE_TTL = Duration.ofMinutes(5);

    /**
     * TTL 抖动因子（百分比），默认 0.2 表示 ±20%
     */
    private static final double JITTER_FACTOR = 0.2;

    /**
     * Redisson 客户端
     */
    private final RedissonClient redissonClient;

    /**
     * JSON 序列化/反序列化
     */
    private final ObjectMapper objectMapper;

    /**
     * 获取单个缓存值，带有缓存穿透防护
     *
     * <p>从Redis中获取指定键的值，如果值为空值标记则返回null，防止缓存穿透。
     * 底层Redisson操作为同步阻塞API，通过{@code subscribeOn(BlockingIoSchedulers.cache())}
     * 隔离到弹性线程池。
     *
     * @param <T> 返回值类型
     * @param key 缓存键
     * @param clazz 目标类型
     * @return 缓存值，如果不存在或为空标记则返回null
     */
    @Override
    public <T> Mono<T> get(String key, Class<T> clazz) {
        return Mono.fromCallable(() -> {
                    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                    String value = bucket.get();

                    if (value == null) {
                        return null;
                    }

                    if (isEmptyValue(value)) {
                        return null;
                    }

                    if (clazz == String.class) {
                        return clazz.cast(value);
                    }
                    return objectMapper.readValue(value, clazz);
                })
                .subscribeOn(BlockingIoSchedulers.cache());
    }

    /**
     * 设置单个缓存值（带 TTL 抖动）
     *
     * <p>向Redis中写入指定键的值，并设置过期时间。
     * TTL 会被增加 ±20% 随机抖动以防止缓存雪崩。
     * 底层Redisson操作为同步阻塞API，通过{@code subscribeOn(BlockingIoSchedulers.cache())}
     * 隔离到弹性线程池。
     *
     * @param <T>   值类型
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   过期时间（会叠加 ±20% 随机抖动）
     * @return 操作成功返回true
     */
    @Override
    public <T> Mono<Boolean> set(String key, T value, Duration ttl) {
        Duration jitteredTtl = applyJitter(ttl);
        return Mono.fromCallable(() -> {
                    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                    bucket.set(toStoreValue(value), jitteredTtl);
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.cache());
    }

    /**
     * 删除一个或多个缓存键
     *
     * <p>逐个删除指定的缓存键，返回成功删除的数量。
     * 底层Redisson操作为同步阻塞API，通过{@code subscribeOn(BlockingIoSchedulers.cache())}
     * 隔离到弹性线程池。
     *
     * @param keys 要删除的缓存键数组
     * @return 成功删除的键数量
     */
    @Override
    /**
     * 删除
     *
     * @param keys 参数
     *
     * @return 结果
     */
    public Mono<Long> delete(String... keys) {
        return Mono.fromCallable(() -> {
                    long count = 0;
                    for (String key : keys) {
                        if (redissonClient.getBucket(key, StringCodec.INSTANCE).delete()) {
                            count++;
                        }
                    }
                    return count;
                })
                .subscribeOn(BlockingIoSchedulers.cache());
    }

    /**
     * 批量获取缓存值，带有缓存穿透防护
     *
     * <p>批量从Redis获取多个键的值，自动过滤空值标记和异常值，防止缓存穿透。
     * 底层Redisson操作为同步阻塞API，通过{@code subscribeOn(BlockingIoSchedulers.cache())}
     * 隔离到弹性线程池。
     *
     * @param <T>  返回值类型
     * @param keys 缓存键列表
     * @param clazz 目标类型
     * @return 非空的缓存值列表
     */
    @Override
    public <T> Mono<List<T>> getBatch(List<String> keys, Class<T> clazz) {
        return Mono.fromCallable(() -> keys.stream()
                        .map(key -> {
                            try {
                                RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                                String value = bucket.get();
                                if (value == null || isEmptyValue(value)) {
                                    return null;
                                }
                                if (clazz == String.class) {
                                    return clazz.cast(value);
                                }
                                return objectMapper.readValue(value, clazz);
                            } catch (Exception e) {
                                return null;
                            }
                        })
                        .filter(v -> v != null)
                        .toList())
                .subscribeOn(BlockingIoSchedulers.cache());
    }

    /**
     * 批量设置缓存值（带 TTL 抖动）
     *
     * <p>向Redis中批量写入多个键值对，并统一设置过期时间。
     * TTL 会被增加 ±20% 随机抖动以防止缓存雪崩。
     * 底层Redisson操作为同步阻塞API，通过{@code subscribeOn(BlockingIoSchedulers.cache())}
     * 隔离到弹性线程池。
     *
     * @param <T>         值类型
     * @param keyValueMap 键值对映射
     * @param ttl         过期时间（会叠加 ±20% 随机抖动）
     * @return 操作成功返回true
     */
    @Override
    public <T> Mono<Boolean> setBatch(Map<String, T> keyValueMap, Duration ttl) {
        Duration jitteredTtl = applyJitter(ttl);
        return Mono.fromCallable(() -> {
                    keyValueMap.forEach((key, value) -> {
                        RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                        bucket.set(toStoreValue(value), jitteredTtl);
                    });
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.cache());
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

    /**
     * 判断缓存值是否为空值标记
     *
     * <p>用于检查从缓存中获取的值是否为空值标记（__EMPTY__），
     * 从而判断该键对应的缓存是否实际存在
     *
     * @param value 缓存值
     * @return true-为空值标记，false-非空值标记
     */
    private boolean isEmptyValue(String value) {
        return EMPTY_VALUE.equals(value);
    }

    /**
     * 对 TTL 添加随机抖动，防止缓存雪崩
     *
     * <p>在实际 TTL 的基础上增加 ±20% 的随机偏移量，使缓存过期时间分散开，
     * 避免大量缓存同时过期导致的缓存雪崩。
     *
     * <h3>示例</h3>
     * <pre>{@code * 输入 TTL: 5分钟
     * jitter: ±20%
     * 输出范围: 4分钟 ~ 6分钟
     * }</pre>
     *
     * @param ttl 基础 TTL
     * @return 叠加随机抖动后的 TTL
     */
    private Duration applyJitter(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return ttl;
        }
        long baseMs = ttl.toMillis();
        long jitterRange = (long) (baseMs * JITTER_FACTOR);
        long randomOffset = ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);
        return Duration.ofMillis(Math.max(1, baseMs + randomOffset));
    }
}
