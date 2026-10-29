package com.apex.scaffold.interfaces.governance.platform.kubernetes;

import com.apex.scaffold.interfaces.governance.lifecycle.ReadinessStateController;
import com.apex.scaffold.interfaces.governance.platform.DeploymentGovernanceStrategy;
import com.apex.scaffold.interfaces.governance.platform.DeploymentMode;
import com.apex.scaffold.interfaces.governance.platform.GovernanceCapabilities;
import com.apex.scaffold.interfaces.governance.platform.WithdrawalReason;

/**
 * Kubernetes 部署治理策略。
 *
 * <p>策略通过 Spring readiness 与 Kubernetes probe 协作，不直接调用 Kubernetes API。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@lombok.RequiredArgsConstructor
public class KubernetesGovernanceStrategy implements DeploymentGovernanceStrategy {
    private final ReadinessStateController readiness;

    /** @return Kubernetes 模式 */
    @Override
    public DeploymentMode mode() {
        return DeploymentMode.KUBERNETES;
    }

    /** @return Kubernetes 能力 */
    @Override
    public GovernanceCapabilities capabilities() {
        return new GovernanceCapabilities(true, true, true, true);
    }

    /**
     * 准备接收流量。
     */
    @Override
    public void prepareForTraffic() {
        readiness.acceptTraffic();
    }

    /**
     * 通过 readiness probe 摘除 Pod 流量。
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
     * 恢复 readiness probe。
     *
     * @return 是否完成恢复
     */
    @Override
    public boolean restoreTraffic() {
        readiness.acceptTraffic();
        return true;
    }
}
