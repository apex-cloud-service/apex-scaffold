package com.apex.scaffold.infrastructure.mq.service;

import com.apex.scaffold.infrastructure.event.DomainEventOutboxStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * 基于 Outbox 的 RocketMQ 本地事务消息处理器。
 *
 * <p>与 {@link com.apex.scaffold.infrastructure.outbox.TransactionalOutboxExecutor} 配合：
 * 业务方在同一数据库事务内写入业务记录与 Outbox 事件，随后以事务消息发送（事件 ID 作为
 * 消息 ID）。RocketMQ 触发本地事务或回查时，本处理器按消息 ID 查询 Outbox 记录：</p>
 * <ul>
 *   <li>记录存在——本地事务已提交，返回 {@code COMMIT}；</li>
 *   <li>记录不存在——本地事务未生效，返回 {@code ROLLBACK}；</li>
 *   <li>无法解析消息 ID——状态未知，返回 {@code UNKNOWN}，交由代理稍后回查。</li>
 * </ul>
 *
 * <p>消息 ID 解析顺序：优先读取 Header {@code apex-message-id}（由
 * {@link MqProducerService} 发送事务消息时写入），缺失或空白时回退到 RocketMQ 的
 * {@code KEYS} Header。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.4.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "apex.features", name = "message-queue", havingValue = "true", matchIfMissing = true)
@ConditionalOnClass(RocketMQLocalTransactionState.class)
@ConditionalOnBean(DomainEventOutboxStore.class)
public class DefaultMqLocalTransactionHandler implements MqLocalTransactionHandler {

    /** 生产者写入的消息 ID Header。 */
    private static final String MESSAGE_ID_HEADER = "apex-message-id";

    /** RocketMQ 保留的 key 集合 Header，作为消息 ID 回退来源。 */
    private static final String KEYS_HEADER = "KEYS";

    /** 领域事件 Outbox 存储，用于回查本地事务是否已提交。 */
    private final DomainEventOutboxStore outboxStore;

    /**
     * 执行本地事务。
     *
     * <p>业务与 Outbox 写入已在发送事务消息前完成，此处直接按 Outbox 记录判定状态，
     * 保证半消息提交与否与本地事务结果一致。</p>
     *
     * @param message  RocketMQ 半消息
     * @param argument 事务参数（当前实现不依赖）
     * @return 事务状态
     */
    @Override
    public RocketMQLocalTransactionState execute(Message<?> message, Object argument) {
        return resolve(message);
    }

    /**
     * 回查本地事务状态。
     *
     * @param message RocketMQ 半消息
     * @return 事务状态
     */
    @Override
    public RocketMQLocalTransactionState check(Message<?> message) {
        return resolve(message);
    }

    /**
     * 依据 Outbox 记录解析事务状态。
     *
     * @param message RocketMQ 半消息
     * @return {@code COMMIT}/{@code ROLLBACK}/{@code UNKNOWN}
     */
    private RocketMQLocalTransactionState resolve(Message<?> message) {
        String messageId = resolveMessageId(message);
        if (messageId == null) {
            log.warn("事务消息缺少可解析的消息 ID，返回 UNKNOWN 等待回查");
            return RocketMQLocalTransactionState.UNKNOWN;
        }
        boolean committed = outboxStore.existsByEventId(messageId);
        if (committed) {
            return RocketMQLocalTransactionState.COMMIT;
        }
        return RocketMQLocalTransactionState.ROLLBACK;
    }

    /**
     * 解析消息 ID，优先 {@code apex-message-id}，回退 {@code KEYS}。
     *
     * @param message RocketMQ 半消息
     * @return 非空消息 ID；不可解析时返回 {@code null}
     */
    private String resolveMessageId(Message<?> message) {
        if (message == null) {
            return null;
        }
        String fromHeader = headerAsString(message, MESSAGE_ID_HEADER);
        if (fromHeader != null) {
            return fromHeader;
        }
        return headerAsString(message, KEYS_HEADER);
    }

    /**
     * 读取指定 Header 并归一化为非空字符串。
     *
     * @param message RocketMQ 半消息
     * @param name    Header 名称
     * @return 去除空白后的值；缺失或空白时返回 {@code null}
     */
    private String headerAsString(Message<?> message, String name) {
        Object value = message.getHeaders().get(name);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
