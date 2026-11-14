package com.apex.scaffold.interfaces.governance.lifecycle;

import com.apex.scaffold.common.governance.DrainParticipant;
import com.apex.scaffold.common.governance.DrainResult;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 资源排空参与者注册表。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ResourceDrainRegistry {
    private final List<DrainParticipant> participants = new CopyOnWriteArrayList<>();

    /**
     * 注册资源参与者。
     *
     * @param participant 资源参与者
     */
    public void register(DrainParticipant participant) {
        if (participant != null && !participants.contains(participant)) {
            participants.add(participant);
        }
    }

    /**
     * 按注册顺序停止接收新任务。
     */
    public void stopAccepting() {
        participants.forEach(DrainParticipant::stopAccepting);
    }

    /**
     * 使用同一截止时间并行排空所有资源。
     *
     * @param deadline 统一截止时间
     * @return 所有参与者的排空结果
     */
    public CompletionStage<List<DrainResult>> drain(Instant deadline) {
        List<CompletableFuture<DrainResult>> futures = participants.stream()
                .map(participant -> drainSafely(participant, deadline))
                .toList();
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .thenApply(
                        ignored -> futures.stream().map(CompletableFuture::join).toList());
    }

    /**
     * 强制停止所有资源。
     */
    public void forceStop() {
        participants.forEach(this::forceStopSafely);
    }

    /**
     * 返回当前注册的参与者快照。
     *
     * @return 参与者列表
     */
    public List<DrainParticipant> participants() {
        return List.copyOf(new ArrayList<>(participants));
    }

    private CompletableFuture<DrainResult> drainSafely(DrainParticipant participant, Instant deadline) {
        try {
            long remainingMillis =
                    Math.max(0, Duration.between(Instant.now(), deadline).toMillis());
            return participant
                    .drain(deadline)
                    .toCompletableFuture()
                    .orTimeout(remainingMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .exceptionally(error -> DrainResult.timedOut(
                            participant.name(), error.getClass().getSimpleName()));
        } catch (RuntimeException exception) {
            return CompletableFuture.completedFuture(DrainResult.timedOut(
                    participant.name(), exception.getClass().getSimpleName()));
        }
    }

    private void forceStopSafely(DrainParticipant participant) {
        try {
            participant.forceStop();
        } catch (RuntimeException ignored) {
        }
    }
}
