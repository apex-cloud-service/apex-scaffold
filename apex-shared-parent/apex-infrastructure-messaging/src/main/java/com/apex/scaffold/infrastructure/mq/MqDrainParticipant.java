package com.apex.scaffold.infrastructure.mq;

import com.apex.scaffold.common.governance.DrainParticipant;
import com.apex.scaffold.common.governance.DrainResult;
import com.apex.scaffold.infrastructure.mq.listener.DeadLetterQueueProcessor;
import com.apex.scaffold.infrastructure.mq.service.MqConsumerService;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * MQ 普通消费与死信处理的资源排空适配器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@lombok.RequiredArgsConstructor
public class MqDrainParticipant implements DrainParticipant {
    private final MqConsumerService consumerService;
    private final DeadLetterQueueProcessor deadLetterQueueProcessor;

    /**
     * @return MQ 参与者名称
     */
    @Override
    public String name() {
        return "rocketmq";
    }

    /**
     * 停止普通消息和死信消息的 intake。
     */
    @Override
    public void stopAccepting() {
        consumerService.stopAccepting();
        deadLetterQueueProcessor.stopAccepting();
    }

    /**
     * 等待普通消息和死信消息共同排空。
     *
     * @param deadline 统一截止时间
     * @return 排空结果
     */
    @Override
    public CompletionStage<DrainResult> drain(Instant deadline) {
        CompletableFuture<Boolean> consumer = consumerService.drain(deadline).toCompletableFuture();
        CompletableFuture<Boolean> deadLetter =
                deadLetterQueueProcessor.drain(deadline).toCompletableFuture();
        return CompletableFuture.allOf(consumer, deadLetter)
                .thenApply(ignored -> consumer.join() && deadLetter.join()
                        ? DrainResult.drained(name())
                        : DrainResult.timedOut(name(), "message processing did not drain"));
    }

    /**
     * 强制停止 RocketMQ 资源。
     */
    @Override
    public void forceStop() {
        consumerService.forceStop();
        deadLetterQueueProcessor.forceStop();
    }
}
