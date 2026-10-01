package com.apex.scaffold.infrastructure.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 消费者增强配置
 *
 * <p>扩展 RocketMQ 消费者的可靠性、性能和监控配置，支持生产环境优化。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>✅ 并发消费控制</li>
 *   <li>✅ 消费超时控制</li>
 *   <li>✅ 消息拉取策略</li>
 *   <li>✅ 重试策略</li>
 *   <li>✅ 死信队列处理</li>
 *   <li>✅ 流量控制</li>
 * </ul>
 *
 * <h2>配置示例</h2>
 * <pre>{@code
 * apex:
 *   mq:
 *     consumer:
 *       enabled: true
 *       min-consume-thread: 20
 *       max-consume-thread: 64
 *       consume-timeout-minutes: 15
 *       max-reconsume-times: 16
 *       pull-batch-size: 32
 *       pull-interval-ms: 0
 *       flow-control:
 *         enabled: true
 *         max-consume-rate: 1000
 *         slow-consumer-threshold-ms: 5000
 *       dlq:
 *         enabled: true
 *         max-retry-before-dlq: 16
 *         dlq-handler-enabled: true
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.mq.consumer")
public class RocketMqConsumerConfig {

    /** 是否启用消费者 */
    private boolean enabled = true;

    /** 最小消费线程数 */
    private int minConsumeThread = 20;

    /** 最大消费线程数 */
    private int maxConsumeThread = 64;

    /** 消费超时时间（分钟） */
    private long consumeTimeoutMinutes = 15;

    /** 消息最大重试次数 */
    private int maxReconsumeTimes = 16;

    /** 每次拉取的消息数量 */
    private int pullBatchSize = 32;

    /** 拉取消息的间隔（毫秒），0 表示不等待 */
    private long pullIntervalMs = 0;

    /** 消息消费模式：CLUSTERING（集群）, BROADCASTING（广播） */
    private String messageModel = "CLUSTERING";

    /** 流量控制配置 */
    private FlowControlConfig flowControl = new FlowControlConfig();

    /** 死信队列配置 */
    private DlqConfig dlq = new DlqConfig();

    /** 幂等性配置 */
    private IdempotentConfig idempotent = new IdempotentConfig();

    /**
     * 流量控制配置
     */
    @Data
    public static class FlowControlConfig {
        /** 是否启用流量控制 */
        private boolean enabled = true;

        /** 最大消费速率（消息/秒），0 表示不限制 */
        private int maxConsumeRate = 1000;

        /** 慢消费阈值（毫秒），超过此时间算慢消费 */
        private long slowConsumerThresholdMs = 5000;

        /** 慢消费告警阈值（百分比：0-100） */
        private float slowConsumerAlertThreshold = 30.0f;

        /** 是否启用自适应流控（根据消费延迟动态调整） */
        private boolean adaptiveEnabled = false;
    }

    /**
     * 死信队列配置
     */
    @Data
    public static class DlqConfig {
        /** 是否启用死信队列处理 */
        private boolean enabled = true;

        /** 进入死信队列前的最大重试次数 */
        private int maxRetryBeforeDlq = 16;

        /** 是否启用死信队列自动处理器 */
        private boolean dlqHandlerEnabled = true;

        /** 死信消息保留时间（小时） */
        private int dlqMessageRetentionHours = 72;

        /** 是否自动告警死信消息 */
        private boolean alertOnDlqMessage = true;
    }

    /**
     * 幂等性配置
     */
    @Data
    public static class IdempotentConfig {
        /** 是否启用幂等性检查 */
        private boolean enabled = true;

        /** 幂等性记录 TTL（分钟） */
        private long ttlMinutes = 30;

        /** 是否使用本地缓存作为幂等性一级缓存 */
        private boolean localCacheEnabled = true;

        /** 本地缓存最大条目数 */
        private int localCacheMaxSize = 10000;

        /** 本地缓存 TTL（分钟） */
        private long localCacheTtlMinutes = 10;
    }
}
