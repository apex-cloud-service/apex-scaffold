package com.apex.scaffold.interfaces.governance.lifecycle;

import com.apex.scaffold.common.governance.DrainResult;
import com.apex.scaffold.interfaces.governance.config.GovernanceProperties;
import com.apex.scaffold.interfaces.governance.platform.DeploymentGovernanceStrategy;
import com.apex.scaffold.interfaces.governance.platform.WithdrawalReason;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 统一优雅关停协调器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class GracefulShutdownCoordinator {
    private static final Logger LOGGER = LoggerFactory.getLogger(GracefulShutdownCoordinator.class);
    private final GovernanceProperties properties;
    private final DeploymentGovernanceStrategy strategy;
    private final ServiceLifecycleStateMachine stateMachine;
    private final ReadinessStateController readiness;
    private final ResourceDrainRegistry registry;
    private final AtomicBoolean stopping = new AtomicBoolean(false);

    /**
     * 创建关停协调器。
     *
     * @param properties 治理配置
     * @param strategy 部署治理策略
     * @param stateMachine 生命周期状态机
     * @param readiness readiness 控制器
     * @param registry 资源排空注册表
     */
    public GracefulShutdownCoordinator(
            GovernanceProperties properties,
            DeploymentGovernanceStrategy strategy,
            ServiceLifecycleStateMachine stateMachine,
            ReadinessStateController readiness,
            ResourceDrainRegistry registry) {
        this.properties = properties;
        this.strategy = strategy;
        this.stateMachine = stateMachine;
        this.readiness = readiness;
        this.registry = registry;
    }

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
        LOGGER.info("服务关停完成, mode={}, drained={}, participantCount={}", strategy.mode(), drained, results.size());
        return new ShutdownResult(drained, results);
    }

    /**
     * 关停结果。
     *
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
