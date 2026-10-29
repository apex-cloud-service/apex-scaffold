package com.apex.scaffold.infrastructure.ratelimit.distributed;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 分布式限流监控指标采集
 *
 * <p>通过 AOP 自动采集分布式限流的监控指标，包括：
 * <ul>
 *   <li>限流通过次数</li>
 *   <li>限流拒绝次数</li>
 *   <li>限流执行延迟</li>
 *   <li>Redis 故障次数</li>
 * </ul>
 *
 * <h2>Prometheus 指标</h2>
 * <pre>
 * # 限流通过次数
 * ratelimit_distributed_allowed_total{component="ratelimit"}
 *
 * # 限流拒绝次数
 * ratelimit_distributed_rejected_total{component="ratelimit"}
 *
 * # 限流执行延迟
 * ratelimit_distributed_duration_seconds{quantile="0.5"}
 * ratelimit_distributed_duration_seconds{quantile="0.99"}
 *
 * # Redis 故障次数
 * ratelimit_distributed_redis_errors_total{component="ratelimit"}
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(prefix = "apex.ratelimit.distributed", name = "enabled", havingValue = "true")
public class DistributedRateLimitMetrics {

    private final Counter allowedCounter;
    private final Counter rejectedCounter;
    private final Counter redisErrorCounter;
    private final Timer durationTimer;

    /**
     * 构造分布式限流指标并注册通过/拒绝/Redis 故障计数器与延迟 Timer。
     *
     * @param meterRegistry Micrometer 指标注册表
     */
    public DistributedRateLimitMetrics(MeterRegistry meterRegistry) {
        this.allowedCounter = Counter.builder("ratelimit.distributed.allowed")
                .tag("component", "ratelimit")
                .description("分布式限流通过次数")
                .register(meterRegistry);

        this.rejectedCounter = Counter.builder("ratelimit.distributed.rejected")
                .tag("component", "ratelimit")
                .description("分布式限流拒绝次数")
                .register(meterRegistry);

        this.redisErrorCounter = Counter.builder("ratelimit.distributed.redis.errors")
                .tag("component", "ratelimit")
                .description("Redis 限流故障次数")
                .register(meterRegistry);

        this.durationTimer = Timer.builder("ratelimit.distributed.duration")
                .tag("component", "ratelimit")
                .description("分布式限流执行延迟")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * 拦截 tryAcquire 方法，采集监控指标
     */
    @Around(
            "execution(* com.apex.scaffold.infrastructure.ratelimit.distributed.DistributedTokenBucketRateLimiter.tryAcquire(..))")
    public Object aroundTryAcquire(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.nanoTime();

        try {
            Object result = joinPoint.proceed();

            // 处理 Mono 返回值
            if (result instanceof Mono) {
                return ((Mono<?>) result)
                        .doOnSuccess(allowed -> {
                            // 记录执行时间
                            durationTimer.record(Duration.ofNanos(System.nanoTime() - startTime));

                            // 记录通过/拒绝次数
                            if (Boolean.TRUE.equals(allowed)) {
                                allowedCounter.increment();
                            } else {
                                rejectedCounter.increment();
                            }
                        })
                        .doOnError(e -> {
                            // 记录 Redis 故障
                            redisErrorCounter.increment();
                            log.error("分布式限流执行失败", e);
                        });
            }

            return result;
        } catch (Throwable e) {
            redisErrorCounter.increment();
            throw e;
        }
    }
}
