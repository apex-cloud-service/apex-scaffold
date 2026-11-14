package com.apex.scaffold.interfaces.governance.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.apex.scaffold.common.governance.DrainParticipant;
import com.apex.scaffold.common.governance.DrainResult;
import com.apex.scaffold.interfaces.governance.config.GovernanceProperties;
import com.apex.scaffold.interfaces.governance.platform.DeploymentGovernanceStrategy;
import com.apex.scaffold.interfaces.governance.platform.DeploymentMode;
import com.apex.scaffold.interfaces.governance.platform.GovernanceCapabilities;
import com.apex.scaffold.interfaces.governance.platform.WithdrawalReason;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/**
 * 优雅关停协调器测试。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@DisplayName("GracefulShutdownCoordinator 单元测试")
class GracefulShutdownCoordinatorTest {

    /**
     * 验证关停顺序、统一 deadline 和排空成功状态。
     */
    @Test
    @DisplayName("应先摘流和停止 intake 再排空资源")
    void shouldWithdrawTrafficBeforeDrainingResources() {
        GovernanceProperties properties = properties(Duration.ofSeconds(1), true);
        DeploymentGovernanceStrategy strategy = strategy();
        ServiceLifecycleStateMachine stateMachine = new ServiceLifecycleStateMachine();
        ReadinessStateController readiness = mock(ReadinessStateController.class);
        ResourceDrainRegistry registry = mock(ResourceDrainRegistry.class);
        DrainParticipant participant = mock(DrainParticipant.class);
        when(registry.drain(any(Instant.class)))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(List.of(DrainResult.drained("worker"))));
        when(registry.participants()).thenReturn(List.of(participant));

        GracefulShutdownCoordinator coordinator =
                new GracefulShutdownCoordinator(properties, strategy, stateMachine, readiness, registry);
        coordinator.start();
        GracefulShutdownCoordinator.ShutdownResult result =
                coordinator.shutdown().toCompletableFuture().join();

        assertThat(result.drained()).isTrue();
        assertThat(stateMachine.current()).isEqualTo(ServiceLifecycleState.STOPPED);
        InOrder order = inOrder(readiness, strategy, registry);
        order.verify(readiness).refuseTraffic();
        order.verify(strategy).withdrawFromTraffic(WithdrawalReason.SHUTDOWN);
        order.verify(registry).stopAccepting();
        order.verify(registry).drain(any(Instant.class));
        verify(registry, never()).forceStop();
    }

    /**
     * 验证资源未在 deadline 内排空时执行强制停止。
     */
    @Test
    @DisplayName("排空超时后应强制停止资源并进入失败状态")
    void shouldForceStopWhenDrainTimesOut() {
        GovernanceProperties properties = properties(Duration.ZERO, true);
        DeploymentGovernanceStrategy strategy = strategy();
        ServiceLifecycleStateMachine stateMachine = new ServiceLifecycleStateMachine();
        ReadinessStateController readiness = mock(ReadinessStateController.class);
        ResourceDrainRegistry registry = mock(ResourceDrainRegistry.class);
        when(registry.drain(any(Instant.class)))
                .thenAnswer(invocation ->
                        CompletableFuture.completedFuture(List.of(DrainResult.timedOut("worker", "timeout"))));

        GracefulShutdownCoordinator coordinator =
                new GracefulShutdownCoordinator(properties, strategy, stateMachine, readiness, registry);
        coordinator.start();
        GracefulShutdownCoordinator.ShutdownResult result =
                coordinator.shutdown().toCompletableFuture().join();

        assertThat(result.drained()).isFalse();
        assertThat(stateMachine.current()).isEqualTo(ServiceLifecycleState.FAILED);
        verify(registry).forceStop();
    }

    /**
     * 验证重复关停调用不会重复操作资源。
     */
    @Test
    @DisplayName("重复关停应幂等")
    void shouldMakeRepeatedShutdownIdempotent() {
        GovernanceProperties properties = properties(Duration.ofSeconds(1), true);
        DeploymentGovernanceStrategy strategy = strategy();
        ServiceLifecycleStateMachine stateMachine = new ServiceLifecycleStateMachine();
        ReadinessStateController readiness = mock(ReadinessStateController.class);
        ResourceDrainRegistry registry = mock(ResourceDrainRegistry.class);
        when(registry.drain(any(Instant.class))).thenReturn(CompletableFuture.completedFuture(List.of()));

        GracefulShutdownCoordinator coordinator =
                new GracefulShutdownCoordinator(properties, strategy, stateMachine, readiness, registry);
        coordinator.start();
        coordinator.shutdown().toCompletableFuture().join();
        GracefulShutdownCoordinator.ShutdownResult repeated =
                coordinator.shutdown().toCompletableFuture().join();

        assertThat(repeated.participants()).isEmpty();
        verify(registry).drain(any(Instant.class));
        verify(registry, never()).forceStop();
    }

    private GovernanceProperties properties(Duration timeout, boolean forceStop) {
        GovernanceProperties properties = new GovernanceProperties();
        properties.getShutdown().setTotalTimeout(timeout);
        properties.getShutdown().setForceShutdownAfterTimeout(forceStop);
        return properties;
    }

    private DeploymentGovernanceStrategy strategy() {
        DeploymentGovernanceStrategy strategy = mock(DeploymentGovernanceStrategy.class);
        when(strategy.mode()).thenReturn(DeploymentMode.STANDALONE);
        when(strategy.capabilities()).thenReturn(new GovernanceCapabilities(true, false, false, true));
        return strategy;
    }
}
