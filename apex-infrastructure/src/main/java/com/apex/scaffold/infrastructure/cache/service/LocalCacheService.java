package com.apex.scaffold.infrastructure.cache.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 本地缓存服务实现（Caffeine 一级缓存）
 *
 * <p>基于 Caffeine 实现的进程内本地缓存，提供微秒级访问速度。
 * 适用于存储高频访问的热点数据，配合 {@link MultiLevelCacheService 多级缓存服务}
 * 作为 L1 缓存层使用。
 *
 * <h2>设计要点</h2>
 * <ul>
 *   <li><strong>进程级缓存</strong>：每个服务实例独立维护，数据不跨实例共享</li>
 *   <li><strong>W-TinyLFU 淘汰算法</strong>：Caffeine 核心算法，统计频率 + 衰减窗口，
 *       能够识别并保留真正高频访问的"热点"数据</li>
 *   <li><strong>响应式适配</strong>：所有操作返回 {@link Mono}，与 WebFlux 响应式架构对齐</li>
 *   <li><strong>JSON 序列化</strong>：复杂对象使用 Jackson 序列化存储，确保类型安全</li>
 * </ul>
 *
 * <h2>适用场景</h2>
 * <ul>
 *   <li>用户 Session / Token 缓存</li>
 *   <li>系统配置 / 字典数据（几乎不更新的读密集型数据）</li>
 *   <li>热点业务数据（短时间内被大量请求访问的数据）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MultiLevelCacheService
 * @see CaffeineCacheManager
 */
@Service
public class LocalCacheService {
    private static final Logger log = LoggerFactory.getLogger(LocalCacheService.class);

    /** 默认缓存名称 */
    private static final String DEFAULT_CACHE_NAME = "localCache";

    /**
     * 缓存条目过期时间跟踪表
     *
     * <p>由于 CaffeineCacheManager 不支持按 key 设置独立的 TTL，
     * 此映射用于跟踪每个 key 的过期时间，在读取时进行过期校验。
     */
    private final ConcurrentHashMap<String, Long> ttlTracker = new ConcurrentHashMap<>();

    private final CaffeineCacheManager cacheManager;

    private final ObjectMapper objectMapper;

    /**
     * 构造本地缓存服务
     *
     * @param cacheManager Caffeine 缓存管理器
     * @param objectMapper JSON 序列化器
     */
    public LocalCacheService(CaffeineCacheManager cacheManager, ObjectMapper objectMapper) {
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 转换缓存原始值为目标类型：优先类型安全的强制转换，回退到 JSON 反序列化。
     *
     * @param value 缓存的原始值
     * @param clazz 目标类型 Class 对象
     * @param <T>   目标类型泛型
     * @return 转换后的值，转换失败返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    private <T> T deserializeValue(Object value, Class<T> clazz) {
        if (clazz.isInstance(value)) {
            return clazz.cast(value);
        }
        if (value instanceof String str) {
            try {
                return objectMapper.readValue(str, clazz);
            } catch (Exception e) {
                log.warn("JSON 反序列化缓存值失败, type: {}", clazz.getName(), e);
            }
        }
        return null;
    }

    /**
     * 从本地缓存中获取单个值
     *
     * <p>优先尝试直接类型转换，失败时使用 JSON 反序列化。
     * 如果键不存在或值为 {@code null}，返回 {@link Mono#empty()}。
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
        try {
            // 检查是否已过期
            Long expireTime = ttlTracker.get(key);
            if (expireTime != null && System.currentTimeMillis() > expireTime) {
                // 惰性删除过期条目
                ttlTracker.remove(key);
                org.springframework.cache.Cache cache = cacheManager.getCache(DEFAULT_CACHE_NAME);
                if (cache != null) {
                    cache.evict(key);
                }
                return Mono.empty();
            }
            org.springframework.cache.Cache cache = cacheManager.getCache(DEFAULT_CACHE_NAME);
            if (cache == null) {
                return Mono.empty();
            }
            org.springframework.cache.Cache.ValueWrapper wrapper = cache.get(key);
            if (wrapper == null) {
                return Mono.empty();
            }
            Object value = wrapper.get();
            if (value == null) {
                return Mono.empty();
            }
            return Mono.justOrEmpty(deserializeValue(value, clazz));
        } catch (Exception e) {
            log.warn("获取本地缓存失败, key: {}", key, e);
            return Mono.empty();
        }
    }

    /**
     * 向本地缓存中设置值
     *
     * <p>基本类型（String、Number、Boolean）直接存储，
     * 复杂类型自动 JSON 序列化后存储。
     * 通过独立的 TTL 跟踪表（{@link #ttlTracker}）记录每个 key 的过期时间，
     * 在读取时进行惰性过期校验，弥补 CaffeineCacheManager 不支持按 key 设置
     * 独立 TTL 的限制。
     *
     * @param <T>   缓存值的类型
     * @param key   缓存键
     * @param value 缓存值
     * @param ttl   过期时间，读取时会检查是否超过此时间
     * @return 设置成功返回 true，键为 null 时返回 false
     */
    public <T> Mono<Boolean> set(String key, T value, Duration ttl) {
        if (key == null) {
            return Mono.just(false);
        }
        try {
            org.springframework.cache.Cache cache = cacheManager.getCache(DEFAULT_CACHE_NAME);
            if (cache == null) {
                return Mono.just(false);
            }
            Object cacheValue;
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                cacheValue = value;
            } else {
                cacheValue = objectMapper.writeValueAsString(value);
            }
            cache.put(key, cacheValue);
            // 记录 TTL：仅在 ttl 不为 null 时设置过期时间戳
            if (ttl != null) {
                ttlTracker.put(key, System.currentTimeMillis() + ttl.toMillis());
            }
            return Mono.just(true);
        } catch (Exception e) {
            log.warn("设置本地缓存失败, key: {}", key, e);
            return Mono.just(false);
        }
    }

