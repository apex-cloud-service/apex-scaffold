package com.apex.scaffold.workflowservice.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.contract.event.TaskAssignedEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import reactor.test.StepVerifier;

/**
 * {@link LocalWorkflowEventPublisher} 单元测试.
 *
 * <p>验证本地通道不是空操作：事件必须真的进入进程内事件总线，由本地订阅者收到；
 * 同时保持对空事件的参数校验语义。
 *
 * @author Apex Team
 * @version 1.1.0
 * @since 1.3.1
 */
@DisplayName("LocalWorkflowEventPublisher 单元测试")
class LocalWorkflowEventPublisherTest {

    /** 记录被派发到本地总线的事件。 */
    private final List<Object> published = new ArrayList<>();

    private final ApplicationEventPublisher bus = published::add;

    private final LocalWorkflowEventPublisher publisher = new LocalWorkflowEventPublisher(bus);

    @Test
    @DisplayName("publish - 事件应实际派发到本地事件总线")
    void shouldDeliverToLocalBus() {
        TaskAssignedEvent event = new TaskAssignedEvent(1L, 2L, "node_1", 20L, 10L);

        StepVerifier.create(publisher.publish(event)).verifyComplete();

        assertThat(published).containsExactly(event);
    }

    @Test
    @DisplayName("publish - 派发失败应传播错误而不是静默完成")
    void shouldPropagatePublishFailure() {
        ApplicationEventPublisher failing = event -> {
            throw new IllegalStateException("listener failed");
        };
        LocalWorkflowEventPublisher failingPublisher = new LocalWorkflowEventPublisher(failing);
        TaskAssignedEvent event = new TaskAssignedEvent(1L, 2L, "node_1", 20L, 10L);

        StepVerifier.create(failingPublisher.publish(event))
                .expectError(IllegalStateException.class)
                .verify();
    }

    @Test
    @DisplayName("publish - 事件为空时抛出 IllegalArgumentException")
    void shouldRejectNullEvent() {
        assertThatThrownBy(() -> publisher.publish(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
