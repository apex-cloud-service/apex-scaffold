package com.apex.scaffold.infrastructure.mq.alert;

import com.apex.scaffold.common.exception.AlertEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 消息队列告警管理器
 *
 * <p>统一管理 RocketMQ 相关的告警，包括消息积压、死信队列、发送失败等。
 *
 * <h2>告警类型</h2>
 * <ul>
 *   <li>消息积压告警：Lag > 阈值</li>
 *   <li>死信消息告警：DLQ 消息速率过高</li>
 *   <li>消息发送失败告警：发送失败率 > 阈值</li>
 *   <li>消费失败告警：消费失败率 > 阈值</li>
 *   <li>消息降级告警：触发 Outbox 降级</li>
 * </ul>
 *
 * <h2>告警级别</h2>
 * <ul>
 *   <li>INFO: 轻微异常，无需立即处理</li>
 *   <li>WARNING: 需要关注，可能影响系统</li>
 *   <li>ERROR: 严重问题，需要立即处理</li>
 *   <li>CRITICAL: 致命问题，系统可能不可用</li>
 * </ul>
 *
 * <h2>告警通知</h2>
 * <p>告警信息会：
 * <ul>
 *   <li>记录到日志（根据级别使用不同日志级别）</li>
 *   <li>暴露为 Prometheus 指标</li>
 *   <li>触发 Prometheus Alertmanager 告警</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Component
public class MqAlertManager {

    private final MeterRegistry meterRegistry;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${apex.mq.alert.lag-warning-threshold:10000}")
    private long lagWarningThreshold;

    @Value("${apex.mq.alert.lag-critical-threshold:50000}")
    private long lagCriticalThreshold;

    @Value("${apex.mq.alert.dlq-warning-rate:1}")
    private double dlqWarningRate;

    @Value("${apex.mq.alert.dlq-critical-rate:10}")
    private double dlqCriticalRate;

    @Value("${apex.mq.alert.send-fail-rate-threshold:0.05}")
    private double sendFailRateThreshold;

    @Value("${apex.mq.alert.consume-fail-rate-threshold:0.1}")
    private double consumeFailRateThreshold;

    @Value("${apex.mq.alert.cooldown-ms:300000}")
    private long cooldownMs;

    private final Map<String, Long> lastAlertTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> alertCount = new ConcurrentHashMap<>();

    private final Counter lagAlertCounter;
    private final Counter dlqAlertCounter;
    private final Counter sendFailAlertCounter;
    private final Counter consumeFailAlertCounter;

    /**
     * 构造 MQ 告警管理器并注册各类告警计数器。
     *
     * @param meterRegistry Micrometer 指标注册表
     * @param eventPublisher 应用事件发布器
     */
    public MqAlertManager(MeterRegistry meterRegistry, ApplicationEventPublisher eventPublisher) {
        this.meterRegistry = meterRegistry;
        this.eventPublisher = eventPublisher;

        // 注册告警计数器
        this.lagAlertCounter =
                Counter.builder("mq.alert.lag").description("消息积压告警次数").register(meterRegistry);

        this.dlqAlertCounter =
                Counter.builder("mq.alert.dlq").description("死信队列告警次数").register(meterRegistry);

        this.sendFailAlertCounter =
                Counter.builder("mq.alert.send.fail").description("消息发送失败告警次数").register(meterRegistry);

        this.consumeFailAlertCounter = Counter.builder("mq.alert.consume.fail")
                .description("消息消费失败告警次数")
                .register(meterRegistry);
    }

    /**
     * 发送消息积压告警
     *
     * @param consumerGroup 消费者组名
     * @param lag           积压量
     */
    public void alertLag(String consumerGroup, long lag) {
        if (lag < lagWarningThreshold) {
            return;
        }
        String alertKey = "lag_" + consumerGroup;

        if (!shouldAlert(alertKey)) {
            return;
        }

        AlertLevel level = lag >= lagCriticalThreshold ? AlertLevel.CRITICAL : AlertLevel.WARNING;

        String message = String.format(
                "【消息积压告警】消费者 %s 消息积压: %d 条 (阈值: %s-%d, 严重-%d)",
                consumerGroup,
                lag,
                level == AlertLevel.WARNING ? "警告" : "严重",
                lagWarningThreshold,
                lagCriticalThreshold);

        publishAlert(level, message);
        lagAlertCounter.increment();
        updateAlertTime(alertKey);
    }

    /**
     * 发送死信队列告警
     *
     * @param consumerGroup 消费者组名
     * @param rate          死信消息速率（msg/s）
     */
    public void alertDlq(String consumerGroup, double rate) {
        if (rate < dlqWarningRate) {
            return;
        }
        String alertKey = "dlq_" + consumerGroup;

        if (!shouldAlert(alertKey)) {
            return;
        }

        AlertLevel level = rate >= dlqCriticalRate ? AlertLevel.CRITICAL : AlertLevel.WARNING;

        String message = String.format(
                "【死信队列告警】消费者 %s 死信消息速率: %.2f msg/s (阈值: 警告-%.0f, 严重-%.0f)",
                consumerGroup, rate, dlqWarningRate, dlqCriticalRate);

        publishAlert(level, message);
        dlqAlertCounter.increment();
        updateAlertTime(alertKey);
    }

