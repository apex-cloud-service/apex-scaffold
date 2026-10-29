package com.apex.scaffold.infrastructure.mq.metrics;

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

/**
 * RocketMQ 生产者监控指标
 *
 * <p>通过 AOP 自动采集消息发送的监控指标，包括：
 * <ul>
 *   <li>发送成功次数</li>
 *   <li>发送失败次数</li>
 *   <li>发送延迟</li>
 *   <li>降级次数</li>
 * </ul>
 *
 * <h2>Prometheus 指标</h2>
 * <pre>
 * # 消息发送成功次数
 * mq_producer_send_success_total{topic="xxx",tag="xxx"}
 *
 * # 消息发送失败次数
 * mq_producer_send_failed_total{topic="xxx",tag="xxx"}
 *
 * # 消息发送延迟
 * mq_producer_send_duration_seconds{topic="xxx",quantile="0.99"}
 *
 * # 降级处理次数
 * mq_producer_fallback_total{topic="xxx",strategy="outbox"}
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Aspect
@Component
@ConditionalOnProperty(
        prefix = "apex.mq.producer",
        name = "metrics-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MqProducerMetrics {

    private final Counter successCounter;
    private final Counter failedCounter;
    private final Counter fallbackCounter;
    private final Timer sendDurationTimer;

    /**
     * 构造 MQ 生产者指标并注册发送成功/失败/降级计数器与延迟 Timer。
     *
     * @param meterRegistry Micrometer 指标注册表
     */
    public MqProducerMetrics(MeterRegistry meterRegistry) {
        this.successCounter = Counter.builder("mq.producer.send.success")
                .tag("component", "mq")
                .description("消息发送成功次数")
                .register(meterRegistry);

        this.failedCounter = Counter.builder("mq.producer.send.failed")
                .tag("component", "mq")
                .description("消息发送失败次数")
                .register(meterRegistry);

        this.fallbackCounter = Counter.builder("mq.producer.fallback")
                .tag("component", "mq")
                .description("消息发送降级次数")
                .register(meterRegistry);

        this.sendDurationTimer = Timer.builder("mq.producer.send.duration")
                .tag("component", "mq")
                .description("消息发送延迟")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);
    }

    /**
     * 拦截 sendSync 方法
     */
    @Around("execution(* com.apex.scaffold.infrastructure.mq.service.MqProducerService.sendSync(..))")
    public Object aroundSendSync(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.nanoTime();

        try {
            Object result = joinPoint.proceed();

            // 记录成功
            successCounter.increment();
            sendDurationTimer.record(Duration.ofNanos(System.nanoTime() - startTime));

            return result;
        } catch (Throwable e) {
            // 记录失败
            failedCounter.increment();
            sendDurationTimer.record(Duration.ofNanos(System.nanoTime() - startTime));
            throw e;
        }
    }

    /**
     * 拦截 sendAsync 方法
     */
    @Around("execution(* com.apex.scaffold.infrastructure.mq.service.MqProducerService.sendAsync(..))")
    public Object aroundSendAsync(ProceedingJoinPoint joinPoint) throws Throwable {
        // 异步发送仅记录发起次数，成功失败由回调统计
        return joinPoint.proceed();
    }

    /**
     * 拦截降级处理方法
     */
    @Around("execution(* com.apex.scaffold.infrastructure.mq.service.MqProducerService.handleSendFailureFallback(..))")
    public Object aroundFallback(ProceedingJoinPoint joinPoint) throws Throwable {
        fallbackCounter.increment();
        return joinPoint.proceed();
    }
}
