package com.apex.scaffold.infrastructure.mq.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本地幂等缓存
 *
 * <p>当 Redis 不可用时作为降级方案,使用 Caffeine 本地内存缓存实现幂等性。
 * 适用于单实例或对幂等性要求不严格的场景。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>本地内存幂等:基于 Caffeine 缓存实现消息幂等性检查</li>
 *   <li>自动过期:支持配置 TTL,自动清理过期记录</li>
 *   <li>容量限制:防止内存溢出</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>Redis 集群故障时的降级方案</li>
 *   <li>开发测试环境的轻量级幂等实现</li>
 *   <li>对幂等性要求不严格的业务场景</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>仅支持单实例幂等,多实例部署时无法保证幂等性</li>
 *   <li>进程重启后幂等记录丢失</li>
 *   <li>不适用于对幂等性要求严格的金融等场景</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Component
public class LocalIdempotentCache {

    /**
     * Caffeine 本地缓存
     *
     * <p>存储格式: key=messageId, value=状态(PROCESSING/SUCCESS/FAILED)
     */
    private final Cache<String, String> localCache;

    /**
     * 构造函数
     *
     * @param maxSize 缓存最大容量,默认10000
     * @param ttlMinutes 缓存过期时间(分钟),默认30分钟
     */
    public LocalIdempotentCache(
            @Value("${apex.mq.consumer.local-idempotent-max-size:10000}") int maxSize,
            @Value("${apex.mq.consumer.idempotent-ttl-minutes:30}") int ttlMinutes) {

        this.localCache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                .recordStats()
                .build();

        log.info("LocalIdempotentCache 初始化: maxSize={}, ttlMinutes={}", maxSize, ttlMinutes);
    }

    /**
     * 尝试获取处理令牌
     *
     * <p>原子性地检查消息是否已处理,如果未处理则标记为 PROCESSING 状态。
     *
     * @param key 消息幂等键
     * @return true 表示获取成功(首次处理),false 表示消息已被处理或正在处理
     */
    public boolean tryAcquire(String key) {
        String existingValue = localCache.asMap().putIfAbsent(key, "PROCESSING");
        if (existingValue == null) {
            log.debug("本地幂等缓存: 首次处理 key={}", key);
            return true;
        }
        log.debug("本地幂等缓存: 重复消息 key={}, status={}", key, existingValue);
        return false;
    }

    /**
     * 标记消息处理成功
     *
     * @param key 消息幂等键
     */
    public void markSuccess(String key) {
        localCache.put(key, "SUCCESS");
        log.debug("本地幂等缓存: 标记成功 key={}", key);
    }

    /**
     * 标记消息处理失败
     *
     * @param key 消息幂等键
     */
    public void markFailed(String key) {
        localCache.put(key, "FAILED");
        log.debug("本地幂等缓存: 标记失败 key={}", key);
    }

    /**
     * 检查消息状态
     *
     * @param key 消息幂等键
     * @return 消息状态(PROCESSING/SUCCESS/FAILED),如果不存在返回 null
     */
    public String getStatus(String key) {
        return localCache.getIfPresent(key);
    }

    /**
     * 清除指定消息的幂等记录
     *
     * @param key 消息幂等键
     */
    public void invalidate(String key) {
        localCache.invalidate(key);
        log.debug("本地幂等缓存: 清除记录 key={}", key);
    }

    /**
     * 获取缓存统计信息
     *
     * @return 缓存统计信息字符串
     */
    public String getStats() {
        var stats = localCache.stats();
        return String.format(
                "LocalIdempotentCache Stats: hitRate=%.2f%%, size=%d, evictions=%d",
                stats.hitRate() * 100, localCache.estimatedSize(), stats.evictionCount());
    }
}
