package com.apex.scaffold.interfaces.config;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * 优雅关闭管理器。
 *
 * <p>统一管理应用关闭时各组件的停止顺序，确保：
 * <ol>
 *   <li>先从注册中心摘除流量（Nacos deregister）</li>
 *   <li>停止接收新的 HTTP 请求（Spring graceful shutdown）</li>
 *   <li>等待 RocketMQ 消费者处理完已拉取的消息</li>
 *   <li>等待 @Async 线程池排空队列中的任务</li>
 *   <li>关闭数据库连接池和 Redis 连接</li>
 * </ol>
 *
 * <h2>关闭顺序设计</h2>
 * <p>Spring 的 {@code @PreDestroy} 和 {@code SmartLifecycle} 回调默认无严格顺序。
 * 本类通过 {@link SmartLifecycle} 的 {@code getPhase()} 控制关闭阶段：
 * <ul>
 *   <li>Phase = {@link Integer#MIN_VALUE}：最先执行，在所有业务 Bean 销毁前</li>
 *   <li>各线程池的 {@code @PreDestroy} 在中间阶段执行</li>
 *   <li>连接池的关闭在最后阶段由 Spring 自动处理</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ThreadPoolConfig
 * @see AsyncConfig
 */
@Slf4j
@Component
public class GracefulShutdownManager implements SmartLifecycle {

    private static final Logger logger = LoggerFactory.getLogger(GracefulShutdownManager.class);

    /** MQ 消费者排空等待时间（秒） */
    private static final long MQ_DRAIN_WAIT_SECONDS = 15L;

    /** 生命周期活跃标记 */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Spring asyncExecutor（ThreadPoolTaskExecutor 包装的 Executor） */
    private final java.util.concurrent.Executor asyncExecutor;

    /**
     * 构造优雅关闭管理器。
     *
     * @param asyncExecutor Spring {@code @Async} 线程池执行器
     */
    public GracefulShutdownManager(@Qualifier("asyncExecutor") java.util.concurrent.Executor asyncExecutor) {
        this.asyncExecutor = asyncExecutor;
    }

    /**
     * 启动生命周期组件。
     */
    @Override
    public void start() {
        running.set(true);
        logger.info("优雅关闭管理器已启动");
    }

    /**
     * 停止生命周期组件，执行有序关闭。
     *
     * <p>关闭顺序：
     * 1. 等待 RocketMQ 消费者处理完已拉取消息
     * 2. 记录 @Async 线程池状态（排空由 Spring Bean 生命周期处理）
     * 3. ThreadPoolConfig 的 @PreDestroy 会在此后自动执行
     */
    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        logger.info("===== 优雅关闭流程开始 =====");

        // 阶段 1：等待 RocketMQ 消费者处理完已拉取的消息
        logger.info("[Shutdown-1] 等待 RocketMQ 消费者排空已拉取消息, 超时: {}s", MQ_DRAIN_WAIT_SECONDS);
        waitForMqConsumerDrain();

        // 阶段 2：记录 @Async 线程池状态
        logger.info("[Shutdown-2] 检查 @Async 线程池状态");
        logAsyncExecutorStatus();

        // 阶段 3：ThreadPoolConfig 的 @PreDestroy 会在此后自动执行
        logger.info("[Shutdown-3] 等待 ThreadPoolConfig 线程池关闭（由 @PreDestroy 处理）");

        logger.info("===== 优雅关闭流程完成 =====");
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
     * 返回关闭阶段。
     *
     * <p>使用 {@link Integer#MIN_VALUE} 确保本组件在所有其他 Bean 之前执行关闭逻辑，
     * 以便在连接池和线程池销毁前给消费者和异步任务留出排空时间。
     *
     * @return 最小整数值
     */
    @Override
    public int getPhase() {
        return Integer.MIN_VALUE;
    }

    /**
     * 等待 RocketMQ 消费者处理完已拉取的消息。
     *
     * <p>RocketMQ Spring 消费者在容器关闭时会自动调用 {@code consumer.shutdown()}，
     * 但已拉取到本地但尚未处理的消息可能被中断。本方法通过短暂等待
     * 让消费线程有机会完成处理。
     */
    private void waitForMqConsumerDrain() {
        try {
            // 给 MQ 消费者一个宽限期处理已拉取消息
            Thread.sleep(MQ_DRAIN_WAIT_SECONDS * 1000);
            logger.info("[Shutdown-1] RocketMQ 消费者排空等待完成");
        } catch (InterruptedException e) {
            logger.warn("[Shutdown-1] RocketMQ 消费者排空等待被中断");
            Thread.currentThread().interrupt();
        }
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
            logger.info("[Shutdown-2] @Async 线程池状态: 活跃任务={}, 队列待处理={}", active, queueSize);
            if (active > 0 || queueSize > 0) {
                logger.info("[Shutdown-2] @Async 线程池有未完成任务, Spring 将在 Bean 销毁时等待最多 60s");
            }
        } else {
            logger.info("[Shutdown-2] @Async 线程池排空由 Spring Bean 生命周期处理");
        }
    }
}
