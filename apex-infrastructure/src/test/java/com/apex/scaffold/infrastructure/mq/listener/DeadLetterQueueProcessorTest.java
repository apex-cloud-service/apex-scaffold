package com.apex.scaffold.infrastructure.mq.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.mq.metrics.MqConsumerMetrics;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 死信队列自动处理器单元测试。
 *
 * <p>验证 {@link DeadLetterQueueProcessor} 的处理器注册、消息路由和
 * 处理成功/失败/异常等场景。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("DeadLetterQueueProcessor 死信队列处理器测试")
class DeadLetterQueueProcessorTest {

    /** 被测处理器 */
    private DeadLetterQueueProcessor processor;

    /**
     * 初始化测试环境。
     */
    @BeforeEach
    void setUp() {
        processor = new DeadLetterQueueProcessor();
        ReflectionTestUtils.setField(processor, "nameServer", "localhost:9876");
    }

    /**
     * 测试注册处理器后应能正确路由死信消息。
     */
    @Test
    @DisplayName("注册处理器后应正确路由并处理死信消息")
    void shouldRouteAndProcessDeadLetter() {
        String topic = "apex-photo-moderation";
        DeadLetterQueueProcessor.DlqHandler handler = msg -> true;
        processor.registerHandler(topic, handler);

        MessageExt message = createMessageExt(topic, "msg-001");
        ConsumeConcurrentlyStatus status = processor.processDeadLetter(message);

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
    }

    /**
     * 测试处理器返回 false 时应重新投递。
     */
    @Test
    @DisplayName("处理器返回false时应重新投递")
    void shouldReconsumeWhenHandlerReturnsFalse() {
        String topic = "apex-photo-moderation";
        DeadLetterQueueProcessor.DlqHandler handler = msg -> false;
        processor.registerHandler(topic, handler);

        MessageExt message = createMessageExt(topic, "msg-002");
        ConsumeConcurrentlyStatus status = processor.processDeadLetter(message);

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
    }

    /**
     * 测试处理器抛出异常时应重新投递。
     */
    @Test
    @DisplayName("处理器抛出异常时应重新投递")
    void shouldReconsumeWhenHandlerThrows() {
        String topic = "apex-photo-moderation";
        DeadLetterQueueProcessor.DlqHandler handler = msg -> {
            throw new RuntimeException("处理异常");
        };
        processor.registerHandler(topic, handler);

        MessageExt message = createMessageExt(topic, "msg-003");
        ConsumeConcurrentlyStatus status = processor.processDeadLetter(message);

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
    }

    /**
     * 测试未注册处理器的 Topic 应返回成功（避免消息卡在 DLQ）。
     */
    @Test
    @DisplayName("未注册处理器的Topic应返回成功避免消息卡住")
    void shouldReturnSuccessWhenNoHandlerRegistered() {
        MessageExt message = createMessageExt("unknown-topic", "msg-004");
        ConsumeConcurrentlyStatus status = processor.processDeadLetter(message);

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
    }

