package com.apex.scaffold.infrastructure.resilience.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.infrastructure.resilience.config.BulkheadProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 分布式舱壁隔离服务（Enterprise License 特性）
 *
 * <p>基于 Redisson 分布式信号量实现跨实例的并发隔离舱，防止单个下游服务故障或慢调用拖垮整个集群。
 * 与本地 Resilience4j Bulkhead 不同，分布式舱壁在多实例环境中共享同一个并发上限。
 *
 * <h2>核心特性</h2>
 * <ul>
 *   <li><b>分布式信号量</b>：基于 Redis 实现跨实例的许可证（permit）管理</li>
 *   <li><b>快速失败</b>：超限时立即拒绝（不排队），避免请求堆积</li>
 *   <li><b>自动释放</b>：支持超时自动释放许可证，防止泄漏</li>
 *   <li><b>指标监控</b>：记录拒绝次数（apex_bulkhead_rejected_total）</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>限制对第三方 API 的并发调用数（如支付网关、AI 推理服务）</li>
 *   <li>保护慢查询数据库操作（如报表生成、全表扫描）</li>
 *   <li>隔离不同租户的资源消耗（SaaS 多租户场景）</li>
 * </ul>
 *
 * <h2>与熔断器联动</h2>
 * <p>建议与 {@link CircuitBreakerService} 配合使用：
 * <pre>{@code
 * bulkheadService.execute("third-party-api", () ->
 *     circuitBreakerService.execute("third-party-api", () ->
 *         callThirdPartyApi()
 *     )
 * )
 * }</pre>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 * @see CircuitBreakerService
 * @see BulkheadProperties
 */
@Slf4j
@Service
public class DistributedBulkheadService {

    private static final String SEMAPHORE_KEY_PREFIX = "bulkhead:semaphore:";

    private final RedissonClient redissonClient;

    private final BulkheadProperties bulkheadProperties;

    private final Counter rejectedCounter;

    /**
     * 构造分布式舱壁服务
     *
     * @param redissonClient      Redisson 客户端
     * @param bulkheadProperties  舱壁配置属性
     * @param meterRegistry       指标注册表
     */
    public DistributedBulkheadService(
            RedissonClient redissonClient, BulkheadProperties bulkheadProperties, MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.bulkheadProperties = bulkheadProperties;
        this.rejectedCounter = Counter.builder("apex_bulkhead_rejected_total")
                .description("分布式舱壁拒绝次数")
                .register(meterRegistry);
    }

    /**
     * 在分布式舱壁保护下执行操作
     *
     * <p>尝试获取分布式信号量许可证，成功则执行操作，超限则快速失败。
     * 操作完成后自动释放许可证。
     *
     * @param <T>       返回值类型
     * @param key       舱壁隔离键（如 "third-party-api"、"slow-report"）
     * @param operation 要执行的操作
     * @return 操作结果
     * @throws BusinessException 当舱壁超限时抛出 SERVICE_UNAVAILABLE
     */
    public <T> Mono<T> execute(String key, Supplier<Mono<T>> operation) {
        if (!bulkheadProperties.isEnabled()) {
            return operation.get();
        }

        String semaphoreKey = SEMAPHORE_KEY_PREFIX + key;
        int maxConcurrent = bulkheadProperties.getMaxConcurrentCalls();
        Duration timeout = Duration.ofMillis(bulkheadProperties.getPermitTimeoutMs());

        return Mono.defer(() -> {
            RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(semaphoreKey);

            try {
                semaphore.trySetPermits(maxConcurrent);
            } catch (Exception e) {
                log.warn("初始化分布式信号量失败, key: {}", semaphoreKey, e);
            }

            String permitId;
            try {
                permitId = semaphore.tryAcquire(0, timeout.toMillis(), TimeUnit.MILLISECONDS);
                if (permitId == null) {
                    log.warn("分布式舱壁超限，快速失败, key: {}, maxConcurrent: {}", key, maxConcurrent);
                    rejectedCounter.increment();
                    return Mono.error(new BusinessException(ResultCode.SYSTEM_BUSY, "系统繁忙，请稍后重试"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("获取分布式信号量被中断, key: {}", key, e);
                return Mono.error(new BusinessException(ResultCode.SYSTEM_BUSY, "系统繁忙，请稍后重试"));
            }

            log.debug("获取分布式信号量成功, key: {}, permitId: {}", key, permitId);

            return operation.get().doFinally(signalType -> {
                try {
                    semaphore.release(permitId);
                    log.debug("释放分布式信号量, key: {}, permitId: {}", key, permitId);
                } catch (Exception e) {
                    log.error("释放分布式信号量失败, key: {}, permitId: {}", key, permitId, e);
                }
            });
        });
    }

    /**
     * 获取指定舱壁的当前可用许可证数量
     *
     * @param key 舱壁隔离键
     * @return 可用许可证数量
     */
    public Mono<Integer> getAvailablePermits(String key) {
        String semaphoreKey = SEMAPHORE_KEY_PREFIX + key;
        return Mono.fromCallable(() -> {
            RPermitExpirableSemaphore semaphore = redissonClient.getPermitExpirableSemaphore(semaphoreKey);
            return semaphore.availablePermits();
        });
    }
}
