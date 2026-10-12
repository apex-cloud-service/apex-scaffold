package com.apex.scaffold.infrastructure.mq.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import com.apex.scaffold.infrastructure.notification.NotificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link NotificationBroadcastListener} 的单元测试类。
 *
 * <p>覆盖多实例扇出：监听器以广播模式订阅通知主题，收到广播后仅向本实例
 * SSE 订阅者推送（{@code publishLocal}），绝不回环再次发布到 MQ。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationBroadcastListener 单元测试")
class NotificationBroadcastListenerTest {

    @Mock
    private MqConsumerService mqConsumerService;

    @Mock
    private NotificationService notificationService;

    private NotificationBroadcastListener listener;

    @BeforeEach
    void setUp() {
        listener = new NotificationBroadcastListener(mqConsumerService, notificationService);
    }

    @Test
    @DisplayName("应声明广播消费模式并订阅通知主题")
    void shouldDeclareBroadcastingConsumeMode() {
        RocketMQMessageListener annotation =
                NotificationBroadcastListener.class.getAnnotation(RocketMQMessageListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.messageModel()).isEqualTo(MessageModel.BROADCASTING);
        assertThat(annotation.topic()).isEqualTo(MqConstant.TOPIC_NOTIFICATION);
        assertThat(annotation.consumerGroup()).isEqualTo(MqConstant.CONSUMER_GROUP_NOTIFICATION);
    }

    @Test
    @DisplayName("收到广播消息应仅推送本地订阅者，不重复回环到MQ")
    void shouldFanOutLocallyWithoutLoopback() throws Exception {
        Map<String, Object> message = message("user-1", "你有新的通知");
        when(mqConsumerService.processMessage(eq("msg-1"), eq(message), any())).thenAnswer(invocation -> {
            MqConsumerService.MessageHandler<Map<String, Object>> handler = invocation.getArgument(2);
            return handler.handle(message);
        });

        listener.onMessage(message);

        ArgumentCaptor<NotificationService.NotificationMessage> captor =
                ArgumentCaptor.forClass(NotificationService.NotificationMessage.class);
        verify(notificationService).publishLocal(captor.capture());
        assertThat(captor.getValue().recipient()).isEqualTo("user-1");
        assertThat(captor.getValue().content()).isEqualTo("你有新的通知");

        verify(notificationService, never()).publish(any(NotificationService.NotificationMessage.class));
        verify(notificationService, never()).publish(any(String.class), any(String.class));
    }

    @Test
    @DisplayName("幂等处理失败时应抛出异常触发重消费")
    void shouldThrowWhenIdempotentProcessingFails() {
        Map<String, Object> message = message("user-1", "hello");
        when(mqConsumerService.processMessage(eq("msg-1"), eq(message), any())).thenReturn(false);

        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("msg-1");

        verify(notificationService, never()).publishLocal(any(NotificationService.NotificationMessage.class));
    }

    private Map<String, Object> message(String recipient, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", "msg-1");
        message.put("recipient", recipient);
        message.put("content", content);
        return message;
    }
}
