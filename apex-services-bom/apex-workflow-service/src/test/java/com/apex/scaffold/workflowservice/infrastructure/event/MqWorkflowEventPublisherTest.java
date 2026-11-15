package com.apex.scaffold.workflowservice.infrastructure.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.apex.scaffold.contract.event.WorkflowInstanceStartedEvent;
import java.time.LocalDateTime;
import org.apache.rocketmq.client.producer.SendCallback;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.spring.support.RocketMQHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import reactor.test.StepVerifier;

/**
 * {@link MqWorkflowEventPublisher} 单元测试.
 *
 * <p>验证生产发布器将工作流事件序列化后经 {@link RocketMQTemplate#asyncSend} 异步投递到
 * 约定主题，并以事件 ID 作为消息 KEYS；同时覆盖发送成功、发送失败与空事件三种路径
 * （迁移方案 §6.4 / 执行计划 Phase 3.4）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@DisplayName("MqWorkflowEventPublisher 单元测试")
class MqWorkflowEventPublisherTest {

    /** 契约约定的事件主题。 */
    private static final String TOPIC = "apex-workflow-events";

    private RocketMQTemplate rocketMQTemplate;
    private MqWorkflowEventPublisher publisher;

    @BeforeEach
    void setUp() {
        rocketMQTemplate = mock(RocketMQTemplate.class);
        publisher = new MqWorkflowEventPublisher(rocketMQTemplate, TOPIC);
    }

    @Test
    @DisplayName("publish - 成功投递事件并以事件ID作为消息KEYS")
    void shouldPublishSuccessfully() {
        WorkflowInstanceStartedEvent event =
                new WorkflowInstanceStartedEvent(1L, "leave_approval", "LEAVE-001", 10L, LocalDateTime.now());
        Message<?>[] captured = new Message<?>[1];
        doAnswer(invocation -> {
                    captured[0] = invocation.getArgument(1);
                    SendCallback callback = invocation.getArgument(2);
                    SendResult result = new SendResult();
                    result.setMsgId("msg-1");
                    callback.onSuccess(result);
                    return null;
                })
                .when(rocketMQTemplate)
                .asyncSend(eq(TOPIC), any(Message.class), any(SendCallback.class));

        StepVerifier.create(publisher.publish(event)).verifyComplete();

        verify(rocketMQTemplate).asyncSend(eq(TOPIC), any(Message.class), any(SendCallback.class));
        Message<?> message = captured[0];
        assertThat(message).isNotNull();
        assertThat(message.getHeaders().get(RocketMQHeaders.KEYS)).isEqualTo(event.getEventId());
        assertThat(message.getPayload()).isInstanceOf(String.class);
        assertThat((String) message.getPayload()).contains("leave_approval");
    }

    @Test
    @DisplayName("publish - 发送失败时传播错误信号")
    void shouldPropagateSendFailure() {
        WorkflowInstanceStartedEvent event =
                new WorkflowInstanceStartedEvent(1L, "leave_approval", "LEAVE-001", 10L, LocalDateTime.now());
        RuntimeException failure = new RuntimeException("broker unreachable");
        doAnswer(invocation -> {
                    SendCallback callback = invocation.getArgument(2);
                    callback.onException(failure);
                    return null;
                })
                .when(rocketMQTemplate)
                .asyncSend(eq(TOPIC), any(Message.class), any(SendCallback.class));

        StepVerifier.create(publisher.publish(event))
                .expectErrorSatisfies(error -> assertThat(error).isSameAs(failure))
                .verify();
    }

    @Test
    @DisplayName("publish - 事件为空时抛出 IllegalArgumentException")
    void shouldRejectNullEvent() {
        assertThatThrownBy(() -> publisher.publish(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
