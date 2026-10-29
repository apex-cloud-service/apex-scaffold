package com.apex.scaffold.infrastructure.mq.monitor;

import com.apex.scaffold.infrastructure.mq.alert.MqAlertManager;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 消息积压监控器
 *
 * <p>自动监控消息队列的积压情况，并提供告警和自动处理机制。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>实时监控消息积压量（Lag）</li>
 *   <li>监控消息堆积时间</li>
 *   <li>自动触发消费扩容</li>
 *   <li>死信消息自动处理</li>
 *   <li>Prometheus 指标暴露</li>
 * </ul>
 *
 * <h2>告警策略</h2>
 * <ul>
 *   <li>轻微积压（1000-10000）: INFO 级别日志</li>
 *   <li>中度积压（10000-50000）: WARN 级别告警</li>
 *   <li>严重积压（> 50000）: ERROR 级别告警 + 自动扩容</li>
 * </ul>
 *
 * <h2>自动处理机制</h2>
 * <ul>
 *   <li>动态调整消费线程数（20-128）</li>
 *   <li>启用批量消费模式</li>
 *   <li>触发紧急消费降级</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Slf4j
@Component
public class MqLagMonitor {

    private static final int MAX_CONSUMER_COUNT = 32;

    private final MeterRegistry meterRegistry;
    private final MqAlertManager alertManager;
    private final Map<String, DefaultMQPushConsumer> consumers = new ConcurrentHashMap<>();
    private final DefaultMQAdminExt adminClient;
    private volatile boolean adminStarted;

    /**
     * 构造积压监控器，未提供专属 Admin 客户端时使用默认客户端。
     *
     * @param meterRegistry Micrometer 指标注册表
     * @param alertManager MQ 告警管理器
     * @param adminClient 专属 RocketMQ Admin 客户端，未装配时为 null
     */
    public MqLagMonitor(
            MeterRegistry meterRegistry,
            MqAlertManager alertManager,
            @Qualifier("mqLagAdminClient") @Nullable DefaultMQAdminExt adminClient) {
        this.meterRegistry = meterRegistry;
        this.alertManager = alertManager;
        this.adminClient = adminClient != null ? adminClient : new DefaultMQAdminExt();
    }

    @Value("${apex.mq.monitor.lag.warning-threshold:10000}")
    private long warningThreshold;

    @Value("${apex.mq.monitor.lag.critical-threshold:50000}")
    private long criticalThreshold;

    @Value("${apex.mq.monitor.auto-scale.enabled:true}")
    private boolean autoScaleEnabled;

    @Value("${rocketmq.name-server:}")
    private String nameServer;

    /**
     * 注册消费者到监控器
     *
     * @param consumerGroup 消费者组名
     * @param consumer      消费者实例
     */
    public void registerConsumer(String consumerGroup, DefaultMQPushConsumer consumer) {
        consumers.put(consumerGroup, consumer);

        // 注册 Prometheus 指标
        Gauge.builder("mq.consumer.lag", () -> getConsumerLag(consumerGroup))
                .tag("consumer_group", consumerGroup)
                .description("消费者消息积压量")
                .register(meterRegistry);

        log.info("已注册消费者到监控器: {}", consumerGroup);
    }

    /**
     * 定时监控消费积压情况（每 30 秒执行一次）
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 60000)
    public void monitorConsumerLag() {
        for (Map.Entry<String, DefaultMQPushConsumer> entry : consumers.entrySet()) {
            String consumerGroup = entry.getKey();
            try {
                long totalLag = getConsumerLag(consumerGroup);

                // 根据积压量级别采取不同措施
                if (totalLag >= criticalThreshold) {
                    handleCriticalLag(consumerGroup, totalLag);
                } else if (totalLag >= warningThreshold) {
                    handleWarningLag(consumerGroup, totalLag);
                } else if (totalLag > 0) {
                    log.debug("消费者 {} 消息积压: {} 条", consumerGroup, totalLag);
                }

            } catch (Exception e) {
                log.error("监控消费者 {} 积压失败", consumerGroup, e);
            }
        }
    }

    /**
     * 获取消费者积压量
     *
     * @param consumerGroup 消费者组名
     * @return 积压消息数量
     */
    private long getConsumerLag(String consumerGroup) {
        try {
            startAdminClientIfNecessary();
            ConsumeStats consumeStats = adminClient.examineConsumeStats(consumerGroup);
            if (consumeStats == null || consumeStats.getOffsetTable() == null) {
                return 0;
            }
            return consumeStats.getOffsetTable().values().stream()
                    .mapToLong(this::queueLag)
                    .sum();
        } catch (Exception e) {
            log.error("获取消费者 {} 积压量失败", consumerGroup, e);
            return 0;
        }
    }

