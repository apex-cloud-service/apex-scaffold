package com.apex.scaffold.domain.gateway;

import java.time.Duration;
import reactor.core.publisher.Mono;

/**
 * 统一授权快照的短期缓存契约。
 *
 * @author Apex Team
 * @since 1.3.0
 */
public interface AuthorizationCache {
    /**
     * 读取指定类型的租户授权快照。
     *
     * @param key 包含租户与用户身份的缓存键
     * @param type 快照类型
     * @param <T> 快照类型
     * @return 缓存快照，未命中时为空
     */
    <T> Mono<T> get(String key, Class<T> type);

    /**
     * 按限定有效期保存授权快照。
     *
     * @param key 包含租户与用户身份的缓存键
     * @param value 快照
     * @param ttl 有效期
     * @param <T> 快照类型
     * @return 是否保存成功
     */
    <T> Mono<Boolean> set(String key, T value, Duration ttl);
}