    /**
     * 从本地缓存中删除一个或多个键
     *
     * <p>逐个删除指定的缓存键。
     *
     * @param keys 要删除的缓存键数组
     * @return 成功删除的键数量
     */
    public Mono<Long> delete(String... keys) {
        if (keys == null || keys.length == 0) {
            return Mono.just(0L);
        }
        try {
            org.springframework.cache.Cache cache = cacheManager.getCache(DEFAULT_CACHE_NAME);
            if (cache == null) {
                return Mono.just(0L);
            }
            long deleted = 0;
            for (String key : keys) {
                if (key != null) {
                    cache.evictIfPresent(key);
                    ttlTracker.remove(key);
                    deleted++;
                }
            }
            return Mono.just(deleted);
        } catch (Exception e) {
            log.warn("删除本地缓存失败", e);
            return Mono.just(0L);
        }
    }

    /**
     * 批量从本地缓存中获取值
     *
     * <p>根据键列表批量获取缓存值，自动过滤不存在的键和异常。
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
        try {
            org.springframework.cache.Cache cache = cacheManager.getCache(DEFAULT_CACHE_NAME);
            if (cache == null) {
                return Mono.just(Collections.emptyList());
            }
            List<T> results = keys.stream()
                    .filter(Objects::nonNull)
                    .map(key -> {
                        try {
                            Long expireTime = ttlTracker.get(key);
                            if (expireTime != null && System.currentTimeMillis() > expireTime) {
                                ttlTracker.remove(key);
                                cache.evict(key);
                                return null;
                            }
                            org.springframework.cache.Cache.ValueWrapper wrapper = cache.get(key);
                            if (wrapper == null) {
                                return null;
                            }
                            Object value = wrapper.get();
                            if (value == null) {
                                return null;
                            }
                            return deserializeValue(value, clazz);
                        } catch (Exception e) {
                            log.warn("批量获取本地缓存失败, key: {}", key, e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return Mono.just(results);
        } catch (Exception e) {
            log.warn("批量获取本地缓存失败", e);
            return Mono.just(Collections.emptyList());
        }
    }

    /**
     * 批量向本地缓存中设置值
     *
     * <p>将键值对映射批量存入 Caffeine 缓存。
     *
     * @param <T>         缓存值的类型
     * @param keyValueMap 键值对映射
     * @return 操作完成返回 true，键值对映射为空时返回 false
     */
    public <T> Mono<Boolean> setBatch(Map<String, T> keyValueMap) {
        if (keyValueMap == null || keyValueMap.isEmpty()) {
            return Mono.just(false);
        }
        try {
            org.springframework.cache.Cache cache = cacheManager.getCache(DEFAULT_CACHE_NAME);
            if (cache == null) {
                return Mono.just(false);
            }
            keyValueMap.forEach((key, value) -> {
                try {
                    Object cacheValue;
                    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                        cacheValue = value;
                    } else {
                        cacheValue = objectMapper.writeValueAsString(value);
                    }
                    cache.put(key, cacheValue);
                } catch (Exception e) {
                    log.warn("批量设置本地缓存失败, key: {}", key, e);
                }
            });
            return Mono.just(true);
        } catch (Exception e) {
            log.warn("批量设置本地缓存失败", e);
            return Mono.just(false);
        }
    }

    /**
     * 清空本地缓存
     *
     * <p>清除当前缓存管理器中所有缓存条目，通常在缓存刷新或数据变更时调用。
     *
     * @return 清空操作完成信号
     */
    public Mono<Void> clear() {
        try {
            cacheManager.getCache(DEFAULT_CACHE_NAME).clear();
            ttlTracker.clear();
            log.debug("本地缓存已清空");
        } catch (Exception e) {
            log.warn("清空本地缓存失败", e);
        }
        return Mono.empty();
    }
}
