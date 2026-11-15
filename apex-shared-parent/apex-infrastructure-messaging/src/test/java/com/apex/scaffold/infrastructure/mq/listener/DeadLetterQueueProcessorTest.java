package com.apex.scaffold.infrastructure.mq.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.infrastructure.mq.metrics.MqConsumerMetrics;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.AfterEach;
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

    /** 生产默认的死信消费者组名（{@code apex.mq.dlq.consumer-group} 缺省值）。 */
    private static final String DEFAULT_DLQ_CONSUMER_GROUP = "apex-dlq-processor-dlq-consumer";

    /** 被测处理器 */
    private DeadLetterQueueProcessor processor;

    /**
     * 初始化测试环境。
     */
    @BeforeEach
    void setUp() {
        processor = new DeadLetterQueueProcessor("localhost:9876", DEFAULT_DLQ_CONSUMER_GROUP, null);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("死信处理应安装消息租户并在成功、失败和异常后恢复旧租户")
    void shouldRestoreTenantForEveryHandlerOutcome() {
        for (int outcome = 0; outcome < 3; outcome++) {
            int handlerOutcome = outcome;
            TenantContext.setTenantId("previous-tenant");
            processor.registerHandler("tenant-topic", msg -> {
                assertThat(TenantContext.currentTenantId()).contains("tenant-42");
                if (handlerOutcome == 2) {
                    throw new IllegalStateException("处理失败");
                }
                return handlerOutcome == 0;
            });
            ConsumeConcurrentlyStatus status =
                    processor.processDeadLetter(createMessageExt("tenant-topic", "tenant-event"));
            assertThat(status)
                    .isEqualTo(
                            outcome == 0
                                    ? ConsumeConcurrentlyStatus.CONSUME_SUCCESS
                                    : ConsumeConcurrentlyStatus.RECONSUME_LATER);
            assertThat(TenantContext.currentTenantId()).contains("previous-tenant");
        }
    }

    @Test
    @DisplayName("连续不同租户死信不得泄漏线程上下文")
    void shouldClearTenantBetweenMessages() {
        TenantContext.clear();
        processor.registerHandler("tenant-topic", msg -> {
            assertThat(TenantContext.currentTenantId()).contains(msg.getUserProperty("tenantId"));
            return true;
        });
        for (String tenant : new String[] {"tenant-a", "tenant-b"}) {
            MessageExt message = createMessageExt("tenant-topic", "event-" + tenant);
            when(message.getUserProperty("tenantId")).thenReturn(tenant);
            assertThat(processor.processDeadLetter(message)).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
            assertThat(TenantContext.currentTenantId()).isEmpty();
        }
    }

    @Test
    @DisplayName("缺失或空白租户不得使用线程旧租户或进入业务处理器")
    void shouldRejectMissingTenantWithoutInvokingHandler() {
        DeadLetterQueueProcessor.DlqHandler handler = mock(DeadLetterQueueProcessor.DlqHandler.class);
        processor.registerHandler("tenant-topic", handler);
        TenantContext.setTenantId("previous-tenant");
        for (String tenant : new String[] {null, "", "  "}) {
            MessageExt message = createMessageExt("tenant-topic", "invalid-tenant");
            when(message.getUserProperty("tenantId")).thenReturn(tenant);
            assertThat(processor.processDeadLetter(message)).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
            assertThat(TenantContext.currentTenantId()).contains("previous-tenant");
        }
        verifyNoInteractions(handler);
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
     * 测试未注册处理器的 Topic 不得被成功确认。
     */
    @Test
    @DisplayName("未注册处理器的Topic应请求稍后重投")
    void shouldReconsumeWhenNoHandlerRegistered() {
        MessageExt message = createMessageExt("unknown-topic", "msg-004");
        ConsumeConcurrentlyStatus status = processor.processDeadLetter(message);

        assertThat(status).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
    }

    @Test
    @DisplayName("死信携带重试前Topic属性时应路由到业务处理器")
    void shouldRouteNaturalDeadLetterByRetryTopic() {
        String businessTopic = "natural-business-topic";
        String consumerGroup = "natural-consumer-group";
        StringBuilder handled = new StringBuilder();
        processor.registerHandler(businessTopic, consumerGroup, msg -> {
            handled.append(msg.getUserProperty("tenantId"));
            return true;
        });

        MessageExt message = createNaturalDeadLetter("%DLQ%" + consumerGroup, businessTopic, "msg-natural-001");

        assertThat(processor.processDeadLetter(message)).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(handled).hasToString("tenant-42");
    }

    @Test
    @DisplayName("缺少Broker重试属性时应按死信Topic唯一反查业务处理器")
    void shouldRouteByUniqueDlqTopicWhenRetryTopicAbsent() {
        String businessTopic = "unique-business-topic";
        String consumerGroup = "unique-consumer-group";
        StringBuilder handled = new StringBuilder();
        processor.registerHandler(businessTopic, consumerGroup, msg -> {
            handled.append("handled");
            return true;
        });

        MessageExt message = createNaturalDeadLetter("%DLQ%" + consumerGroup, null, "msg-natural-002");

        assertThat(processor.processDeadLetter(message)).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(handled).hasToString("handled");
    }

    @Test
    @DisplayName("声明消费组并注册处理器后应按死信Topic反查并路由")
    void shouldResolveTopicForDeclaredConsumerGroup() {
        DeadLetterQueueProcessor.DlqHandler handler = mock(DeadLetterQueueProcessor.DlqHandler.class);
        when(handler.handle(any(MessageExt.class))).thenReturn(true);
        processor.registerConsumerGroup("declared-topic", "declared-consumer-group");
        processor.registerHandler("declared-topic", "declared-consumer-group", handler);

        MessageExt message = createNaturalDeadLetter("%DLQ%declared-consumer-group", null, "msg-natural-005");

        assertThat(processor.processDeadLetter(message)).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verify(handler).handle(message);
    }

    @Test
    @DisplayName("同组多Topic且缺少原Topic属性时不得误路由或成功确认")
    void shouldReconsumeAmbiguousTopicWithoutInvokingHandlers() {
        String consumerGroup = "shared-consumer-group";
        DeadLetterQueueProcessor.DlqHandler firstHandler = mock(DeadLetterQueueProcessor.DlqHandler.class);
        DeadLetterQueueProcessor.DlqHandler secondHandler = mock(DeadLetterQueueProcessor.DlqHandler.class);
        when(secondHandler.handle(any(MessageExt.class))).thenReturn(true);
        processor.registerHandler("shared-topic-a", consumerGroup, firstHandler);
        processor.registerHandler("shared-topic-b", consumerGroup, secondHandler);

        MessageExt message = createNaturalDeadLetter("%DLQ%" + consumerGroup, null, "msg-natural-003");

        assertThat(processor.processDeadLetter(message)).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        verifyNoInteractions(firstHandler, secondHandler);
    }

    @Test
    @DisplayName("同组多Topic启动时只订阅合法DLQ Topic一次")
    void shouldSubscribeSharedConsumerGroupOnceWithoutNullTopic() throws Exception {
        processor.registerHandler("shared-topic-a", "shared-consumer-group", msg -> true);
        processor.registerHandler("shared-topic-b", "shared-consumer-group", msg -> true);

        try (MockedConstruction<DefaultMQPushConsumer> construction = mockConstruction(DefaultMQPushConsumer.class)) {
            processor.start();

            DefaultMQPushConsumer consumer = construction.constructed().get(0);
            verify(consumer).subscribe("%DLQ%shared-consumer-group", "*");
            verify(consumer, never()).subscribe("%DLQ%null", "*");
            verify(consumer).start();
        }
    }

    @Test
    @DisplayName("显式原Topic属性优先于重试前Topic属性")
    void shouldPreferExplicitOriginalTopicOverRetryTopic() {
        StringBuilder handled = new StringBuilder();
        processor.registerHandler("explicit-topic", "explicit-consumer-group", msg -> {
            handled.append("explicit");
            return true;
        });
        processor.registerHandler("retry-topic", "retry-consumer-group", msg -> {
            handled.append("retry");
            return true;
        });

        MessageExt message = createNaturalDeadLetter("%DLQ%explicit-consumer-group", "retry-topic", "msg-natural-004");
        when(message.getUserProperty("ORIGINAL_TOPIC")).thenReturn("explicit-topic");

        assertThat(processor.processDeadLetter(message)).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        assertThat(handled).hasToString("explicit");
    }

    /**
     * 创建模拟 RocketMQ 死信投递的 MessageExt，不作为真实 Broker 验收证据。
     *
     * @param dlqTopic   死信 Topic
     * @param retryTopic 重试前业务 Topic 属性，可为 {@code null}
     * @param msgId      消息 ID
     * @return Mock 的 MessageExt
     */
    private MessageExt createNaturalDeadLetter(String dlqTopic, String retryTopic, String msgId) {
        MessageExt message = mock(MessageExt.class);
        when(message.getTopic()).thenReturn(dlqTopic);
        when(message.getMsgId()).thenReturn(msgId);
        when(message.getReconsumeTimes()).thenReturn(16);
        when(message.getProperty(MessageConst.PROPERTY_RETRY_TOPIC)).thenReturn(retryTopic);
        when(message.getUserProperty("tenantId")).thenReturn("tenant-42");
        when(message.getBody()).thenReturn(new byte[0]);
        return message;
    }

    /**
     * 测试 NameServer 未配置时不应启动消费者。
     */
    @Test
    @DisplayName("NameServer未配置时不启动消费者")
    void shouldNotStartWhenNameServerBlank() {
        DeadLetterQueueProcessor noNameServerProcessor =
                new DeadLetterQueueProcessor("", DEFAULT_DLQ_CONSUMER_GROUP, null);
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
        DeadLetterQueueProcessor noNameServerProcessor =
                new DeadLetterQueueProcessor("", DEFAULT_DLQ_CONSUMER_GROUP, null);

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

    @Test
    @DisplayName("死信消费者组名应可配置且默认回退到生产组名")
    void shouldUseConfiguredDlqConsumerGroup() throws Exception {
        java.util.concurrent.atomic.AtomicReference<String> capturedGroup =
                new java.util.concurrent.atomic.AtomicReference<>();
        DeadLetterQueueProcessor configuredProcessor =
                new DeadLetterQueueProcessor("localhost:9876", "it-dlq-processor-group", null);
        configuredProcessor.registerHandler("cfg-topic", "cfg-consumer-group", msg -> true);

        try (MockedConstruction<DefaultMQPushConsumer> construction = mockConstruction(
                DefaultMQPushConsumer.class,
                (mock, context) ->
                        capturedGroup.set((String) context.arguments().get(0)))) {
            configuredProcessor.start();

            assertThat(capturedGroup.get()).isEqualTo("it-dlq-processor-group");
        }

        java.util.concurrent.atomic.AtomicReference<String> defaultGroup =
                new java.util.concurrent.atomic.AtomicReference<>();
        DeadLetterQueueProcessor defaultProcessor =
                new DeadLetterQueueProcessor("localhost:9876", DEFAULT_DLQ_CONSUMER_GROUP, null);
        defaultProcessor.registerHandler("cfg-topic", "cfg-consumer-group", msg -> true);

        try (MockedConstruction<DefaultMQPushConsumer> construction = mockConstruction(
                DefaultMQPushConsumer.class,
                (mock, context) -> defaultGroup.set((String) context.arguments().get(0)))) {
            defaultProcessor.start();

            assertThat(defaultGroup.get()).isEqualTo("apex-dlq-processor-dlq-consumer");
        }
    }

    /**
     * 测试处理死信消息时应累加死信指标。
     */
    @Test
    @DisplayName("处理死信消息时应累加死信指标")
    void shouldRecordDlqMetricWhenDeadLetterProcessed() {
        MqConsumerMetrics metrics = mock(MqConsumerMetrics.class);
        processor = new DeadLetterQueueProcessor("localhost:9876", DEFAULT_DLQ_CONSUMER_GROUP, metrics);
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
        processor = new DeadLetterQueueProcessor("localhost:9876", DEFAULT_DLQ_CONSUMER_GROUP, metrics);

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
        when(message.getUserProperty("tenantId")).thenReturn("tenant-42");
        when(message.getBody()).thenReturn(new byte[0]);
        return message;
    }
}
