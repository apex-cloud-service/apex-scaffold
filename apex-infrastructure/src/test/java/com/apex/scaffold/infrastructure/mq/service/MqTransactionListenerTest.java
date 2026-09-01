package com.apex.scaffold.infrastructure.mq.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@ExtendWith(MockitoExtension.class)
@DisplayName("MqTransactionListener 单元测试")
class MqTransactionListenerTest {

    @Mock
    private ObjectProvider<MqLocalTransactionHandler> handlerProvider;

    @Mock
    private MqLocalTransactionHandler handler;

    @Test
    @DisplayName("未配置本地事务处理器时应返回UNKNOWN")
    void shouldReturnUnknownWithoutHandler() {
        when(handlerProvider.getIfAvailable()).thenReturn(null);
        MqTransactionListener listener = new MqTransactionListener(handlerProvider);
        Message<String> message = MessageBuilder.withPayload("message").build();

        assertThat(listener.executeLocalTransaction(message, "argument"))
                .isEqualTo(RocketMQLocalTransactionState.UNKNOWN);
        assertThat(listener.checkLocalTransaction(message)).isEqualTo(RocketMQLocalTransactionState.UNKNOWN);
    }

    @Test
    @DisplayName("应委托本地事务处理器并透传事务状态")
    void shouldDelegateToHandler() {
        when(handlerProvider.getIfAvailable()).thenReturn(handler);
        when(handler.execute(any(Message.class), any())).thenReturn(RocketMQLocalTransactionState.COMMIT);
        when(handler.check(any(Message.class))).thenReturn(RocketMQLocalTransactionState.ROLLBACK);
        MqTransactionListener listener = new MqTransactionListener(handlerProvider);
        Message<String> message = MessageBuilder.withPayload("message").build();

        assertThat(listener.executeLocalTransaction(message, "argument"))
                .isEqualTo(RocketMQLocalTransactionState.COMMIT);
        assertThat(listener.checkLocalTransaction(message)).isEqualTo(RocketMQLocalTransactionState.ROLLBACK);

        verify(handler).execute(message, "argument");
        verify(handler).check(message);
    }

    @Test
    @DisplayName("应声明RocketMQ事务监听器")
    void shouldDeclareRocketMqTransactionListener() {
        assertThat(MqTransactionListener.class).hasAnnotation(RocketMQTransactionListener.class);
    }
}
