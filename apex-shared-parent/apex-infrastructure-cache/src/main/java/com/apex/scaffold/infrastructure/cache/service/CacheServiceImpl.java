package com.apex.scaffold.infrastructure.cache.service;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.service.CacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 缓存服务实现类
 *
 * <p>基于 Redisson 实现的缓存服务，提供分布式缓存能力，支持响应式编程模型。
 * 底层 Redisson 操作为同步阻塞 API，通过 {@code subscribeOn(BlockingIoSchedulers.cache())}
 * 将阻塞操作隔离到弹性线程池，避免阻塞 WebFlux 事件循环线程。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>服务实现模式</strong>：实现 {@link CacheService} 接口，提供统一的缓存操作抽象</li>
 *   <li><strong>适配器模式</strong>：将 Redisson 客户端操作适配为响应式 {@link Mono} 返回类型</li>
 *   <li><strong>序列化策略模式</strong>：根据值类型自动选择直接存储或 JSON 序列化存储</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see CacheService
 * @see RedissonClient
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheServiceImpl implements CacheService {

    private final RedissonClient redissonClient;

    private final ObjectMapper objectMapper;

    /**
     * 从缓存中获取单个值
     *
     * <p>根据键从Redis中获取缓存值，支持直接类型匹配和JSON反序列化两种方式。
     *
     * @param <T>   返回值类型
     * @param key   缓存键
     * @param clazz 目标类型
     * @return 缓存值，如果键为null或值不存在则返回空Mono
     */
    @Override
    public <T> Mono<T> get(String key, Class<T> clazz) {
        if (key == null) {
            return Mono.empty();
        }
        return Mono.fromCallable(() -> {
                    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                    String value = bucket.get();
                    if (value == null) {
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
     * 向缓存中设置值
     *
     * <p>将值存入Redis缓存，基本类型直接存储，复杂对象使用JSON序列化后存储。
     *
     * @param <T>   缓存值的类型
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   过期时间
     * @return 设置成功返回true，键为null时返回false
     */
    @Override
    public <T> Mono<Boolean> set(String key, T value, Duration ttl) {
        if (key == null) {
            return Mono.just(false);
        }
        return Mono.fromCallable(() -> {
                    RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                    bucket.set(toStoreValue(value), ttl);
                    return true;
                })
                .subscribeOn(BlockingIoSchedulers.cache());
    }

    /**
     * 删除一个或多个缓存键
     *
     * <p>批量删除指定的缓存键，返回实际删除的键数量。
     *
     * @param keys 要删除的缓存键数组
     * @return 成功删除的键数量，键为空时返回0
     */
    @Override
    public Mono<Long> delete(String... keys) {
        return Mono.fromCallable(() -> {
                    if (keys == null || keys.length == 0) {
                        return 0L;
                    }
                    RKeys rKeys = redissonClient.getKeys();
                    return rKeys.delete(keys);
                })
                .subscribeOn(BlockingIoSchedulers.cache());
    }

    /**
     * 批量获取缓存值
     *
     * <p>根据键列表批量从Redis获取缓存值，自动过滤null键和null值，支持JSON反序列化。
     *
     * @param <T>   返回值类型
     * @param keys  缓存键列表
     * @param clazz 目标类型
     * @return 非空的缓存值列表，键列表为空时返回空列表
     */
    @Override
    public <T> Mono<List<T>> getBatch(List<String> keys, Class<T> clazz) {
        if (keys == null || keys.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }
        return Mono.fromCallable(() -> keys.stream()
                        .filter(key -> key != null)
                        .map(key -> {
                            try {
                                RBucket<String> bucket = redissonClient.getBucket(key, StringCodec.INSTANCE);
                                String value = bucket.get();
                                if (value == null) {
                                    return null;
                                }
                                if (clazz == String.class) {
                                    return clazz.cast(value);
                                }
                                return objectMapper.readValue(value, clazz);
                            } catch (Exception e) {
                                log.warn("Failed to get cache for key: {}", key, e);
                                return null;
                            }
                        })
                        .filter(value -> value != null)
                        .toList())
                .subscribeOn(BlockingIoSchedulers.cache());
    }

    /**
     * 批量设置缓存值
     *
     * <p>将键值对映射批量存入Redis缓存，基本类型直接存储，复杂对象使用JSON序列化后存储。
     *
     * @param <T>          缓存值的类型
     * @param keyValueMap  键值对映射
     * @param ttl          过期时间
     * @return 所有键值对均设置成功返回true，任一操作失败或键值对映射为空时返回false
     */
    @Override
    public <T> Mono<Boolean> setBatch(Map<String, T> keyValueMap, Duration ttl) {
        if (keyValueMap == null || keyValueMap.isEmpty()) {
            return Mono.just(false);
        }
        return Mono.fromCallable(() -> {
                    for (Map.Entry<String, T> entry : keyValueMap.entrySet()) {
                        try {
                            RBucket<String> bucket = redissonClient.getBucket(entry.getKey(), StringCodec.INSTANCE);
                            bucket.set(toStoreValue(entry.getValue()), ttl);
                        } catch (Exception e) {
                            log.warn("Failed to set cache for key: {}", entry.getKey(), e);
                            return false;
                        }
                    }
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
}
