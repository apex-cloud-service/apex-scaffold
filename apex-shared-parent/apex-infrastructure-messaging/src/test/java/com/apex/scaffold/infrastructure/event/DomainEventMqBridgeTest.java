package com.apex.scaffold.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import com.apex.scaffold.common.event.DistributedDomainEvent;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;

/**
 * {@link DomainEventMqBridge} 单元测试（D5 主数据分发传输段）。
 *
 * <p>覆盖：标记事件以 {@code topic:tag} 异步发送并携带事件 ID key、
 * 未标记事件不广播、发送提交异常不外溢。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@DisplayName("分布式事件 MQ 桥测试")
class DomainEventMqBridgeTest {

    private RocketMQTemplate rocketMQTemplate;

    private DomainEventMqBridge bridge;

    /** 标记事件夹具。 */
    static class MarkerEvent extends AbstractDomainEvent implements DistributedDomainEvent {

        MarkerEvent() {
            super("TENANT_CHANGED", null);
        }

        @Override
        public String getSourceId() {
            return "T001";
        }
    }

    /** 未标记事件夹具。 */
    static class PlainEvent extends AbstractDomainEvent {

        PlainEvent() {
            super("SOME_LOCAL_EVENT", null);
        }

        @Override
        public String getSourceId() {
            return "x-1";
        }
    }

    @BeforeEach
    void setUp() {
        rocketMQTemplate = mock(RocketMQTemplate.class);
        bridge = new DomainEventMqBridge(rocketMQTemplate, "apex-domain-event");
    }

    @Test
    @DisplayName("标记事件应按 topic:tag 异步发送并携带 eventId key")
    void shouldSendMarkedEventWithTopicTag() {
        MarkerEvent event = new MarkerEvent();

        bridge.onDomainEvent(event);

        org.mockito.ArgumentCaptor<Message<String>> captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate)
                .asyncSend(startsWith("apex-domain-event:TENANT_CHANGED"), captor.capture(), any(SendCallback.class));
        assertThat(captor.getValue().getHeaders().get(RocketMQHeaders.KEYS)).isEqualTo(event.getEventId());
    }

    @Test
    @DisplayName("未标记事件不跨服务广播")
    void shouldSkipUnmarkedEvent() {
        bridge.onDomainEvent(new PlainEvent());

        verify(rocketMQTemplate, never()).asyncSend(anyString(), any(Message.class), any(SendCallback.class));
    }

    @Test
    @DisplayName("发送提交异常只记录不外溢（旁路语义）")
    void shouldSwallowSubmitFailure() {
        org.mockito.Mockito.doThrow(new RuntimeException("broker down"))
                .when(rocketMQTemplate)
                .asyncSend(anyString(), any(Message.class), any(SendCallback.class));

        bridge.onDomainEvent(new MarkerEvent());

        verify(rocketMQTemplate).asyncSend(anyString(), any(Message.class), any(SendCallback.class));
    }
}
