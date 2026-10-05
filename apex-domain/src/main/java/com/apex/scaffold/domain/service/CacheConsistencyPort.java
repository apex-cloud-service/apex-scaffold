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

    /**
     * 失效指定缓存键（延迟双删）。
     *
     * <p>针对并发读写导致缓存脏数据的经典场景：线程 A 读取 DB 旧值后，
     * 线程 B 更新 DB 并删除缓存，随后线程 A 将旧值写回缓存。
     * 该策略在「立即删除」之后延迟一段时间「再次删除」，以覆盖延迟写回的旧值，
     * 保障缓存与数据库最终一致。</p>
     *
     * <p>默认实现退化为单次删除 {@link #evict(String, String)}，
     * 便于不关心该特性的实现或测试桩保持向后兼容；基础设施层实现类会覆盖为真正的延迟双删。</p>
     *
     * @param cacheName 缓存名称
     * @param key 缓存键
     * @return 第二次删除是否成功（未启用延迟双删时返回首次删除结果）
     */
    default Mono<Boolean> evictWithDelayedDoubleDelete(String cacheName, String key) {
        return evict(cacheName, key);
    }
}
