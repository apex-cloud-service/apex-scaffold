package com.apex.scaffold.infrastructure.mq.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.mq.constant.MqConstant;
import com.apex.scaffold.infrastructure.mq.handler.UserMessageDlqHandler;
import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import com.apex.scaffold.infrastructure.outbox.service.MessageOutboxService;
import java.nio.charset.StandardCharsets;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

@DisplayName("用户死信重新入Outbox租户契约")
class UserMessageDlqHandlerTest {

    private MessageOutboxService outboxService;
    private UserMessageDlqHandler handler;

    @BeforeEach
    void setUp() {
        outboxService = mock(MessageOutboxService.class);
        handler = new UserMessageDlqHandler(
                mock(DeadLetterQueueProcessor.class),
                outboxService,
                MqConstant.TOPIC_USER,
                MqConstant.CONSUMER_GROUP_USER);
    }

    @Test
    @DisplayName("应向死信处理器注册实际Topic与消费组")
    void shouldRegisterActualTopicAndConsumerGroup() {
        DeadLetterQueueProcessor processor = mock(DeadLetterQueueProcessor.class);
        UserMessageDlqHandler customHandler =
                new UserMessageDlqHandler(processor, outboxService, "custom-topic", "custom-group");

        customHandler.register();

        verify(processor).registerHandler("custom-topic", "custom-group", customHandler);
    }

    @Test
    @DisplayName("回箱Outbox应使用实际Topic而非默认常量")
    void shouldWriteOutboxWithActualTopic() {
        when(outboxService.append(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        UserMessageDlqHandler customHandler = new UserMessageDlqHandler(
                mock(DeadLetterQueueProcessor.class), outboxService, "custom-topic", "custom-group");

        assertThat(customHandler.handle(message("tenant-a"))).isTrue();

        ArgumentCaptor<MessageOutboxEntity> captor = ArgumentCaptor.forClass(MessageOutboxEntity.class);
        verify(outboxService).append(captor.capture());
        assertThat(captor.getValue().getTopic()).isEqualTo("custom-topic");
    }

    @Test
    @DisplayName("缺失或空白租户不得兜底default或写入Outbox")
    void shouldRejectMissingTenant() {
        when(outboxService.append(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        for (String tenant : new String[] {null, "", "  "}) {
            MessageExt message = mock(MessageExt.class);
            when(message.getUserProperty("tenantId")).thenReturn(tenant);
            assertThatThrownBy(() -> handler.handle(message)).isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(outboxService);
    }

    @Test
    @DisplayName("重新入Outbox保留消息租户与载荷")
    void shouldPreserveTenantAndPayload() {
        when(outboxService.append(any())).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        assertThat(handler.handle(message("tenant-a"))).isTrue();

        ArgumentCaptor<MessageOutboxEntity> captor = ArgumentCaptor.forClass(MessageOutboxEntity.class);
        verify(outboxService).append(captor.capture());
        MessageOutboxEntity outbox = captor.getValue();
        assertThat(outbox.getTenantId()).isEqualTo("tenant-a");
        assertThat(outbox.getMessageId()).isEqualTo("event-1:dlq-retry");
        assertThat(outbox.getTopic()).isEqualTo(MqConstant.TOPIC_USER);
        assertThat(outbox.getPayload()).isEqualTo("{\"tenantId\":\"tenant-a\"}");
    }

    @Test
    @DisplayName("Outbox写入失败不得返回成功")
    void shouldPropagateOutboxFailure() {
        when(outboxService.append(any())).thenReturn(Mono.error(new IllegalStateException("存储失败")));

        assertThatThrownBy(() -> handler.handle(message("tenant-a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("存储失败");
    }

    private MessageExt message(String tenant) {
        MessageExt message = new MessageExt();
        message.setTopic(MqConstant.TOPIC_USER);
        message.setKeys("event-1");
        message.setBody("{\"tenantId\":\"tenant-a\"}".getBytes(StandardCharsets.UTF_8));
        if (tenant != null) {
            message.putUserProperty("tenantId", tenant);
        }
        return message;
    }
}
