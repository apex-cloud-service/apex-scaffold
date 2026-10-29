package com.apex.scaffold.infrastructure.mq.listener;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import com.apex.scaffold.infrastructure.notification.NotificationService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 站内通知广播消费者（多实例 SSE 扇出）。
 *
 * <p>以 RocketMQ <b>广播模式</b>（{@link MessageModel#BROADCASTING}）订阅通知主题，
 * 集群中每个实例都会收到同一条消息。收到后仅调用
 * {@link NotificationService#publishLocal(NotificationService.NotificationMessage)}
 * 向本实例持有的 SSE 长连接订阅者推送，<b>绝不</b>再次发布到 MQ，从而避免消息回环。</p>
 *
 * <h2>消息格式</h2>
 * <pre>
 * {
 *   "messageId": "全局唯一ID（幂等键）",
 *   "recipient": "接收账户 ID 的字符串表示",
 *   "content":   "消息内容",
 *   "tenantId":  "租户ID（必填，来自内部可信发布方）"
 * }
 * </pre>
 *
 * <h2>消费确认说明</h2>
 * <p>本地推送完成后再返回，处理失败时抛出异常触发 RocketMQ 重消费。幂等性由
 * {@link MqConsumerService} 基于 Redis 保证。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 * @see NotificationService
 * @see MqConsumerService
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RocketMQTemplate.class)
@ConditionalOnBean(RocketMQTemplate.class)
@ConditionalOnProperty(name = "apex.mq.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        consumerGroup = MqConstant.CONSUMER_GROUP_NOTIFICATION,
        topic = MqConstant.TOPIC_NOTIFICATION,
        messageModel = MessageModel.BROADCASTING,
        maxReconsumeTimes = MqConstant.MAX_RECONSUME_TIMES)
@lombok.RequiredArgsConstructor
public class NotificationBroadcastListener implements RocketMQListener<Map<String, Object>> {

    /** MQ 消费者服务，用于幂等消费。 */
    private final MqConsumerService mqConsumerService;

    /** 通知推送服务，用于本地 SSE 扇出。 */
    private final NotificationService notificationService;

    /**
     * 接收并处理通知广播消息。
     *
     * <p>在租户上下文内幂等消费：仅向本实例 SSE 订阅者推送（{@code publishLocal}），
     * 不重复回环到 MQ。处理失败时抛出异常触发 RocketMQ 重消费。</p>
     *
     * @param message 消息体
     */
    @Override
    public void onMessage(Map<String, Object> message) {
        String messageId = message == null ? null : asString(message.get("messageId"));
        log.info("接收到通知广播消息, messageId: {}", messageId);

        String tenantId = message == null ? null : asString(message.get("tenantId"));
        String recipient = message == null ? null : asString(message.get("recipient"));
        if (tenantId == null || tenantId.isBlank() || recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("通知广播必须包含租户与接收账户");
        }
        String previousTenantId = TenantContext.setTenantId(tenantId);
        try {
            boolean processed = mqConsumerService.processMessage(
                    MqConstant.CONSUMER_GROUP_NOTIFICATION, messageId, message, ignored -> {
                        String content = asString(message.get("content"));
                        notificationService.publishLocal(
                                new NotificationService.NotificationMessage(tenantId, recipient, content));
                        return true;
                    });
            if (!processed) {
                throw new IllegalStateException("通知广播消费失败, messageId: " + messageId);
            }
        } finally {
            if (previousTenantId == null) {
                TenantContext.clear();
            } else {
                TenantContext.setTenantId(previousTenantId);
            }
        }
    }

    /**
     * 将消息字段安全转换为字符串。
     *
     * @param value 原始字段值
     * @return 字符串值，null 时返回 null
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
