package com.apex.scaffold.infrastructure.mq.listener;

import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.autoconfigure.RocketMQAutoConfiguration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RocketMQ 死信队列（DLQ）自动处理器。
 *
 * <p>自动监听 RocketMQ 死信队列中的消息，对消费重试耗尽的消息进行二次处理、
 * 告警通知和持久化记录，避免死信消息只能人工查询处理的问题。
 *
 * <h2>处理流程</h2>
 * <ol>
 *   <li>消费者拉取到死信消息后，解析原始 Topic 和消息体</li>
 *   <li>根据原始 Topic 路由到对应的 {@link DlqHandler} 处理器</li>
 *   <li>处理器执行补偿逻辑（如重投递、降级处理、写入审计表）</li>
 *   <li>补偿成功则 ACK，失败则记录 CRITICAL 告警日志</li>
 * </ol>
 *
 * <h2>启用条件</h2>
 * <p>需同时满足以下条件：
 * <ul>
 *   <li>RocketMQ 已配置（{@code rocketmq.name-server} 不为空）</li>
 *   <li>{@code apex.mq.dlq.enabled=true}（默认 true）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see MqConstant
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RocketMQAutoConfiguration.class)
@ConditionalOnProperty(name = "apex.mq.consumer.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "apex.mq.dlq.enabled", havingValue = "true", matchIfMissing = true)
public class DeadLetterQueueProcessor {

    /** 死信队列消费者组后缀 */
    private static final String DLQ_CONSUMER_GROUP_SUFFIX = "-dlq-consumer";

    /** 死信 Topic 前缀（RocketMQ 规范：%DLQ% + 消费组名） */
    private static final String DLQ_TOPIC_PREFIX = "%DLQ%";

    /** RocketMQ NameServer 地址 */
    @Value("${rocketmq.name-server:}")
    private String nameServer;

    /** DLQ 处理器是否已启动 */
    private volatile boolean started = false;

    /** 原始 Topic 到死信处理器的路由表 */
    private final Map<String, DlqHandler> handlerRegistry = new ConcurrentHashMap<>();

    /** RocketMQ 死信消费者实例 */
    private DefaultMQPushConsumer dlqConsumer;

    /**
     * 注册死信处理器。
     *
     * <p>各业务模块通过此方法注册其 Topic 的死信处理逻辑。
     * 在死信消息到达时，根据原始 Topic 路由到对应处理器。
     *
     * @param originalTopic 原始业务 Topic
     * @param handler       死信处理器
     */
    public void registerHandler(String originalTopic, DlqHandler handler) {
        handlerRegistry.put(originalTopic, handler);
        log.info(
                "注册 DLQ 处理器, topic: {}, handler: {}",
                originalTopic,
                handler.getClass().getSimpleName());
    }

    /**
     * 启动死信队列消费者。
     *
     * <p>在 Bean 初始化后自动启动，监听所有已注册消费组的死信 Topic。
     * 如果 NameServer 未配置或无注册处理器，则跳过启动。
     *
     * @throws MQClientException 如果 RocketMQ 客户端启动失败
     */
    @PostConstruct
    public void start() {
        if (nameServer == null || nameServer.isBlank()) {
            log.info("RocketMQ NameServer 未配置, DLQ 处理器不启动");
            return;
        }
        if (handlerRegistry.isEmpty()) {
            log.info("无已注册的 DLQ 处理器, DLQ 处理器暂不启动");
            return;
        }
        try {
            initAndStartConsumer();
        } catch (MQClientException e) {
            log.error("DLQ 消费者启动失败, 死信消息将无法自动处理", e);
        }
    }

    /**
     * 初始化并启动死信消费者。
     *
     * @throws MQClientException 如果消费者启动失败
     */
    private void initAndStartConsumer() throws MQClientException {
        String consumerGroup = "apex-dlq-processor" + DLQ_CONSUMER_GROUP_SUFFIX;
        dlqConsumer = new DefaultMQPushConsumer(consumerGroup);
        dlqConsumer.setNamesrvAddr(nameServer);

        // 订阅所有已注册消费组的死信 Topic
        for (String topic : handlerRegistry.keySet()) {
            String dlqTopic = buildDlqTopic(topic);
            try {
                dlqConsumer.subscribe(dlqTopic, "*");
                log.info("订阅死信 Topic: {}", dlqTopic);
            } catch (MQClientException e) {
                log.error("订阅死信 Topic 失败: {}", dlqTopic, e);
            }
        }

        dlqConsumer.registerMessageListener(new DlqMessageListener());
        dlqConsumer.start();
        started = true;
        log.info("DLQ 死信队列处理器已启动, 监听 {} 个 Topic", handlerRegistry.size());
    }

    /**
     * 构建死信 Topic 名称。
     *
     * <p>RocketMQ 死信 Topic 格式为 {@code %DLQ% + 消费组名}。
     * 本方法根据原始 Topic 推导对应的消费组死信 Topic。
     *
     * @param originalTopic 原始业务 Topic
     * @return 死信 Topic 名称
     */
    private String buildDlqTopic(String originalTopic) {
        return DLQ_TOPIC_PREFIX + originalTopic.replace("-topic", "-consumer-group");
    }

    /**
     * 处理单条死信消息。
     *
     * <p>根据消息的原始 Topic 路由到对应的处理器。如果未找到处理器，
     * 记录 CRITICAL 告警日志并返回成功（避免消息卡在 DLQ）。
     *
     * @param messageExt RocketMQ 消息体
     * @return 处理结果
     */
    ConsumeConcurrentlyStatus processDeadLetter(MessageExt messageExt) {
        String originalTopic = messageExt.getUserProperty("ORIGINAL_TOPIC");
        if (originalTopic == null) {
            originalTopic = messageExt.getTopic();
        }
        String msgId = messageExt.getMsgId();
        int reconsumeTimes = messageExt.getReconsumeTimes();
        log.warn(
                "[DLQ] 收到死信消息, msgId: {}, originalTopic: {}, reconsumeTimes: {}", msgId, originalTopic, reconsumeTimes);

        DlqHandler handler = handlerRegistry.get(originalTopic);
        if (handler == null) {
            log.error("[DLQ] 未找到 Topic {} 的死信处理器, msgId: {}, 消息将被丢弃", originalTopic, msgId);
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        try {
            boolean success = handler.handle(messageExt);
            if (success) {
                log.info("[DLQ] 死信消息处理成功, msgId: {}, topic: {}", msgId, originalTopic);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            } else {
                log.error("[DLQ] 死信消息处理失败, msgId: {}, topic: {}", msgId, originalTopic);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        } catch (Exception e) {
            log.error("[DLQ] 死信消息处理异常, msgId: {}, topic: {}", msgId, originalTopic, e);
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }

    /**
     * 关闭死信消费者。
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        if (dlqConsumer != null && started) {
            dlqConsumer.shutdown();
            log.info("DLQ 死信队列处理器已关闭");
        }
    }

    /**
     * 死信消息监听器内部实现。
     *
     * <p>将 RocketMQ 原生消息监听回调委托给外部处理器的内部桥接类。
     *
     * @author Apex Team
     */
    private class DlqMessageListener implements MessageListenerConcurrently {

        /**
         * 消费死信消息。
         *
         * @param msgs    消息列表
         * @param context 消费上下文
         * @return 消费状态
         */
        @Override
        public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
            for (MessageExt msg : msgs) {
                ConsumeConcurrentlyStatus status = processDeadLetter(msg);
                if (status != ConsumeConcurrentlyStatus.CONSUME_SUCCESS) {
                    return status;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
    }

    /**
     * 死信消息处理器接口。
     *
     * <p>各业务模块实现此接口，定义其 Topic 消费失败后的补偿逻辑。
     *
     * @author Apex Team
     */
    @FunctionalInterface
    public interface DlqHandler {

        /**
         * 处理死信消息。
         *
         * @param messageExt RocketMQ 原始消息体
         * @return true 表示处理成功（ACK），false 表示处理失败（重新投递）
         */
        boolean handle(MessageExt messageExt);
    }
}
