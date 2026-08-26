package com.apex.scaffold.application.service;

import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 幂等性服务（Redis 实现）
 *
 * <p>基于 Redis 的 SETNX + EXPIRE 原子操作实现分布式幂等性控制，
 * 防止多实例环境下重复提交。相比内存版 ConcurrentHashMap 实现，
 * Redis 版本支持多实例共享幂等性状态，保证集群环境下的幂等性一致性。
 *
 * <p>典型使用场景：
 *
 * <ul>
 *   <li>防止用户重复点击提交表单</li>
 *   <li>防止消息队列重复消费</li>
 *   <li>防止接口超时重试导致重复处理</li>
 * </ul>
 *
 * <h2>实现原理</h2>
 * <ul>
 *   <li>使用 Redis {@code SETNX + EXPIRE} 原子操作确保首次请求独占 Key</li>
 *   <li>利用 TTL 自动过期机制替代定时任务清理，无需维护额外线程池</li>
 *   <li>Key 前缀 {@code idempotent:} 保证与其他业务 Key 隔离</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
public class IdempotentService {

    /** 幂等性 Key 前缀，用于区分不同业务的幂等性 Key */
    private static final String IDEMPOTENT_KEY_PREFIX = "idempotent:";

    /** 默认过期时间 */
    private static final Duration DEFAULT_EXPIRE_TIME = Duration.ofMinutes(5);

    /** Redis 幂等性占位值 */
    private static final String PLACEHOLDER_VALUE = "1";

    /** Redis 响应式字符串模板 */
    private final ReactiveStringRedisTemplate redisTemplate;

    /**
     * 构造幂等性服务
     *
     * @param redisTemplate Redis 响应式字符串模板，由 Spring 自动注入
     */
    public IdempotentService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 检查并设置幂等性 Key（原子操作）
     *
     * <p>使用 Redis {@code SETNX + EXPIRE} 实现原子性的「检查并设置」操作，
     * 避免 read-then-write 竞态条件。首次请求时，通过 SETNX 成功写入 Key
     * 并设置 TTL 自动过期；重复请求时 Key 已存在，SETNX 返回 false。
     *
     * <p><strong>原子性保证</strong>：Redis {@code setIfAbsent}（底层 SETNX）
     * 是单线程模型下的原子操作，多实例并发调用时，仅有一个实例能成功设置 Key，
     * 其余实例均返回 false，保证分布式环境下的幂等性正确性。
     *
     * <p>与 v1.0 内存版的关键差异：
     * <ul>
     *   <li>v1.0：使用 {@code ConcurrentHashMap.putIfAbsent} + {@code ScheduledExecutorService} 定时过期</li>
     *   <li>v2.0：使用 Redis {@code SETNX + EXPIRE}，利用 TTL 自动过期，无需额外线程池</li>
     * </ul>
     *
     * @param key 幂等性 Key（通常为 requestId、token 或业务唯一标识）
     * @return true - 首次请求，允许处理；false - 重复请求，拒绝处理
     */
    public Mono<Boolean> checkAndSet(String key) {
        String cacheKey = IDEMPOTENT_KEY_PREFIX + key;

        return redisTemplate
                .opsForValue()
                .setIfAbsent(cacheKey, PLACEHOLDER_VALUE, DEFAULT_EXPIRE_TIME)
                .doOnNext(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.info("幂等性检查通过，key: {}", key);
                    } else {
                        log.warn("检测到重复请求，key: {}", key);
                    }
                });
    }

    /**
     * 删除幂等性 Key
     *
     * <p>在业务处理完成后主动调用，释放 Redis 中的幂等性 Key。
     * 实际生产环境中，TTL 自动过期会兜底清理，但仍建议业务正常流程中显式删除。
     *
     * @param key 幂等性 Key
     * @return true - 删除成功；false - Key 不存在或已过期
     */
    public Mono<Boolean> delete(String key) {
        String cacheKey = IDEMPOTENT_KEY_PREFIX + key;

        return redisTemplate.opsForValue().delete(cacheKey).doOnNext(deleted -> {
            if (Boolean.TRUE.equals(deleted)) {
                log.info("幂等性 Key 已删除，key: {}", key);
            }
        });
    }

    /**
     * 验证幂等性 Token 是否存在
     *
     * <p>仅验证 Token 是否存在，不修改 Redis 中的缓存状态。
     * 使用 Redis {@code EXISTS} 命令检查 Key 是否仍然有效。
     *
     * @param token 幂等性 Token
     * @return true - Token 有效且存在；false - Token 不存在或已过期
     */
    public Mono<Boolean> validateToken(String token) {
        String cacheKey = IDEMPOTENT_KEY_PREFIX + token;

        return redisTemplate.hasKey(cacheKey).doOnNext(exists -> {
            if (Boolean.FALSE.equals(exists)) {
                log.warn("幂等 Token 不存在或已过期");
            }
        });
    }
}
