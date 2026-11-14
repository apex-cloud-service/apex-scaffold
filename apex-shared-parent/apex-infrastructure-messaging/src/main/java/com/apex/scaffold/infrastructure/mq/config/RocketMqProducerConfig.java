package com.apex.scaffold.infrastructure.mq.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 生产者增强配置
 *
 * <p>扩展 RocketMQ 生产者的可靠性、性能和监控配置，支持生产环境优化。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>✅ 发送超时控制</li>
 *   <li>✅ 失败重试策略</li>
 *   <li>✅ 批量发送优化</li>
 *   <li>✅ 压缩配置</li>
 *   <li>✅ 故障降级</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * apex:
 *   mq:
 *     producer:
 *       send-timeout-ms: 3000           # 发送超时
 *       retry-times-when-send-failed: 2  # 同步发送失败重试次数
 *       retry-times-when-send-async-failed: 2  # 异步发送失败重试次数
 *       max-message-size: 4194304        # 最大消息体大小 4MB
 *       compress-msg-body-over-howmuch: 4096  # 大于 4KB 启用压缩
 *       batch:
 *         enabled: true
 *         max-batch-size: 32             # 批量发送最大消息数
 *         max-batch-bytes: 131072        # 批量发送最大字节数 128KB
 *       circuit-breaker:
 *         enabled: true
 *         failure-rate-threshold: 50     # 失败率阈值 50%
 *         wait-duration-in-open-state: 60000  # 熔断持续时间 60s
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.1.0
 */
@Data
@Component
@ConfigurationProperties(prefix = "apex.mq.producer")
public class RocketMqProducerConfig {

    /** 发送消息超时时间（毫秒） */
    private int sendTimeoutMs = 3000;

    /** 同步发送失败时的重试次数（不含首次） */
    private int retryTimesWhenSendFailed = 2;

    /** 异步发送失败时的重试次数（不含首次） */
    private int retryTimesWhenSendAsyncFailed = 2;

    /** 消息发送失败是否重试其他 Broker */
    @SuppressWarnings("PMD.LowerCamelCaseVariableNamingRule")
    private boolean retryAnotherBrokerWhenNotStoreOK = false;

    /** 最大消息体大小（字节），默认 4MB */
    private int maxMessageSize = 4 * 1024 * 1024;

    /** 消息体大于多少字节时启用压缩（字节），默认 4KB */
    private int compressMsgBodyOverHowmuch = 4096;

    /** 批量发送配置 */
    private BatchConfig batch = new BatchConfig();

    /** 熔断器配置 */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /** 故障降级配置 */
    private FallbackConfig fallback = new FallbackConfig();

    /**
     * 批量发送配置
     */
    @Data
    public static class BatchConfig {
        /** 是否启用批量发送 */
        private boolean enabled = false;

        /** 批量发送最大消息数 */
        private int maxBatchSize = 32;

        /** 批量发送最大字节数（字节），默认 128KB */
        private int maxBatchBytes = 128 * 1024;

        /** 批量发送等待时间（毫秒） */
        private int batchWaitMs = 100;
    }

    /**
     * 熔断器配置
     */
    @Data
    public static class CircuitBreakerConfig {
        /** 是否启用熔断器 */
        private boolean enabled = true;

        /** 失败率阈值（百分比：0-100） */
        private float failureRateThreshold = 50.0f;

        /** 慢调用时间阈值（毫秒） */
        private int slowCallDurationThreshold = 5000;

        /** 慢调用率阈值（百分比：0-100） */
        private float slowCallRateThreshold = 100.0f;

        /** 熔断器开启状态持续时间（毫秒） */
        private long waitDurationInOpenState = 60000L;

        /** 半开状态允许的调用数 */
        private int permittedNumberOfCallsInHalfOpenState = 10;

        /** 滑动窗口大小 */
        private int slidingWindowSize = 100;

        /** 最小调用数（达到此数才计算失败率） */
        private int minimumNumberOfCalls = 10;
    }

    /**
     * 故障降级配置
     */
    @Data
    public static class FallbackConfig {
        /** 是否启用故障降级 */
        private boolean enabled = true;

        /** 降级策略：log（仅记录日志）, outbox（写入 Outbox 表） */
        private FallbackStrategy strategy = FallbackStrategy.OUTBOX;

        /** 降级后是否抛出异常 */
        private boolean throwExceptionAfterFallback = false;
    }

    /**
     * 降级策略枚举
     */
    public enum FallbackStrategy {
        /** 仅记录日志 */
        LOG,
        /** 写入 Outbox 表待后续补偿 */
        OUTBOX,
        /** 写入本地队列待重试 */
        LOCAL_QUEUE
    }
}
