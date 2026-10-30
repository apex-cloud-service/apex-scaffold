package com.apex.scaffold.infrastructure.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.service.MqProducerService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link NotificationPublisher} 单元测试：校验负载与发布主题。
 */
@DisplayName("NotificationPublisher 单元测试")
@ExtendWith(MockitoExtension.class)
class NotificationPublisherTest {

    @Mock
    private MqProducerService mqProducerService;

    private NotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new NotificationPublisher(mqProducerService);
    }

    /** 验证发布负载包含 messageId、租户、接收人与内容，主题正确。 */
    @Test
    @DisplayName("发布负载包含完整字段且主题正确")
    void publishesValidatedPayload() {
        String messageId = publisher.publish("tenant-1", "42", "您的审批已通过");

        assertThat(messageId).isNotBlank();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mqProducerService).sendSync(eq(MqConstant.TOPIC_NOTIFICATION), eq("notification"), captor.capture());
        Map<String, Object> payload = captor.getValue();
        assertThat(payload.get("messageId")).isEqualTo(messageId);
        assertThat(payload.get("tenantId")).isEqualTo("tenant-1");
        assertThat(payload.get("recipient")).isEqualTo("42");
        assertThat(payload.get("content")).isEqualTo("您的审批已通过");
    }

    /** 验证传入的幂等 messageId 被透传。 */
    @Test
    @DisplayName("传入 messageId 时透传")
    void passesThroughMessageId() {
        String id = publisher.publish("tenant-1", "42", "内容", "custom-id");
        assertThat(id).isEqualTo("custom-id");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(mqProducerService).sendSync(eq(MqConstant.TOPIC_NOTIFICATION), eq("notification"), captor.capture());
        assertThat(captor.getValue().get("messageId")).isEqualTo("custom-id");
    }

    /** 验证租户、接收人与内容缺失时拒绝发布。 */
    @Test
    @DisplayName("缺失范围或内容时拒绝发布")
    void rejectsMissingScopeOrContent() {
        assertThatThrownBy(() -> publisher.publish(null, "42", "内容"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户");
        assertThatThrownBy(() -> publisher.publish("tenant-1", " ", "内容"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("接收账户");
        assertThatThrownBy(() -> publisher.publish("tenant-1", "42", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("内容");
        org.mockito.Mockito.verifyNoInteractions(mqProducerService);
    }
}