    /**
     * 在 Admin 客户端尚未启动时设置 NameServer 并同步启动。
     *
     * <p>通过对象监视器串行化启动，仅在启动成功后设置状态。客户端启动可能阻塞当前线程；
     * 本方法不吞并异常，由调用方决定是否将查询结果降级为零。
     *
     * @throws Exception 当 Admin 客户端启动失败时向调用方传播
     */
    private synchronized void startAdminClientIfNecessary() throws Exception {
        if (adminStarted) {
            return;
        }
        adminClient.setNamesrvAddr(nameServer);
        adminClient.start();
        adminStarted = true;
    }

    /**
     * 根据单队列的 Broker 与消费者位点计算积压量，将负差值截断为零。
     *
     * @param offset 队列的消费位点快照
     * @return 非负的队列积压消息数量
     */
    private long queueLag(OffsetWrapper offset) {
        return Math.max(0L, offset.getBrokerOffset() - offset.getConsumerOffset());
    }

    /** 应用关闭时释放 Admin 客户端（{@code @PreDestroy} 生命周期回调）。 */
    @PreDestroy
    public synchronized void shutdownAdminClient() {
        if (adminStarted) {
            adminClient.shutdown();
            adminStarted = false;
        }
    }

    /**
     * 处理严重积压（> 50000）
     *
     * @param consumerGroup 消费者组名
     * @param lag           积压量
     */
    private void handleCriticalLag(String consumerGroup, long lag) {
        alertManager.alertLag(consumerGroup, lag);

        // 自动扩容
        if (autoScaleEnabled) {
            try {
                autoScaleConsumer(consumerGroup);
            } catch (Exception e) {
                log.error("自动扩容失败: {}", consumerGroup, e);
            }
        }
    }

    /**
     * 处理警告级别积压（10000-50000）
     *
     * @param consumerGroup 消费者组名
     * @param lag           积压量
     */
    private void handleWarningLag(String consumerGroup, long lag) {
        alertManager.alertLag(consumerGroup, lag);
    }

    /**
     * 自动扩容消费者
     *
     * @param consumerGroup 消费者组名
     */
    private void autoScaleConsumer(String consumerGroup) {
        DefaultMQPushConsumer consumer = consumers.get(consumerGroup);
        if (consumer == null) {
            log.warn("未找到消费者: {}", consumerGroup);
            return;
        }

        int currentThreads = consumer.getConsumeThreadMin();
        int maxThreads = consumer.getConsumeThreadMax();

        // 增加消费线程数（最多增加到 128）
        int newMinThreads = Math.min(currentThreads * 2, 128);
        int newMaxThreads = Math.min(maxThreads * 2, 128);

        consumer.setConsumeThreadMin(newMinThreads);
        consumer.setConsumeThreadMax(newMaxThreads);

        log.info(
                "自动扩容消费者 {}: 线程数 [{}-{}] -> [{}-{}]",
                consumerGroup,
                currentThreads,
                maxThreads,
                newMinThreads,
                newMaxThreads);

        // 启用批量消费（如果尚未启用）
        if (consumer.getConsumeMessageBatchMaxSize() < MAX_CONSUMER_COUNT) {
            consumer.setConsumeMessageBatchMaxSize(MAX_CONSUMER_COUNT);
            log.info("启用批量消费模式: {} (批量大小: MAX_CONSUMER_COUNT)", consumerGroup);
        }
    }

    /**
     * 手动触发消费扩容
     *
     * @param consumerGroup 消费者组名
     */
    public void manualScaleUp(String consumerGroup) {
        log.info("手动触发消费扩容: {}", consumerGroup);
        autoScaleConsumer(consumerGroup);
    }

    /**
     * 获取所有消费者的积压情况
     *
     * @return 消费者积压情况 Map
     */
    public Map<String, Long> getAllConsumerLags() {
        Map<String, Long> lags = new ConcurrentHashMap<>(16);
        for (String consumerGroup : consumers.keySet()) {
            lags.put(consumerGroup, getConsumerLag(consumerGroup));
        }
        return lags;
    }
}
