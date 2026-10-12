package com.apex.scaffold.infrastructure.mq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.event.DomainEventOutboxStore;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * {@link DefaultMqLocalTransactionHandler} 回查决策行为测试。
 *
 * <p>事务消息回查时按 {@code apex-message-id}（回退 {@code KEYS}）解析 Outbox 事件 ID，
 * 记录存在则 COMMIT，不存在则 ROLLBACK，无法解析则 UNKNOWN。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.4.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultMqLocalTransactionHandler 回查决策")
class DefaultMqLocalTransactionHandlerTest {

    @Mock
    private DomainEventOutboxStore outboxStore;

    private DefaultMqLocalTransactionHandler newHandler() {
        return new DefaultMqLocalTransactionHandler(outboxStore);
    }

    @Test
    @DisplayName("Outbox 中存在事件时应提交事务")
    void shouldCommitWhenOutboxRecordExists() {
        String messageId = "evt-commit";
        when(outboxStore.existsByEventId(messageId)).thenReturn(true);
        Message<?> message = MessageBuilder.withPayload("payload")
                .setHeader("apex-message-id", messageId)
                .build();

        assertThat(newHandler().check(message))
                .isEqualTo(RocketMQLocalTransactionState.COMMIT);
        verify(outboxStore).existsByEventId(messageId);
    }

    @Test
    @DisplayName("Outbox 中不存在事件时应回滚事务")
    void shouldRollBackWhenOutboxRecordMissing() {
        String messageId = "evt-missing";
        when(outboxStore.existsByEventId(messageId)).thenReturn(false);
        Message<?> message = MessageBuilder.withPayload("payload")
                .setHeader("apex-message-id", messageId)
                .build();

        assertThat(newHandler().check(message))
                .isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
        verify(outboxStore).existsByEventId(messageId);
    }

    @Test
    @DisplayName("执行本地事务时按Outbox记录返回提交")
    void shouldCommitOnExecuteWhenOutboxRecordExists() {
        String messageId = "evt-execute";
        when(outboxStore.existsByEventId(messageId)).thenReturn(true);
        Message<?> message = MessageBuilder.withPayload("payload")
                .setHeader("apex-message-id", messageId)
                .build();

        assertThat(newHandler().execute(message, "argument"))
                .isEqualTo(RocketMQLocalTransactionState.COMMIT);
    }

    @Test
    @DisplayName("缺失消息ID且无KEYS回退时应返回UNKNOWN")
    void shouldReturnUnknownWhenMessageIdUnresolvable() {
        Message<?> message = MessageBuilder.withPayload("payload").build();

        assertThat(newHandler().check(message))
                .isEqualTo(RocketMQLocalTransactionState.UNKNOWN);
    }

    @Test
    @DisplayName("报错消息解析空消息ID时应返回UNKNOWN")
    void shouldReturnUnknownWhenMessageIdBlank() {
        Message<?> message = MessageBuilder.withPayload("payload")
                .setHeader("apex-message-id", "  ")
                .build();

        assertThat(newHandler().check(message))
                .isEqualTo(RocketMQLocalTransactionState.UNKNOWN);
    }

    @Test
    @DisplayName("应回退到KEYS头解析消息ID")
    void shouldFallBackToKeysHeader() {
        String messageId = "evt-keys";
        when(outboxStore.existsByEventId(messageId)).thenReturn(false);
        Message<?> message = MessageBuilder.withPayload("payload")
                .setHeader("KEYS", messageId)
                .build();

        assertThat(newHandler().check(message))
                .isEqualTo(RocketMQLocalTransactionState.ROLLBACK);
        verify(outboxStore).existsByEventId(messageId);
    }
}