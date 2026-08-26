package com.apex.scaffold.infrastructure.cache.decorator;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 缓存操作接口
 *
 * <p>定义缓存的基本操作接口，支持响应式编程模式
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>缓存读取：根据键获取缓存值</li>
 *   <li>缓存写入：设置缓存值并指定过期时间</li>
 *   <li>缓存删除：删除指定键的缓存</li>
 *   <li>批量读取：批量获取多个缓存值</li>
 *   <li>批量写入：批量设置多个缓存值</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>该接口采用响应式设计，返回Mono类型以支持异步非阻塞操作</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface CacheOperation {

    /**
     * 执行get操作
     *
     * <p>详细说明</p>
     *
     * @param key 参数说明
     * @param clazz 参数说明
     * @return 返回值说明
     */
    <T> Mono<T> get(String key, Class<T> clazz);

    /**
     * 执行set操作
     *
     * <p>详细说明</p>
     *
     * @param key 参数说明
     * @param value 参数说明
     * @param ttl 参数说明
     * @return 返回值说明
     */
    <T> Mono<Boolean> set(String key, T value, Duration ttl);

    /**
     * 批量删除缓存
     *
     * <p>删除指定键列表的缓存值
     *
     * @param keys 待删除的缓存键列表
     * @return 包含实际删除数量的Mono
     */
    Mono<Long> delete(String... keys);

    /**
     * 批量获取缓存值
     *
     * <p>根据多个key批量获取缓存值，自动过滤不存在的缓存
     *
     * @param <T>   返回值类型
     * @param keys  缓存键列表
     * @param clazz 目标类型Class对象
     * @return 包含非空缓存值列表的Mono
     */
    <T> Mono<List<T>> getBatch(List<String> keys, Class<T> clazz);

    /**
     * 批量设置缓存值
     *
     * <p>批量设置多个缓存值并指定统一的过期时间
     *
     * @param <T>         值类型
     * @param keyValueMap 键值对映射
     * @param ttl         过期时间
     * @return 包含操作结果的Mono
     */
    <T> Mono<Boolean> setBatch(Map<String, T> keyValueMap, Duration ttl);
}
