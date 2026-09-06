package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.interfaces.governance.lifecycle.ExecutorDrainParticipant;
import com.apex.scaffold.interfaces.governance.lifecycle.ResourceDrainRegistry;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 异步任务配置类。
 *
 * <p>为 Spring {@code @Async} 注解提供线程池执行器。
 * 当前 {@code @Async} 仅用于 {@link com.apex.scaffold.interfaces.config.GracefulShutdownManager}
 * 的优雅停机事件监听，属于极低频调用。
 *
 * <h2>优化说明（Netty/线程池/IO 性能优化方案 P2-2）</h2>
 * <p>本类的 {@code asyncExecutor} 与 {@link ThreadPoolConfig#asyncTaskExecutor()}
 * 参数原完全一致（core=CPU×2, max=CPU×4, queue=200），在 8 核机器上合计 32-64 线程，
 * 存在冗余。优化方案：
 * <ul>
 *   <li>将 {@code asyncExecutor} 缩容为最小配置（core=2, max=4, queue=50），
 *       因为 {@code @Async} 仅用于极低频的优雅停机事件</li>
 *   <li>异步任务的主体由 {@code asyncTaskExecutor}（ThreadPoolConfig）承担</li>
 *   <li>两个线程池职责明确：{@code asyncExecutor} 供 Spring {@code @Async} 使用，
 *       {@code asyncTaskExecutor} 供代码内显式提交使用</li>
 * </ul></p>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li><strong>配置模式</strong>：通过外部化配置和 {@link Bean} 注解管理线程池参数</li>
 *   <li><strong>生产者-消费者模式</strong>：通过线程池 + 阻塞队列实现任务的异步生产和消费</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.1.0
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    private ThreadPoolTaskExecutor asyncExecutor;

    /**
     * 构造异步任务配置类。
     *
     * <p>由 Spring 容器自动实例化，无需手动创建。
     */
    public AsyncConfig() {}

    /**
     * 配置 Spring {@code @Async} 注解的线程池执行器。
     *
     * <p>用于处理 {@code @Async} 标记的异步方法调用。
     * 当前 {@code @Async} 仅用于 {@link com.apex.scaffold.interfaces.config.GracefulShutdownManager}
     * 的优雅停机事件监听（极低频），因此配置为最小线程池。
     *
     * <h2>线程池配置（优化后）</h2>
     * <ul>
     *   <li>核心线程数：2（原 CPU×2，冗余缩容后）</li>
     *   <li>最大线程数：4（原 CPU×4，冗余缩容后）</li>
     *   <li>队列容量：50（原 200，冗余缩容后）</li>
     *   <li>拒绝策略：CallerRunsPolicy（由调用线程执行）</li>
     *   <li>优雅关闭：等待任务完成，最多等待 60 秒</li>
     * </ul>
     *
     * @return Executor 线程池执行器
     */
    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {
        log.info("初始化 @Async 线程池（冗余缩容后最小配置）");

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        int corePoolSize = 2;
        int maxPoolSize = 4;

        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(50);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("async-executor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        log.info("@Async 线程池初始化完成（缩容后）, coreSize: {}, maxSize: {}, queueCapacity: {}", corePoolSize, maxPoolSize, 50);

        asyncExecutor = executor;
        return executor;
    }

    /**
     * 将 Spring 异步线程池注册到统一资源排空注册表。
     *
     * @param event 应用就绪事件
     */
    @EventListener
    public void asyncExecutorDrainRegistration(ApplicationReadyEvent event) {
        if (asyncExecutor != null) {
            ResourceDrainRegistry registry =
                    event.getApplicationContext().getBeansOfType(ResourceDrainRegistry.class).values().stream()
                            .findFirst()
                            .orElse(null);
            if (registry != null) {
                registry.register(new ExecutorDrainParticipant("asyncExecutor", asyncExecutor));
            }
        }
    }
}
