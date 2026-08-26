package com.apex.scaffold.domain.service;

import java.util.List;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * 缓存服务接口。
 *
 * <p>定义缓存操作的接口规范，由基础设施层实现（如Redis等中间件）。
 * 提供单值缓存、批量缓存、过期时间管理及缓存删除等核心操作。</p>
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li><b>单值读写</b>：基于key-value模式的缓存存取，支持泛型类型安全</li>
 *   <li><b>批量读写</b>：批量获取和设置缓存，减少网络往返次数，提升性能</li>
 *   <li><b>TTL管理</b>：支持为每个缓存条目设置独立的过期时间</li>
 *   <li><b>缓存删除</b>：支持单个或批量删除缓存条目</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>短信验证码临时存储（5分钟过期）</li>
 *   <li>用户会话Token缓存</li>
 *   <li>热点数据（如Elo分桶快照、配置信息）缓存加速</li>
 *   <li>接口幂等性Key缓存</li>
 * </ul>
 *
 * <h2>类型安全</h2>
 * <p>所有读写方法均使用泛型参数 {@code T} 和 {@code Class<T>} 保证类型安全。
 * 写入时自动序列化为JSON格式，读取时自动反序列化为目标类型。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface CacheService {

    /**
     * 获取单个缓存值。
     *
     * <p>根据缓存键获取对应的值并反序列化为指定类型。
     * 若键不存在或已过期，返回 {@link Mono#empty()}。</p>
     *
     * @param key   缓存键
     * @param clazz 目标反序列化类型
     * @param <T>   缓存值类型
     * @return 缓存值；若不存在或已过期返回空Mono
     */
    <T> Mono<T> get(String key, Class<T> clazz);

    /**
     * 设置单个缓存值（带过期时间）。
     *
     * <p>将指定值序列化后存储到缓存中，并设置过期时间。
     * 若键已存在，则覆盖旧值并重置过期时间。</p>
     *
     * @param key   缓存键
     * @param value 缓存值，自动序列化为JSON
     * @param ttl   过期时间，如 {@code Duration.ofMinutes(5)}
     * @param <T>   缓存值类型
     * @return true表示设置成功；false表示设置失败
     */
    <T> Mono<Boolean> set(String key, T value, java.time.Duration ttl);

    /**
     * 删除一个或多个缓存键。
     *
     * <p>批量删除指定的缓存键，返回实际被删除的键数量。</p>
     *
     * @param keys 待删除的缓存键数组
     * @return 实际被删除的缓存键数量
     */
    Mono<Long> delete(String... keys);

    /**
     * 批量获取缓存值。
     *
     * <p>一次性查询多个缓存键的值，减少网络IO次数。
     * 返回的列表顺序与输入的keys顺序不一定一致，
     * 不存在的键对应的值不会出现在结果列表中。</p>
     *
     * @param keys  缓存键列表
     * @param clazz 目标反序列化类型
     * @param <T>   缓存值类型
     * @return 存在的缓存值列表；若所有键均不存在返回空列表
     */
    <T> Mono<List<T>> getBatch(List<String> keys, Class<T> clazz);

    /**
     * 批量设置缓存值（带过期时间）。
     *
     * <p>一次性设置多个键值对到缓存中，共享相同的过期时间。
     * 用于批量预热缓存或批量更新配置等场景。</p>
     *
     * @param keyValueMap 键值对映射
     * @param ttl         过期时间，所有键共享此TTL
     * @param <T>         缓存值类型
     * @return true表示全部设置成功；false表示部分或全部设置失败
     */
    <T> Mono<Boolean> setBatch(Map<String, T> keyValueMap, java.time.Duration ttl);
}
