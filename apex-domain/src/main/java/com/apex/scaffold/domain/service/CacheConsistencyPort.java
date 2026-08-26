package com.apex.scaffold.domain.service;

import reactor.core.publisher.Mono;

/**
 * 应用层所需的缓存失效端口，由基础设施层实现。
 *
 * <p>将缓存失效操作封装为领域端口，使应用层在更新业务数据后
 * 可以通知缓存层清理对应的缓存条目，而不直接依赖 Redis/Caffeine
 * 等具体缓存实现。</p>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>用户信息更新后失效用户缓存</li>
 *   <li>照片状态变更后失效照片缓存</li>
 *   <li>排行榜重算后失效排名缓存</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface CacheConsistencyPort {

    /**
     * 失效指定缓存键。
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @return 是否成功失效
     */
    Mono<Boolean> evict(String cacheName, String key);
}
