package com.apex.scaffold.infrastructure.mq.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import com.apex.scaffold.infrastructure.notification.NotificationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 通知广播消费者隔离与本地扇出测试。
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

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void shouldDeclareBroadcastingConsumeMode() {
        RocketMQMessageListener annotation =
                NotificationBroadcastListener.class.getAnnotation(RocketMQMessageListener.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.messageModel()).isEqualTo(MessageModel.BROADCASTING);
        assertThat(annotation.topic()).isEqualTo(MqConstant.TOPIC_NOTIFICATION);
        assertThat(annotation.consumerGroup()).isEqualTo(MqConstant.CONSUMER_GROUP_NOTIFICATION);
    }

    @Test
    void shouldFanOutLocallyWithoutLoopback() throws Exception {
        Map<String, Object> message = message("42", "你有新的通知");
        TenantContext.setTenantId("previous-tenant");
        when(mqConsumerService.processMessage(
                        eq(MqConstant.CONSUMER_GROUP_NOTIFICATION), eq("msg-1"), eq(message), any()))
                .thenAnswer(invocation -> {
                    assertThat(TenantContext.currentTenantId()).contains("tenant-a");
                    MqConsumerService.MessageHandler<Map<String, Object>> handler = invocation.getArgument(3);
                    return handler.handle(message);
                });
        listener.onMessage(message);
        ArgumentCaptor<NotificationService.NotificationMessage> captor =
                ArgumentCaptor.forClass(NotificationService.NotificationMessage.class);
        verify(notificationService).publishLocal(captor.capture());
        assertThat(captor.getValue().recipient()).isEqualTo("42");
        assertThat(captor.getValue().content()).isEqualTo("你有新的通知");
        assertThat(TenantContext.currentTenantId()).contains("previous-tenant");
        verify(notificationService, never()).publish(any(NotificationService.NotificationMessage.class));
        verify(notificationService, never()).publish(any(String.class), any(String.class));
    }

    @Test
    void shouldThrowWhenIdempotentProcessingFails() {
        Map<String, Object> message = message("42", "hello");
        when(mqConsumerService.processMessage(
                        eq(MqConstant.CONSUMER_GROUP_NOTIFICATION), eq("msg-1"), eq(message), any()))
                .thenReturn(false);
        assertThatThrownBy(() -> listener.onMessage(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("msg-1");
        verify(notificationService, never()).publishLocal(any(NotificationService.NotificationMessage.class));
        assertThat(TenantContext.currentTenantId()).isEmpty();
    }

    @Test
    void shouldRejectMissingTenantRatherThanReuseConsumerThreadTenant() {
        TenantContext.setTenantId("stale-tenant");
        Map<String, Object> message = message("42", "hello");
        message.remove("tenantId");
        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mqConsumerService, notificationService);
        assertThat(TenantContext.currentTenantId()).contains("stale-tenant");
    }

    @Test
    void shouldRejectMissingRecipientBeforeConsumption() {
        Map<String, Object> message = message(null, "hello");
        assertThatThrownBy(() -> listener.onMessage(message)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mqConsumerService, notificationService);
    }

    private Map<String, Object> message(String recipient, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("messageId", "msg-1");
        message.put("recipient", recipient);
        message.put("content", content);
        message.put("tenantId", "tenant-a");
        return message;
    }
}
