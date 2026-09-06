package com.apex.scaffold.interfaces.governance.lifecycle;

import com.apex.scaffold.common.governance.DrainParticipant;
import com.apex.scaffold.common.governance.DrainResult;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 线程池资源排空适配器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class ExecutorDrainParticipant implements DrainParticipant {
    private final String participantName;
    private final ExecutorService executor;

    /**
     * 使用线程池任务执行器创建参与者。
     *
     * @param participantName 参与者名称
     * @param executor 线程池执行器
     */
    public ExecutorDrainParticipant(String participantName, ThreadPoolTaskExecutor executor) {
        this(participantName, executor.getThreadPoolExecutor());
    }

    /**
     * 使用 ExecutorService 创建参与者。
     *
     * @param participantName 参与者名称
     * @param executor 线程池执行器
     */
    public ExecutorDrainParticipant(String participantName, ExecutorService executor) {
        this.participantName = participantName;
        this.executor = executor;
    }

    /** @return 稳定参与者名称 */
    @Override
    public String name() {
        return participantName;
    }

    /**
     * 停止线程池接收新任务。
     */
    @Override
    public void stopAccepting() {
        executor.shutdown();
    }

    /**
     * 等待线程池在统一截止时间前完成。
     *
     * @param deadline 统一截止时间
     * @return 排空结果
     */
    @Override
    public CompletionStage<DrainResult> drain(Instant deadline) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                long remaining =
                        Math.max(0, Duration.between(Instant.now(), deadline).toNanos());
                boolean drained = executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
                return drained
                        ? DrainResult.drained(participantName)
                        : DrainResult.timedOut(participantName, "executor termination timeout");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return DrainResult.timedOut(participantName, "interrupted");
            }
        });
    }

    /**
     * 强制停止线程池。
     */
    @Override
    public void forceStop() {
        executor.shutdownNow();
    }
}
