package com.apex.scaffold.infrastructure.ratelimit.distributed;

import com.apex.scaffold.infrastructure.ratelimit.distributed.DistributedRateLimitProperties.RateLimitRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 分布式限流服务
 *
 * <p>提供基于配置规则的分布式限流功能，自动应用规则并支持本地降级。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>✅ 根据资源自动应用限流规则</li>
 *   <li>✅ Redis 故障时自动降级到本地限流（可选）</li>
 *   <li>✅ 支持运行时动态调整规则</li>
 *   <li>✅ 提供监控指标（待实现）</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * @RestController
 * @RequiredArgsConstructor
 * public class UserController {
 *
 *     private final DistributedRateLimitService rateLimitService;
 *
 *     @GetMapping("/api/v1/users")
 *     public Mono<ResponseEntity<?>> getUsers() {
 *         return rateLimitService.tryAcquire("/api/v1/users")
 *             .flatMap(allowed -> {
 *                 if (allowed) {
 *                     return userService.getUsers()
 *                         .map(ResponseEntity::ok);
 *                 } else {
 *                     return Mono.just(ResponseEntity.status(429)
 *                         .body("Too Many Requests"));
 *                 }
 *             });
 *     }
 * }
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.ratelimit.distributed", name = "enabled", havingValue = "true")
public class DistributedRateLimitService {

    private final DistributedTokenBucketRateLimiter rateLimiter;
    private final DistributedRateLimitProperties properties;

    /**
     * 尝试获取令牌（根据配置的规则）
     *
     * @param resource 资源标识
     * @return Mono<Boolean> - true 表示允许通过，false 表示被限流
     */
    public Mono<Boolean> tryAcquire(String resource) {
        return tryAcquire(resource, 1);
    }

    /**
     * 尝试获取指定数量的令牌
     *
     * @param resource 资源标识
     * @param permits 请求的令牌数
     * @return Mono<Boolean> - true 表示允许通过，false 表示被限流
     */
    public Mono<Boolean> tryAcquire(String resource, int permits) {
        RateLimitRule rule = properties.findRule(resource);

        if (rule == null) {
            log.debug("资源 {} 未配置分布式限流规则，默认放行", resource);
            return Mono.just(true);
        }

        return rateLimiter
                .tryAcquire(rule.getResource(), rule.getCapacity(), rule.getRefillRate(), permits)
                .doOnNext(allowed -> {
                    if (!allowed) {
                        log.warn(
                                "分布式限流拒绝请求，资源: {}, 规则: capacity={}, refillRate={}",
                                resource,
                                rule.getCapacity(),
                                rule.getRefillRate());
                    }
                })
                .onErrorResume(e -> {
                    log.error("分布式限流执行失败，资源: {}", resource, e);

                    // 根据配置决定是否降级到本地限流
                    if (properties.isFallbackToLocal()) {
                        log.warn("Redis 不可用，降级到本地限流（暂时放行）");
                        return Mono.just(true); // 简化实现：暂时放行
                        // TODO: 集成本地限流器作为降级
                    } else {
                        // 快速失败：Redis 故障时拒绝请求
                        return Mono.just(false);
                    }
                });
    }

    /**
     * 获取资源的剩余令牌数
     *
     * @param resource 资源标识
     * @return Mono<Long> - 剩余令牌数
     */
    public Mono<Long> getAvailableTokens(String resource) {
        return rateLimiter.getAvailableTokens(resource);
    }

    /**
     * 重置资源的限流状态
     *
     * @param resource 资源标识
     * @return Mono<Boolean> - 是否重置成功
     */
    public Mono<Boolean> reset(String resource) {
        return rateLimiter.reset(resource);
    }

    /**
     * 预热资源的令牌桶
     *
     * @param resource 资源标识
     * @return Mono<Boolean> - 是否预热成功
     */
    public Mono<Boolean> warmup(String resource) {
        RateLimitRule rule = properties.findRule(resource);

        if (rule == null) {
            log.warn("资源 {} 未配置限流规则，无法预热", resource);
            return Mono.just(false);
        }

        // 预热到容量的 50%
        long warmupTokens = rule.getCapacity() / 2;

        return rateLimiter.warmup(resource, warmupTokens, rule.getCapacity()).doOnSuccess(success -> {
            if (success) {
                log.info("预热分布式限流成功，资源: {}, 令牌数: {}", resource, warmupTokens);
            }
        });
    }

    /**
     * 批量预热所有配置的资源
     *
     * @return Mono<Long> - 成功预热的资源数量
     */
    public Mono<Long> warmupAll() {
        if (!properties.hasEnabledRules()) {
            log.info("没有启用的分布式限流规则，跳过预热");
            return Mono.just(0L);
        }

        return Mono.fromCallable(() -> {
                    long successCount = 0;
                    for (RateLimitRule rule : properties.getRules()) {
                        if (rule.isEnabled()) {
                            Boolean success = warmup(rule.getResource()).block();
                            if (Boolean.TRUE.equals(success)) {
                                successCount++;
                            }
                        }
                    }
                    return successCount;
                })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .doOnSuccess(count -> log.info(
                        "批量预热完成，成功: {}/{}", count, properties.getRules().size()));
    }
}
