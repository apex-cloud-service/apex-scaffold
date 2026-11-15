package com.apex.scaffold.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.domain.event.DomainEventBus;
import com.apex.scaffold.domain.event.UserRegisteredEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@DisplayName("EnhancedDomainEventPublisherImpl 可靠发布测试")
class EnhancedDomainEventPublisherImplTest {

    private DomainEventBus domainEventBus;
    private ApplicationEventPublisher applicationEventPublisher;
    private EnhancedDomainEventPublisherImpl publisher;

    @BeforeEach
    void setUp() {
        domainEventBus = mock(DomainEventBus.class);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        publisher = new EnhancedDomainEventPublisherImpl(
                domainEventBus, applicationEventPublisher, mock(RocketMQTemplate.class), "apex-domain-event", true);
    }

    @Test
    @DisplayName("异步发布应等待领域事件总线完成")
    void shouldWaitForDomainEventBus() {
        UserRegisteredEvent event = new UserRegisteredEvent(1L, "user", "phone");
        AtomicBoolean completed = new AtomicBoolean();
        when(domainEventBus.publish(event)).thenReturn(Mono.fromRunnable(() -> completed.set(true)));

        StepVerifier.create(publisher.publishAsync(event)).verifyComplete();

        assertThat(completed).isTrue();
        verify(applicationEventPublisher).publishEvent(event);
        verify(domainEventBus).publish(event);
    }

    @Test
    @DisplayName("领域事件总线失败应传播错误")
    void shouldPropagateDomainEventBusFailure() {
        UserRegisteredEvent event = new UserRegisteredEvent(1L, "user", "phone");
        IllegalStateException failure = new IllegalStateException("event handler failure");
        when(domainEventBus.publish(event)).thenReturn(Mono.error(failure));

        StepVerifier.create(publisher.publishAsync(event))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
    }
}
