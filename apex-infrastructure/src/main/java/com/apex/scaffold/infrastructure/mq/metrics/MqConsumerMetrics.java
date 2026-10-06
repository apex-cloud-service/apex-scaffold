package com.apex.scaffold.infrastructure.mq.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 消费者监控指标
 *
 * <p>采集消息消费的监控指标，包括：
 * <ul>
 *   <li>消费成功次数</li>
 *   <li>消费失败次数</li>
 *   <li>消费延迟</li>
 *   <li>重复消费次数（幂等拦截）</li>
 *   <li>死信消息数</li>
 *   <li>消费积压（Lag）</li>
 * </ul>
 *
 * <h2>Prometheus 指标</h2>
 * <pre>
 * # 消息消费成功次数
 * mq_consumer_consume_success_total{topic="xxx",group="xxx"}
 *
 * # 消息消费失败次数
 * mq_consumer_consume_failed_total{topic="xxx",group="xxx"}
 *
 * # 消息消费延迟
 * mq_consumer_consume_duration_seconds{topic="xxx",quantile="0.99"}
 *
 * # 重复消费拦截次数
 * mq_consumer_duplicate_blocked_total{topic="xxx"}
 *
 * # 死信消息数
 * mq_consumer_dlq_messages_total{topic="xxx"}
 *
 * # 消费积压（当前未消费消息数）
 * mq_consumer_lag{topic="xxx",group="xxx"}
 * </pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "apex.mq.consumer",
        name = "metrics-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MqConsumerMetrics {

    private final Counter successCounter;
    private final Counter failedCounter;
    private final Counter duplicateCounter;
    private final Counter dlqCounter;
    private final Timer consumeDurationTimer;
    private final AtomicLong currentLag = new AtomicLong(0);

    /** 构造 MQ 消费者指标并注册计数器、延迟 Timer 与 Lag Gauge。 */
    public MqConsumerMetrics(MeterRegistry meterRegistry) {
        this.successCounter = Counter.builder("mq.consumer.consume.success")
                .tag("component", "mq")
                .description("消息消费成功次数")
                .register(meterRegistry);

        this.failedCounter = Counter.builder("mq.consumer.consume.failed")
                .tag("component", "mq")
                .description("消息消费失败次数")
                .register(meterRegistry);

        this.duplicateCounter = Counter.builder("mq.consumer.duplicate.blocked")
                .tag("component", "mq")
                .description("重复消息拦截次数")
                .register(meterRegistry);

        this.dlqCounter = Counter.builder("mq.consumer.dlq.messages")
                .tag("component", "mq")
                .description("死信消息数")
                .register(meterRegistry);

        this.consumeDurationTimer = Timer.builder("mq.consumer.consume.duration")
                .tag("component", "mq")
                .description("消息消费延迟")
                .publishPercentiles(0.5, 0.9, 0.95, 0.99)
                .register(meterRegistry);

        Gauge.builder("mq.consumer.lag", currentLag, AtomicLong::get)
                .tag("component", "mq")
                .description("消费积压（未消费消息数）")
                .register(meterRegistry);
    }

    /**
     * 记录消费成功
     *
     * @param durationNanos 本次消费耗时（纳秒）
     */
    public void recordSuccess(long durationNanos) {
        successCounter.increment();
        consumeDurationTimer.record(java.time.Duration.ofNanos(durationNanos));
    }

    /**
     * 记录消费失败
     *
     * @param durationNanos 本次消费耗时（纳秒）
     */
    public void recordFailure(long durationNanos) {
        failedCounter.increment();
        consumeDurationTimer.record(java.time.Duration.ofNanos(durationNanos));
    }

    /**
     * 记录重复消息拦截
     */
    public void recordDuplicate() {
        duplicateCounter.increment();
    }

    /**
     * 记录死信消息
     */
    public void recordDlq() {
        dlqCounter.increment();
    }

    /**
     * 记录死信消息（别名方法）
     */
    public void recordDlqMessage() {
        recordDlq();
    }

    /**
     * 记录消费积压
     *
     * @param lag 当前未消费消息数（Lag）
     */
    public void updateLag(long lag) {
        currentLag.set(lag);
    }
}