    /**
     * 发送消息发送失败告警
     *
     * @param topic    主题名
     * @param failRate 失败率
     */
    public void alertSendFail(String topic, double failRate) {
        if (failRate < sendFailRateThreshold) {
            return;
        }
        String alertKey = "send_fail_" + topic;

        if (!shouldAlert(alertKey)) {
            return;
        }

        AlertLevel level = failRate >= sendFailRateThreshold * 2 ? AlertLevel.ERROR : AlertLevel.WARNING;

        String message = String.format(
                "【消息发送失败告警】主题 %s 发送失败率: %.2f%% (阈值: %.2f%%)", topic, failRate * 100, sendFailRateThreshold * 100);

        publishAlert(level, message);
        sendFailAlertCounter.increment();
        updateAlertTime(alertKey);
    }

    /**
     * 发送消息消费失败告警
     *
     * @param consumerGroup 消费者组名
     * @param failRate      失败率
     */
    public void alertConsumeFail(String consumerGroup, double failRate) {
        if (failRate < consumeFailRateThreshold) {
            return;
        }
        String alertKey = "consume_fail_" + consumerGroup;

        if (!shouldAlert(alertKey)) {
            return;
        }

        AlertLevel level = failRate >= consumeFailRateThreshold * 2 ? AlertLevel.ERROR : AlertLevel.WARNING;

        String message = String.format(
                "【消息消费失败告警】消费者 %s 消费失败率: %.2f%% (阈值: %.2f%%)",
                consumerGroup, failRate * 100, consumeFailRateThreshold * 100);

        publishAlert(level, message);
        consumeFailAlertCounter.increment();
        updateAlertTime(alertKey);
    }

    /**
     * 发送消息降级告警
     *
     * @param topic 主题名
     * @param reason 降级原因
     */
    public void alertFallback(String topic, String reason) {
        String alertKey = "fallback_" + topic;

        if (!shouldAlert(alertKey)) {
            return;
        }

        String message = String.format("【消息降级告警】主题 %s 触发降级: %s", topic, reason);

        publishAlert(AlertLevel.WARNING, message);
        updateAlertTime(alertKey);
    }

    /**
     * 判断是否应该发送告警（防止告警风暴）
     *
     * @param alertKey 告警键
     * @return 是否应该告警
     */
    private boolean shouldAlert(String alertKey) {
        long now = System.currentTimeMillis();
        AtomicLong decision = new AtomicLong();
        lastAlertTime.compute(alertKey, (key, lastTime) -> {
            if (lastTime == null || now - lastTime >= cooldownMs) {
                decision.set(now);
                return now;
            }
            decision.set(0L);
            return lastTime;
        });
        return decision.get() == now;
    }

    /**
     * 更新告警时间
     *
     * @param alertKey 告警键
     */
    private void updateAlertTime(String alertKey) {
        lastAlertTime.put(alertKey, System.currentTimeMillis());
        alertCount.computeIfAbsent(alertKey, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * 发送告警
     *
     * @param level   告警级别
     * @param message 告警消息
     */
    private void publishAlert(AlertLevel level, String message) {
        switch (level) {
            case INFO, WARNING -> log.warn(message);
            case ERROR, CRITICAL -> log.error(message);
            default -> log.warn("未知告警级别: {}, message={}", level, message);
        }

        eventPublisher.publishEvent(AlertEvent.builder()
                .exceptionType("MQ_" + level.name())
                .message(message)
                .serviceName("apex-scaffold")
                .level(toAlertLevel(level))
                .count(1)
                .build());
    }

    /**
     * 将 MQ 内部告警级别一一映射为共享告警事件的同名级别。
     *
     * @param level MQ 内部告警级别
     * @return 对应的共享告警事件级别
     */
    private AlertEvent.AlertLevel toAlertLevel(AlertLevel level) {
        return switch (level) {
            case INFO -> AlertEvent.AlertLevel.INFO;
            case WARNING -> AlertEvent.AlertLevel.WARNING;
            case ERROR -> AlertEvent.AlertLevel.ERROR;
            case CRITICAL -> AlertEvent.AlertLevel.CRITICAL;
        };
    }

    /**
     * 获取告警统计
     *
     * @return 告警统计 Map
     */
    public Map<String, Long> getAlertStatistics() {
        Map<String, Long> statistics = new ConcurrentHashMap<>(16);
        for (Map.Entry<String, AtomicLong> entry : alertCount.entrySet()) {
            statistics.put(entry.getKey(), entry.getValue().get());
        }
        return statistics;
    }

    /**
     * 重置告警冷却时间（用于测试或手动重置）
     *
     * @param alertKey 告警键
     */
    public void resetCooldown(String alertKey) {
        lastAlertTime.remove(alertKey);
        log.info("已重置告警冷却时间: {}", alertKey);
    }

    /**
     * 定时清理过期的告警记录（每小时执行一次）
     */
    @Scheduled(fixedRate = 3600000, initialDelay = 3600000)
    public void cleanupExpiredAlerts() {
        long now = System.currentTimeMillis();
        // 24 小时
        long expirationTime = 24 * 60 * 60 * 1000L;

        lastAlertTime.entrySet().removeIf(entry -> now - entry.getValue() > expirationTime);

        log.debug("已清理过期告警记录");
    }

    /**
     * 告警级别
     */
    public enum AlertLevel {
        // 信息
        INFO,
        // 警告
        WARNING,
        // 错误
        ERROR,
        // 严重
        CRITICAL
    }
}
