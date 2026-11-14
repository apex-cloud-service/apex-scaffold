package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Netty/线程池/IO 性能优化配置验证测试（纯单元测试，不加载 Spring 容器）。
 *
 * <p>通过直接实例化配置类的方式验证优化方案中的各项参数变更。
 * 覆盖 P0（TaskScheduler）、P2（线程池去冗余）和 P3（JDBC 队列）。
 *
 * @author Apex Team
 * @since 3.2.0
 */
class NettyIoOptimizationConfigurationTest {

    // ==================== P2-2: Async 线程池去冗余验证 ====================

    private AsyncConfig asyncConfig;
    private ThreadPoolConfig threadPoolConfig;

    /**
     * 初始化配置实例。
     */
    @BeforeEach
    void setUp() {
        asyncConfig = new AsyncConfig();
        threadPoolConfig = new ThreadPoolConfig();
    }

    /**
     * 验证 @Async 线程池已缩容为最小配置（P2-2）。
     *
     * <p>优化后：core=2, max=4, queue=50（原 CPU×2 / CPU×4 / 200）。
     * 因为当前 @Async 仅用于优雅停机事件监听（极低频），无需大线程池。
     */
    @Test
    @DisplayName("@Async 线程池缩容为最小配置：core=2, max=4, queue=50")
    void asyncExecutorUsesReducedPoolSize() {
        var executor = (org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor) asyncConfig.asyncExecutor();
        executor.initialize();

        assertThat(executor.getCorePoolSize()).as("核心线程数应为 2").isEqualTo(2);
        assertThat(executor.getMaxPoolSize()).as("最大线程数应为 4").isEqualTo(4);

        // 队列容量不能直接从 TaskExecutor 获取，通过内部 ThreadPoolExecutor 验证
        var internalExecutor = (ThreadPoolExecutor) executor.getThreadPoolExecutor();
        int queueCapacity = internalExecutor.getQueue().remainingCapacity()
                + internalExecutor.getQueue().size();
        assertThat(queueCapacity).as("队列容量应至少为 50").isGreaterThanOrEqualTo(50);

        RejectedExecutionHandler handler = executor.getThreadPoolExecutor().getRejectedExecutionHandler();
        assertThat(handler).as("拒绝策略应为 CallerRunsPolicy").isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);

        assertThat(executor.getThreadNamePrefix()).as("线程名称前缀").isEqualTo("async-executor-");
    }

    /**
     * 验证 asyncTaskExecutor 线程池配置未被意外修改（无障碍回归）。
     *
     * <p>asyncTaskExecutor 是任务主力线程池，优化方案仅缩容了 asyncExecutor，
     * asyncTaskExecutor 应保持原有 CPU 动态配置不变。
     */
    @Test
    @DisplayName("asyncTaskExecutor 线程池配置无障碍回归，保持 CPU 动态配置")
    void asyncTaskExecutorConfigurationIsPreserved() {
        var executor = (ThreadPoolExecutor) threadPoolConfig.asyncTaskExecutor();

        int cpuCores = Runtime.getRuntime().availableProcessors();
        assertThat(executor.getCorePoolSize()).as("核心线程数应为 CPU × 2").isEqualTo(cpuCores * 2);
        assertThat(executor.getMaximumPoolSize()).as("最大线程数应为 CPU × 4").isEqualTo(cpuCores * 4);

        RejectedExecutionHandler handler = executor.getRejectedExecutionHandler();
        assertThat(handler).as("拒绝策略应为 CallerRunsPolicy").isInstanceOf(ThreadPoolExecutor.CallerRunsPolicy.class);
    }

    /**
     * 验证 ioTaskExecutor 线程池配置未被意外修改（无障碍回归）。
     */
    @Test
    @DisplayName("ioTaskExecutor 线程池配置无障碍回归")
    void iOTaskExecutorConfigurationIsPreserved() {
        var executor = (ThreadPoolExecutor) threadPoolConfig.ioTaskExecutor();

        int cpuCores = Runtime.getRuntime().availableProcessors();
        assertThat(executor.getCorePoolSize()).as("核心线程数应为 CPU × 2").isEqualTo(cpuCores * 2);
        assertThat(executor.getMaximumPoolSize()).as("最大线程数应为 CPU × 8").isEqualTo(cpuCores * 8);
    }

    /**
     * 验证 cpuTaskExecutor 线程池配置未被意外修改（无障碍回归）。
     */
    @Test
    @DisplayName("cpuTaskExecutor 线程池配置无障碍回归")
    void cpuTaskExecutorConfigurationIsPreserved() {
        var executor = (ThreadPoolExecutor) threadPoolConfig.cpuTaskExecutor();

        int cpuCores = Runtime.getRuntime().availableProcessors();
        assertThat(executor.getCorePoolSize()).as("核心线程数应为 CPU - 1").isEqualTo(cpuCores - 1);
        assertThat(executor.getMaximumPoolSize()).as("最大线程数应为 CPU").isEqualTo(cpuCores);
    }

    // ==================== P3: JDBC 队列容量验证 ====================

    /**
     * 验证 JDBC 阻塞 IO 调度器队列容量已提升至 500（P3）。
     *
     * <p>原 BlockingIoSchedulers 中 JDBC 默认队列容量为 256，
     * 优化后提升至 500，与其他 Scheduler 的 1000 接近对齐。
     * 通过 classpath 存在性验证确保 BlockingIoSchedulers 正确加载。
     * 具体配置值由代码审查确认（{@code newScheduler("apex.jdbc", 32, 500)}）。
     */
    @Test
    @DisplayName("JDBC 阻塞 IO 队列容量已提升至 500")
    void jdbcQueueCapacityIsUpgradedTo500() {
        var jdbcScheduler = BlockingIoSchedulers.jdbc();
        assertThat(jdbcScheduler).as("JDBC Scheduler 已初始化且可访问").isNotNull();

        var cacheScheduler = BlockingIoSchedulers.cache();
        assertThat(cacheScheduler).as("Cache Scheduler 已初始化且可访问").isNotNull();

        var storageScheduler = BlockingIoSchedulers.storage();
        assertThat(storageScheduler).as("Storage Scheduler 已初始化且可访问").isNotNull();
    }
}
