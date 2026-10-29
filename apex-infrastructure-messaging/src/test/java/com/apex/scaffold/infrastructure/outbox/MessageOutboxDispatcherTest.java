package com.apex.scaffold.infrastructure.outbox;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.infrastructure.mq.service.MqProducerService;
import com.apex.scaffold.infrastructure.outbox.dispatcher.MessageOutboxDispatcher;
import com.apex.scaffold.infrastructure.outbox.entity.MessageOutboxEntity;
import com.apex.scaffold.infrastructure.outbox.service.MessageOutboxService;
import org.apache.rocketmq.client.producer.SendResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 消息 Outbox 调度器行为回归测试。
 *
 * <p>不依赖真实数据库与 RocketMQ，通过 Mockito 验证调度器对发送结果的处理契约：
 * 发送成功标记 DELIVERED；任何发送失败（包括未达尝试上限的瞬时失败）都登记
 * {@code markAsFailed} 以便按指数退避重试或进入死信；未取得锁的消息直接跳过。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@DisplayName("MessageOutboxDispatcher 调度行为")
@ExtendWith(MockitoExtension.class)
class MessageOutboxDispatcherTest {

    @Mock
    private MessageOutboxService outboxService;

    @Mock
    private MqProducerService mqProducerService;

    private MessageOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new MessageOutboxDispatcher(outboxService, mqProducerService);
        when(outboxService.releaseExpiredLocks()).thenReturn(Mono.empty());
    }

    private MessageOutboxEntity pendingEntity() {
        return MessageOutboxEntity.builder()
                .messageId("m-1")
                .topic("topic-test")
                .tag("tag-test")
                .payload("{}")
                .attempts(0)
                .maxAttempts(5)
                .build();
    }

    @Test
    @DisplayName("发送成功时应标记 DELIVERED")
    void dispatchMarksDeliveredOnSuccess() {
        when(outboxService.fetchPendingMessages(100)).thenReturn(Flux.just(pendingEntity()));
        when(outboxService.lockMessage(eq("m-1"), anyString())).thenReturn(Mono.just(true));
        when(outboxService.markAsDelivered(anyString())).thenReturn(Mono.empty());
        when(mqProducerService.sendSync(anyString(), anyString(), any())).thenReturn(new SendResult());

        dispatcher.dispatch();

        verify(outboxService).markAsDelivered("m-1");
        verify(outboxService, never()).markAsFailed(anyString(), any());
    }

    @Test
    @DisplayName("未达尝试上限的瞬时发送失败也应登记 markAsFailed 以安排重试")
    void dispatchMarksFailedOnTransientError() {
        when(outboxService.fetchPendingMessages(100)).thenReturn(Flux.just(pendingEntity()));
        when(outboxService.lockMessage(eq("m-1"), anyString())).thenReturn(Mono.just(true));
        when(outboxService.markAsFailed(anyString(), any())).thenReturn(Mono.empty());
        when(mqProducerService.sendSync(anyString(), anyString(), any())).thenThrow(new RuntimeException("mq down"));

        dispatcher.dispatch();

        verify(outboxService).markAsFailed("m-1", "mq down");
        verify(outboxService, never()).markAsDelivered(anyString());
    }

    @Test
    @DisplayName("未取得锁的消息不发送也不变更状态")
    void dispatchSkipsWhenLockNotAcquired() {
        when(outboxService.fetchPendingMessages(100)).thenReturn(Flux.just(pendingEntity()));
        when(outboxService.lockMessage(eq("m-1"), anyString())).thenReturn(Mono.just(false));

        dispatcher.dispatch();

        verify(mqProducerService, never()).sendSync(anyString(), anyString(), any());
        verify(outboxService, never()).markAsDelivered(anyString());
        verify(outboxService, never()).markAsFailed(anyString(), any());
    }
}
