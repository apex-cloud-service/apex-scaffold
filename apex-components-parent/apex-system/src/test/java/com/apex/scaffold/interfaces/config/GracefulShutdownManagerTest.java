package com.apex.scaffold.interfaces.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.apex.scaffold.interfaces.governance.lifecycle.GracefulShutdownCoordinator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 优雅关闭管理器单元测试。
 *
 * <p>验证 {@link GracefulShutdownManager} 的生命周期控制和
 * @Async 线程池状态记录逻辑。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("GracefulShutdownManager 优雅关闭管理器测试")
class GracefulShutdownManagerTest {

    /** 被测管理器 */
    private GracefulShutdownManager manager;

    /** Mock 线程池执行器 */
    private ThreadPoolTaskExecutor asyncExecutor;

    /** Mock 关停协调器 */
    private GracefulShutdownCoordinator coordinator;

    /**
     * 初始化测试环境。
     */
    @BeforeEach
    void setUp() {
        asyncExecutor = mock(ThreadPoolTaskExecutor.class);
        when(asyncExecutor.getActiveCount()).thenReturn(0);
        when(asyncExecutor.getThreadPoolExecutor())
                .thenReturn(new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()));
        coordinator = mock(GracefulShutdownCoordinator.class);
        when(coordinator.shutdown())
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(
                        GracefulShutdownCoordinator.ShutdownResult.alreadyStopped()));
        manager = new GracefulShutdownManager(asyncExecutor, coordinator);
    }

    /**
     * 测试启动后应处于运行状态。
     */
    @Test
    @DisplayName("启动后应处于运行状态")
    void shouldBeRunningAfterStart() {
        assertThat(manager.isRunning()).isFalse();
        manager.start();
        assertThat(manager.isRunning()).isTrue();
    }

    /**
     * 测试停止后应处于非运行状态。
     */
    @Test
    @DisplayName("停止后应处于非运行状态")
    void shouldNotBeRunningAfterStop() {
        manager.start();
        manager.stop();
        assertThat(manager.isRunning()).isFalse();
    }

    /**
     * 测试未启动时停止不应有副作用。
     */
    @Test
    @DisplayName("未启动时停止不应有副作用")
    void shouldNotThrowWhenStopWithoutStart() {
        manager.stop();
        assertThat(manager.isRunning()).isFalse();
    }

    /**
     * 测试 Phase 应为最小值以确保最先执行关闭。
     */
    @Test
    @DisplayName("Phase应为最小值确保最先关闭")
    void shouldReturnMinPhase() {
        assertThat(manager.getPhase()).isEqualTo(Integer.MIN_VALUE);
    }

    /**
     * 测试当有活跃任务时应记录线程池状态。
     */
    @Test
    @DisplayName("有活跃任务时应记录线程池状态并完成关闭")
    void shouldLogStatusWhenActiveTasksExist() {
        when(asyncExecutor.getActiveCount()).thenReturn(3);
        manager.start();
        manager.stop();
        assertThat(manager.isRunning()).isFalse();
    }
}
