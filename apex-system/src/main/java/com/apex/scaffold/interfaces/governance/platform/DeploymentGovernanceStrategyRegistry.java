package com.apex.scaffold.interfaces.governance.platform;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 部署治理策略注册表。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public class DeploymentGovernanceStrategyRegistry {
    private final Map<DeploymentMode, DeploymentGovernanceStrategy> strategies;
    private final DeploymentGovernanceStrategy activeStrategy;

    /**
     * 创建策略注册表并选择最终策略。
     *
     * @param availableStrategies 可用策略
     * @param mode 最终部署模式
     */
    public DeploymentGovernanceStrategyRegistry(
            List<DeploymentGovernanceStrategy> availableStrategies, DeploymentMode mode) {
        this.strategies = availableStrategies.stream()
                .collect(Collectors.toUnmodifiableMap(
                        DeploymentGovernanceStrategy::mode, Function.identity(), (first, second) -> {
                            throw new IllegalStateException("部署治理策略重复注册: " + first.mode());
                        }));
        this.activeStrategy = strategies.get(mode);
        if (activeStrategy == null) {
            throw new IllegalStateException("未找到部署治理策略: " + mode);
        }
    }

    /**
     * 返回当前活动策略。
     *
     * @return 活动策略
     */
    public DeploymentGovernanceStrategy activeStrategy() {
        return activeStrategy;
    }

    /**
     * 返回当前部署模式。
     *
     * @return 部署模式
     */
    public DeploymentMode mode() {
        return activeStrategy.mode();
    }

    /**
     * 返回当前平台能力矩阵。
     *
     * @return 能力矩阵
     */
    public GovernanceCapabilities capabilities() {
        return activeStrategy.capabilities();
    }
}