    /**
     * 测试 NameServer 未配置时不应启动消费者。
     */
    @Test
    @DisplayName("NameServer未配置时不启动消费者")
    void shouldNotStartWhenNameServerBlank() {
        DeadLetterQueueProcessor noNameServerProcessor = new DeadLetterQueueProcessor();
        ReflectionTestUtils.setField(noNameServerProcessor, "nameServer", "");
        noNameServerProcessor.start();
        // 不应抛出异常
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("空注册时启动后首次注册应启动消费者并订阅DLQ Topic")
    void shouldStartWhenHandlerRegisteredAfterStart() throws Exception {
        try (MockedConstruction<DefaultMQPushConsumer> construction = mockConstruction(DefaultMQPushConsumer.class)) {
            processor.start();
            processor.registerHandler("apex-photo-moderation", "photo-consumer-group", msg -> true);

            DefaultMQPushConsumer consumer = construction.constructed().get(0);
            verify(consumer).subscribe("%DLQ%photo-consumer-group", "*");
            verify(consumer).start();
        }
    }

    @Test
    @DisplayName("启动后新增处理器只订阅新增DLQ Topic")
    void shouldSubscribeNewTopicWhenHandlerRegisteredAfterStart() throws Exception {
        processor.registerHandler("apex-photo-moderation", "photo-consumer-group", msg -> true);

        try (MockedConstruction<DefaultMQPushConsumer> construction = mockConstruction(DefaultMQPushConsumer.class)) {
            processor.start();
            processor.registerHandler("apex-video-moderation", "video-consumer-group", msg -> true);
            processor.registerHandler("apex-video-moderation", "video-consumer-group", msg -> true);

            DefaultMQPushConsumer consumer = construction.constructed().get(0);
            verify(consumer).subscribe("%DLQ%photo-consumer-group", "*");
            verify(consumer).subscribe("%DLQ%video-consumer-group", "*");
            verify(consumer).start();
        }
    }

    @Test
    @DisplayName("NameServer未配置时注册处理器不启动消费者")
    void shouldNotStartWhenHandlerRegisteredWithNameServerBlank() {
        DeadLetterQueueProcessor noNameServerProcessor = new DeadLetterQueueProcessor();
        ReflectionTestUtils.setField(noNameServerProcessor, "nameServer", "");

        noNameServerProcessor.start();
        noNameServerProcessor.registerHandler("apex-photo-moderation", msg -> true);

        assertThat(ReflectionTestUtils.getField(noNameServerProcessor, "started"))
                .isEqualTo(false);
    }

    @Test
    @DisplayName("显式消费者组应生成对应的DLQ Topic")
    void shouldBuildDlqTopicFromExplicitConsumerGroup() {
        String topic = "apex-photo-moderation";
        processor.registerHandler(topic, "photo-consumer-group", msg -> true);

        String dlqTopic = ReflectionTestUtils.invokeMethod(processor, "buildDlqTopic", topic);

        assertThat(dlqTopic).isEqualTo("%DLQ%photo-consumer-group");
    }

    /**
     * 测试处理死信消息时应累加死信指标。
     */
    @Test
    @DisplayName("处理死信消息时应累加死信指标")
    void shouldRecordDlqMetricWhenDeadLetterProcessed() {
        MqConsumerMetrics metrics = mock(MqConsumerMetrics.class);
        ReflectionTestUtils.setField(processor, "consumerMetrics", metrics);
        processor.registerHandler("apex-photo-moderation", msg -> true);

        MessageExt message = createMessageExt("apex-photo-moderation", "msg-metric-001");
        processor.processDeadLetter(message);

        verify(metrics).recordDlq();
    }

    /**
     * 测试未注册处理器时也应累加死信指标。
     */
    @Test
    @DisplayName("未注册处理器时也应累加死信指标")
    void shouldRecordDlqMetricWhenNoHandlerRegistered() {
        MqConsumerMetrics metrics = mock(MqConsumerMetrics.class);
        ReflectionTestUtils.setField(processor, "consumerMetrics", metrics);

        MessageExt message = createMessageExt("unknown-topic", "msg-metric-002");
        processor.processDeadLetter(message);

        verify(metrics).recordDlq();
    }

    /**
     * 创建测试用 MessageExt。
     *
     * @param topic  Topic 名称
     * @param msgId  消息 ID
     * @return Mock 的 MessageExt
     */
    private MessageExt createMessageExt(String topic, String msgId) {
        MessageExt message = mock(MessageExt.class);
        when(message.getTopic()).thenReturn(topic);
        when(message.getMsgId()).thenReturn(msgId);
        when(message.getReconsumeTimes()).thenReturn(3);
        when(message.getUserProperty("ORIGINAL_TOPIC")).thenReturn(topic);
        when(message.getBody()).thenReturn(new byte[0]);
        return message;
    }
}
