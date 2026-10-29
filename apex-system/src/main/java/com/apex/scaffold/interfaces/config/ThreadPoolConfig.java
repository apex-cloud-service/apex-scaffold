package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.interfaces.governance.lifecycle.ExecutorDrainParticipant;
import com.apex.scaffold.interfaces.governance.lifecycle.ResourceDrainRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * 线程池配置类。
 *
 * <p>提供高并发场景下的异步任务处理能力，根据任务类型分类管理线程池资源。
 * 所有线程池均设置 {@link Thread.UncaughtExceptionHandler}，确保未捕获异常
 * 能够被记录日志而非静默丢失。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><strong>工厂模式</strong>：通过 {@link Bean} 注解创建和管理不同类型的线程池实例</li>
 *   <li><strong>策略模式</strong>：根据任务类型（异步/IO密集/CPU密集）选择不同的线程池策略</li>
 *   <li><strong>资源隔离模式</strong>：不同类型的任务使用独立的线程池，避免相互影响</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>{@code asyncTaskExecutor} 与 {@link AsyncConfig#asyncExecutor()} 原参数完全一致
 * （core=CPU×2, max=CPU×4, queue=200），在 8 核机器上合计 32-64 线程，存在冗余。
 * {@code asyncExecutor} 已缩容为最小配置（core=2, max=4, queue=50）。
 * 本类的 {@code asyncTaskExecutor} 是通用异步任务的主力线程池，
 * {@link AsyncConfig#asyncExecutor()} 仅服务于 Spring {@code @Async} 注解。</p>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 */
@Slf4j
@Configuration
public class ThreadPoolConfig {

    /** 线程池关闭等待时间（秒） */
    private static final long SHUTDOWN_WAIT_SECONDS = 30L;

    /** 异步任务线程池配置 */
    private static final int ASYNC_CORE_MULTIPLIER = 2;

    private static final int ASYNC_MAX_MULTIPLIER = 4;
    private static final long ASYNC_KEEP_ALIVE_SECONDS = 60L;
    private static final int ASYNC_QUEUE_CAPACITY = 200;

    /** IO密集型任务线程池配置 */
    private static final int IO_CORE_MULTIPLIER = 2;

    private static final int IO_MAX_MULTIPLIER = 8;
    private static final long IO_KEEP_ALIVE_SECONDS = 120L;
    private static final int IO_QUEUE_CAPACITY = 500;

    /** CPU密集型任务线程池配置 */
    private static final int CPU_CORE_ADJUSTMENT = 1;

    private static final long CPU_KEEP_ALIVE_SECONDS = 30L;
    private static final int CPU_QUEUE_CAPACITY = 100;

    private ExecutorService asyncTaskExecutor;
    private ExecutorService ioTaskExecutor;
    private ExecutorService cpuTaskExecutor;

    /**
     * 注册三个业务线程池到统一关停协调器。
     *
     * @param event 应用就绪事件
     */
    @EventListener
    public void threadPoolDrainRegistration(ApplicationReadyEvent event) {
        ResourceDrainRegistry registry =
                event.getApplicationContext().getBeansOfType(ResourceDrainRegistry.class).values().stream()
                        .findFirst()
                        .orElse(null);
        if (registry == null) {
            return;
        }
        if (asyncTaskExecutor != null) {
            registry.register(new ExecutorDrainParticipant("asyncTaskExecutor", asyncTaskExecutor));
        }
        if (ioTaskExecutor != null) {
            registry.register(new ExecutorDrainParticipant("ioTaskExecutor", ioTaskExecutor));
        }
        if (cpuTaskExecutor != null) {
            registry.register(new ExecutorDrainParticipant("cpuTaskExecutor", cpuTaskExecutor));
        }
    }

    /**
     * 异步任务线程池
     *
     * <p>用于处理耗时操作，如发送短信、记录日志等。
     *
     * <h2>线程安全性</h2>
     * <ul>
     *   <li>核心线程数：CPU核心数 * 2</li>
     *   <li>最大线程数：CPU核心数 * 4</li>
     *   <li>队列容量：200</li>
     *   <li>拒绝策略：CallerRunsPolicy（由调用线程执行）</li>
     * </ul>
     *
     * @return ExecutorService 异步任务线程池
     */
    @Bean(name = "asyncTaskExecutor")
    public ExecutorService asyncTaskExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();

        asyncTaskExecutor = new ThreadPoolExecutor(
                cpuCores * ASYNC_CORE_MULTIPLIER,
                cpuCores * ASYNC_MAX_MULTIPLIER,
                ASYNC_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(ASYNC_QUEUE_CAPACITY),
                createThreadFactory("async-task-"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return asyncTaskExecutor;
    }

    /**
     * IO 密集型任务线程池
     *
     * <p>用于处理 IO 密集型操作，如文件读写、网络请求等。
     *
     * <h2>线程安全性</h2>
     * <ul>
     *   <li>核心线程数：CPU核心数 * 2</li>
     *   <li>最大线程数：CPU核心数 * 8</li>
     *   <li>队列容量：500</li>
     *   <li>空闲保活时间：120秒</li>
     *   <li>拒绝策略：CallerRunsPolicy（由调用线程执行）</li>
     * </ul>
     *
     * @return ExecutorService IO密集型任务线程池
     */
    @Bean(name = "ioTaskExecutor")
    public ExecutorService ioTaskExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();

        ioTaskExecutor = new ThreadPoolExecutor(
                cpuCores * IO_CORE_MULTIPLIER,
                cpuCores * IO_MAX_MULTIPLIER,
                IO_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(IO_QUEUE_CAPACITY),
                createThreadFactory("io-task-"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return ioTaskExecutor;
    }

    /**
     * CPU 密集型任务线程池
     *
     * <p>用于处理 CPU 密集型操作，如复杂计算、数据转换等。
     *
     * <h2>线程安全性</h2>
     * <ul>
     *   <li>核心线程数：CPU核心数 - 1（最少为1）</li>
     *   <li>最大线程数：CPU核心数（最少为1）</li>
     *   <li>队列容量：100</li>
     *   <li>空闲保活时间：30秒</li>
     *   <li>拒绝策略：CallerRunsPolicy（由调用线程执行）</li>
     * </ul>
     *
     * @return ExecutorService CPU密集型任务线程池
     */
    @Bean(name = "cpuTaskExecutor")
    public ExecutorService cpuTaskExecutor() {
        int cpuCores = Runtime.getRuntime().availableProcessors();

        cpuTaskExecutor = new ThreadPoolExecutor(
                Math.max(1, cpuCores - CPU_CORE_ADJUSTMENT),
                Math.max(1, cpuCores),
                CPU_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(CPU_QUEUE_CAPACITY),
                createThreadFactory("cpu-task-"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        return cpuTaskExecutor;
    }

    /**
     * 创建带有 UncaughtExceptionHandler 的线程工厂
     *
     * <p>为线程池中的每个线程设置 {@link Thread.UncaughtExceptionHandler}，
     * 当线程因未捕获异常而终止时，记录错误日志，避免异常被静默吞没。
     *
     * @param namePrefix 线程名称前缀
     * @return 配置了异常处理器的线程工厂
     */
    private ThreadFactory createThreadFactory(String namePrefix) {
        CustomizableThreadFactory factory = new CustomizableThreadFactory(namePrefix);
        return runnable -> {
            Thread thread = factory.newThread(runnable);
            thread.setUncaughtExceptionHandler((t, e) -> log.error(
                    "线程池中线程执行异常, threadName: {}, threadPool: {}, error: {}",
                    t.getName(),
                    namePrefix,
                    e.getMessage(),
                    e));
            return thread;
        };
    }

    /**
     * 保留兼容关闭入口。
     *
     * <p>线程池由统一资源排空注册表关闭，避免多个销毁回调叠加等待时间。
     */
    public void shutdown() {
        log.info("线程池关闭由统一治理协调器处理");
    }
}
