package com.apex.scaffold.workflowservice.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.apex.scaffold.contract.event.TaskAssignedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * {@link LocalWorkflowEventPublisher} 单元测试.
 *
 * <p>验证 basic/H2 环境下的本地兜底发布器不投递消息、直接返回完成信号，
 * 且对空事件保持与生产实现一致的参数校验语义。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@DisplayName("LocalWorkflowEventPublisher 单元测试")
class LocalWorkflowEventPublisherTest {

    private final LocalWorkflowEventPublisher publisher = new LocalWorkflowEventPublisher();

    @Test
    @DisplayName("publish - 本地兜底仅记录日志并正常完成")
    void shouldCompleteWithoutDelivery() {
        TaskAssignedEvent event = new TaskAssignedEvent(1L, 2L, "node_1", 20L, 10L);

        StepVerifier.create(publisher.publish(event)).verifyComplete();
    }

    @Test
    @DisplayName("publish - 事件为空时抛出 IllegalArgumentException")
    void shouldRejectNullEvent() {
        assertThatThrownBy(() -> publisher.publish(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
