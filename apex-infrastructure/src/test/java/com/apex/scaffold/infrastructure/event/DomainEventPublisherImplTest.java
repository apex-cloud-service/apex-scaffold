package com.apex.scaffold.infrastructure.event;

import static org.mockito.Mockito.*;

import com.apex.scaffold.common.event.AbstractDomainEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;

/**
 * 领域事件发布器单元测试类
 *
 * <p>测试 DomainEventPublisherImpl 的发布方法，验证领域事件先可靠写入 Outbox。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class DomainEventPublisherImplTest {

    @Mock
    private DomainEventOutboxStore outboxStore;

    @InjectMocks
    private DomainEventPublisherImpl domainEventPublisher;

    @Test
    @DisplayName("发布领域事件 - 成功场景")
    void publish_Success() {
        AbstractDomainEvent event = mock(AbstractDomainEvent.class);

        domainEventPublisher.publish(event).as(StepVerifier::create).verifyComplete();

        verify(outboxStore).append(event);
    }

    @Test
    @DisplayName("发布领域事件 - 多次发布")
    void publish_MultipleEvents_Success() {
        AbstractDomainEvent event1 = mock(AbstractDomainEvent.class);
        AbstractDomainEvent event2 = mock(AbstractDomainEvent.class);

        domainEventPublisher.publish(event1).block();
        domainEventPublisher.publish(event2).block();

        verify(outboxStore).append(event1);
        verify(outboxStore).append(event2);
    }

    @Test
    @DisplayName("发布领域事件 - eventId不为空")
    void publish_EventHasEventId() {
        AbstractDomainEvent event = mock(AbstractDomainEvent.class);
        when(event.getEventId()).thenReturn("test-event-id");

        domainEventPublisher.publish(event).as(StepVerifier::create).verifyComplete();

        verify(outboxStore).append(event);
        verify(event).getEventId();
    }
}
