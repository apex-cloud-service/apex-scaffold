package com.apex.scaffold.infrastructure.mq;

import com.apex.scaffold.domain.gateway.RealtimeNotificationPort;
import com.apex.scaffold.infrastructure.notification.NotificationPublisher;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 站内通知实时推送适配器：把领域端口委托给 infrastructure-messaging 的 MQ 生产者。
 *
 * <p>message-queue 未启用或 RocketMQ 缺失时发布器不存在，本适配器静默跳过；
 * 发布失败仅记录日志，不向上游传播（实时推送不影响已完成的落库结果）。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
public class NotificationBroadcastAdapter implements RealtimeNotificationPort {

    /** infrastructure-messaging 的通知发布器（可选）：未启用 message-queue 时不存在。 */
    private final NotificationPublisher publisher;

    /**
     * 创建适配器，发布器可选注入。
     *
     * @param publisher 通知发布器，可选
     */
    public NotificationBroadcastAdapter(Optional<NotificationPublisher> publisher) {
        this.publisher = publisher.orElse(null);
    }

    /**
     * 发布一条定向实时通知；发布器缺失、范围缺失或发送失败时静默处理。
     *
     * @param tenantId 可信租户 ID
     * @param recipient 接收账户 ID 的字符串表示
     * @param content 消息内容
     */
    @Override
    public void publish(String tenantId, String recipient, String content) {
        if (publisher == null
                || tenantId == null
                || tenantId.isBlank()
                || recipient == null
                || recipient.isBlank()
                || content == null
                || content.isBlank()) {
            return;
        }
        try {
            publisher.publish(tenantId, recipient, content);
        } catch (Exception e) {
            log.error("通知实时推送失败（不影响落库）: recipient={}", recipient, e);
        }
    }
}
