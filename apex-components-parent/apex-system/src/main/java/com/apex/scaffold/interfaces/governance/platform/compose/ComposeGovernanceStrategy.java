package com.apex.scaffold.interfaces.governance.platform.compose;

import com.apex.scaffold.interfaces.governance.lifecycle.ReadinessStateController;
import com.apex.scaffold.interfaces.governance.platform.DeploymentGovernanceStrategy;
import com.apex.scaffold.interfaces.governance.platform.DeploymentMode;
import com.apex.scaffold.interfaces.governance.platform.GovernanceCapabilities;
import com.apex.scaffold.interfaces.governance.platform.WithdrawalReason;

/**
 * Docker Compose 部署治理策略。
 *
 * <p>策略只维护应用 readiness；容器生命周期和副本数由 Compose 或外部控制器管理。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@lombok.RequiredArgsConstructor
public class ComposeGovernanceStrategy implements DeploymentGovernanceStrategy {
    private final ReadinessStateController readiness;

    /** @return Compose 模式 */
    @Override
    public DeploymentMode mode() {
        return DeploymentMode.COMPOSE;
    }

    /** @return Compose 能力 */
    @Override
    public GovernanceCapabilities capabilities() {
        return new GovernanceCapabilities(true, true, false, true);
    }

    /**
     * 准备接收流量。
     */
    @Override
    public void prepareForTraffic() {
        readiness.acceptTraffic();
    }

    /**
     * 通过 healthcheck 可见的 readiness 摘流。
     *
     * @param reason 摘流原因
     * @return 是否完成摘流
     */
    @Override
    public boolean withdrawFromTraffic(WithdrawalReason reason) {
        readiness.refuseTraffic();
        return true;
    }

    /**
     * 恢复 readiness。
     *
     * @return 是否完成恢复
     */
    @Override
    public boolean restoreTraffic() {
        readiness.acceptTraffic();
        return true;
    }
}
