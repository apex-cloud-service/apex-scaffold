package com.apex.scaffold.interfaces.governance.lifecycle;

import com.apex.scaffold.common.governance.DrainResult;
import com.apex.scaffold.interfaces.governance.config.GovernanceProperties;
import com.apex.scaffold.interfaces.governance.platform.DeploymentGovernanceStrategy;
import com.apex.scaffold.interfaces.governance.platform.WithdrawalReason;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一优雅关停协调器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class GracefulShutdownCoordinator {
    private final GovernanceProperties properties;
    private final DeploymentGovernanceStrategy strategy;
    private final ServiceLifecycleStateMachine stateMachine;
    private final ReadinessStateController readiness;
    private final ResourceDrainRegistry registry;
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    /**
     * 初始化并进入可接收流量状态。
     */
    public void start() {
        if (stateMachine.transitionTo(ServiceLifecycleState.READY)) {
            strategy.prepareForTraffic();
        }
    }

    /**
     * 执行一次幂等优雅关停。
     *
     * @return 关停排空结果
     */
    public CompletionStage<ShutdownResult> shutdown() {
        if (!stopping.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(ShutdownResult.alreadyStopped());
        }
        stateMachine.transitionTo(ServiceLifecycleState.DRAINING);
        readiness.refuseTraffic();
        strategy.withdrawFromTraffic(WithdrawalReason.SHUTDOWN);
        registry.stopAccepting();
        Instant deadline = Instant.now().plus(properties.getShutdown().getTotalTimeout());
        return registry.drain(deadline).thenApply(results -> finish(deadline, results));
    }

    /**
     * 返回是否已经进入关停流程。
     *
     * @return 是否正在关停
     */
    public boolean isStopping() {
        return stopping.get();
    }

    private ShutdownResult finish(Instant deadline, java.util.List<DrainResult> results) {
        boolean drained = Instant.now().isBefore(deadline) && results.stream().allMatch(DrainResult::drained);
        if (!drained && properties.getShutdown().isForceShutdownAfterTimeout()) {
            registry.forceStop();
        }
        stateMachine.transitionTo(drained ? ServiceLifecycleState.STOPPED : ServiceLifecycleState.FAILED);
        log.info("服务关停完成, mode={}, drained={}, participantCount={}", strategy.mode(), drained, results.size());
        return new ShutdownResult(drained, results);
    }

    /**
     * 关停结果。
     *
     * @param drained 是否在截止时间内完成排空
     * @param participants 各参与者的排空结果
     * @author Apex Team
     * @version 1.0.0
     * @since 1.0.0
     */
    public record ShutdownResult(boolean drained, java.util.List<DrainResult> participants) {
        /**
         * 创建重复关停结果。
         *
         * @return 已经停止结果
         */
        public static ShutdownResult alreadyStopped() {
            return new ShutdownResult(true, java.util.List.of());
        }
    }
}
