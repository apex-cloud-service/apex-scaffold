package com.apex.scaffold.infrastructure.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.governance.DrainResult;
import com.apex.scaffold.infrastructure.mq.listener.DeadLetterQueueProcessor;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MqDrainParticipant 单元测试")
class MqDrainParticipantTest {
    @Mock
    private MqConsumerService consumerService;

    @Mock
    private DeadLetterQueueProcessor deadLetterQueueProcessor;

    @Test
    @DisplayName("应停止并共同排空普通消费与死信处理")
    void shouldStopAndDrainBothMqResources() throws Exception {
        Instant deadline = Instant.now().plusSeconds(1);
        when(consumerService.drain(deadline)).thenReturn(CompletableFuture.completedFuture(true));
        when(deadLetterQueueProcessor.drain(deadline)).thenReturn(CompletableFuture.completedFuture(true));
        MqDrainParticipant participant = new MqDrainParticipant(consumerService, deadLetterQueueProcessor);

        participant.stopAccepting();
        DrainResult result = participant.drain(deadline).toCompletableFuture().get(1, TimeUnit.SECONDS);

        verify(consumerService).stopAccepting();
        verify(deadLetterQueueProcessor).stopAccepting();
        assertThat(result).isEqualTo(DrainResult.drained("rocketmq"));
    }

    @Test
    @DisplayName("任一 MQ 资源未排空时应返回超时结果")
    void shouldReturnTimedOutWhenOneResourceDoesNotDrain() throws Exception {
        Instant deadline = Instant.now().plusSeconds(1);
        when(consumerService.drain(deadline)).thenReturn(CompletableFuture.completedFuture(true));
        when(deadLetterQueueProcessor.drain(deadline)).thenReturn(CompletableFuture.completedFuture(false));
        MqDrainParticipant participant = new MqDrainParticipant(consumerService, deadLetterQueueProcessor);

        DrainResult result = participant.drain(deadline).toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertThat(result.drained()).isFalse();
        assertThat(result.participant()).isEqualTo("rocketmq");
    }
}
