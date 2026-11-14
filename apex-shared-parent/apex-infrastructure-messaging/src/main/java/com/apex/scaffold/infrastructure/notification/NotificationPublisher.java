package com.apex.scaffold.infrastructure.notification;

import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.service.MqProducerService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 站内通知 MQ 生产者：把业务通知发布到 {@code apex-notification-topic}，
 * 由各实例的 {@code NotificationBroadcastListener} 广播消费并本地 SSE 扇出。
 *
 * <p>这是通知事件链路的唯一生产入口；直接调用 {@code NotificationService.publish}
 * 只会影响本实例订阅者。负载必须包含租户、接收账户与非空内容，messageId 缺省时生成 UUID。</p>
 *
 * @author Apex Team
 * @since 2.2.0
 * @see NotificationBroadcastListener
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnClass(org.apache.rocketmq.spring.core.RocketMQTemplate.class)
@ConditionalOnBean(org.apache.rocketmq.spring.core.RocketMQTemplate.class)
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
public class NotificationPublisher {

    private final MqProducerService mqProducerService;

    /**
     * 发布一条站内通知。
     *
     * @param tenantId 可信租户 ID
     * @param recipient 接收账户 ID
     * @param content 消息内容（非空）
     * @return messageId（调用方传入或自动生成）
     */
    public String publish(String tenantId, String recipient, String content) {
        return publish(tenantId, recipient, content, null);
    }

    /**
     * 发布一条站内通知，允许调用方指定幂等 messageId。
     *
     * @param tenantId 可信租户 ID
     * @param recipient 接收账户 ID
     * @param content 消息内容（非空）
     * @param messageId 幂等 ID，为空时自动生成
     * @return messageId
     */
    public String publish(String tenantId, String recipient, String content, String messageId) {
        requireScope(tenantId, "租户");
        requireScope(recipient, "接收账户");
        requireScope(content, "内容");
        String id = messageId == null || messageId.isBlank() ? UUID.randomUUID().toString() : messageId;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messageId", id);
        payload.put("tenantId", tenantId);
        payload.put("recipient", recipient);
        payload.put("content", content);
        mqProducerService.sendSync(MqConstant.TOPIC_NOTIFICATION, "notification", payload);
        log.info("通知已发布: messageId={}, tenantId={}, recipient={}", id, tenantId, recipient);
        return id;
    }

    /** 拒绝缺失范围。 */
    private static void requireScope(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("通知" + name + "不能为空");
        }
    }
}
