package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.interfaces.governance.lifecycle.GracefulShutdownCoordinator;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 优雅关闭管理器。
 *
 * <p>参与应用关闭生命周期，将关停流程委托给统一协调器并记录异步线程池状态：
 * <ol>
 *   <li>协调器先切换 readiness 并停止资源接收新任务</li>
 *   <li>MQ、线程池和其他后台资源通过排空参与者共享同一个关停截止时间</li>
 *   <li>超时后的资源强制停止由协调器统一执行</li>
 *   <li>Spring Boot 的 HTTP graceful shutdown 负责 Web 服务器自身的关闭</li>
 * </ol>
 *
 * <h2>设计说明</h2>
 * <p>本类返回 {@link Integer#MIN_VALUE} 作为 {@link SmartLifecycle} phase，
 * 用于参与生命周期组件的停止排序；该 phase 不定义 {@code @PreDestroy} 回调之间的严格顺序。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ThreadPoolConfig
 * @see AsyncConfig
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "apex.governance", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GracefulShutdownManager implements SmartLifecycle {

    /** 生命周期活跃标记 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** 统一关停协调器 */
    private final GracefulShutdownCoordinator coordinator;

    /** Spring asyncExecutor（ThreadPoolTaskExecutor 包装的 Executor） */
    private final java.util.concurrent.Executor asyncExecutor;

    /**
     * 构造优雅关闭管理器。
     *
     * @param asyncExecutor Spring {@code @Async} 线程池执行器
     * @param coordinator 统一关停协调器
     */
    public GracefulShutdownManager(
            @Qualifier("asyncExecutor") java.util.concurrent.Executor asyncExecutor,
            GracefulShutdownCoordinator coordinator) {
        this.asyncExecutor = asyncExecutor;
        this.coordinator = coordinator;
    }

    /**
     * 启动生命周期组件。
     */
    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            coordinator.start();
            log.info("优雅关闭管理器已启动");
        }
    }

    /**
     * 停止生命周期组件，固定等待 RocketMQ 消费者并记录 {@code @Async} 线程池状态。
     *
     * <p>线程池和连接池的实际关闭由各自 Bean 生命周期负责。
     */
    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("===== 优雅关闭流程开始 =====");
            coordinator.shutdown().toCompletableFuture().join();
            logAsyncExecutorStatus();
            log.info("===== 优雅关闭流程完成 =====");
        }
    }

    /**
     * 判断生命周期组件是否正在运行。
     *
     * @return true 表示正在运行
     */
    @Override
    public boolean isRunning() {
        return running.get();
    }

    /**
     * 返回生命周期停止阶段。
     *
     * <p>使用 {@link Integer#MIN_VALUE} 参与 {@link SmartLifecycle} 组件的停止排序，
     * 不用于约束普通 Bean 的销毁回调顺序。
     *
     * @return 最小整数值
     */
    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    /**
     * 记录 @Async 线程池当前状态。
     *
     * <p>{@code ThreadPoolTaskExecutor} 已配置 {@code waitForTasksToCompleteOnShutdown=true}
     * 和 {@code awaitTerminationSeconds=60}，Spring 会在 Bean 销毁时自动等待排空。
     * 本方法在 Bean 销毁前提前记录状态，便于监控和排查。
     */
    private void logAsyncExecutorStatus() {
        if (asyncExecutor instanceof org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor tpte) {
            int active = tpte.getActiveCount();
            int queueSize = tpte.getThreadPoolExecutor().getQueue().size();
            log.info("[Shutdown-2] @Async 线程池状态: 活跃任务={}, 队列待处理={}", active, queueSize);
            if (active > 0 || queueSize > 0) {
                log.info("[Shutdown-2] @Async 线程池有未完成任务, Spring 将在 Bean 销毁时等待最多 60s");
            }
        } else {
            log.info("[Shutdown-2] @Async 线程池排空由 Spring Bean 生命周期处理");
        }
    }
}
