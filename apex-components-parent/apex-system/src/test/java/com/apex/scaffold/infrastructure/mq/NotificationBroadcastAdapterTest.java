package com.apex.scaffold.infrastructure.mq;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.apex.scaffold.domain.gateway.RealtimeNotificationPort;
import com.apex.scaffold.infrastructure.notification.NotificationPublisher;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link NotificationBroadcastAdapter} 单元测试：校验委托、条件跳过与失败边界。
 */
@DisplayName("NotificationBroadcastAdapter 单元测试")
@ExtendWith(MockitoExtension.class)
class NotificationBroadcastAdapterTest {

    @Mock
    private NotificationPublisher publisher;

    /** 验证有效负载原样委托给发布器。 */
    @Test
    @DisplayName("有效负载原样委托给发布器")
    void delegatesValidPayload() {
        RealtimeNotificationPort port = new NotificationBroadcastAdapter(Optional.of(publisher));

        port.publish("tenant-1", "42", "您的积分已达到 100");

        verify(publisher).publish("tenant-1", "42", "您的积分已达到 100");
    }

    /** 验证发布器缺失或负载缺失时静默跳过。 */
    @Test
    @DisplayName("发布器缺失或负载缺失时静默跳过")
    void skipsWhenPublisherMissingOrPayloadInvalid() {
        RealtimeNotificationPort missing = new NotificationBroadcastAdapter(Optional.empty());
        RealtimeNotificationPort present = new NotificationBroadcastAdapter(Optional.of(publisher));

        assertThatCode(() -> {
                    missing.publish("tenant-1", "42", "内容");
                    present.publish(null, "42", "内容");
                    present.publish("tenant-1", " ", "内容");
                    present.publish("tenant-1", "42", null);
                })
                .doesNotThrowAnyException();

        verifyNoInteractions(publisher);
    }

    /** 验证发布器抛出异常时失败边界收敛，不向上游传播。 */
    @Test
    @DisplayName("发布失败时收敛异常不向上游传播")
    void swallowsPublishFailure() {
        doThrow(new IllegalStateException("mq down")).when(publisher).publish(any(), any(), any());
        RealtimeNotificationPort port = new NotificationBroadcastAdapter(Optional.of(publisher));

        assertThatCode(() -> port.publish("tenant-1", "42", "内容")).doesNotThrowAnyException();
    }
}
