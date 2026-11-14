package com.apex.scaffold.interfaces.governance.platform.standalone;

import com.apex.scaffold.interfaces.governance.lifecycle.ReadinessStateController;
import com.apex.scaffold.interfaces.governance.platform.DeploymentGovernanceStrategy;
import com.apex.scaffold.interfaces.governance.platform.DeploymentMode;
import com.apex.scaffold.interfaces.governance.platform.GovernanceCapabilities;
import com.apex.scaffold.interfaces.governance.platform.WithdrawalReason;

/**
 * java -jar 或 systemd 部署治理策略。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@lombok.RequiredArgsConstructor
public class StandaloneGovernanceStrategy implements DeploymentGovernanceStrategy {
    private final ReadinessStateController readiness;

    /** @return standalone 模式 */
    @Override
    public DeploymentMode mode() {
        return DeploymentMode.STANDALONE;
    }

    /**
     * 允许本地实例接收流量。
     */
    @Override
    public void prepareForTraffic() {
        readiness.acceptTraffic();
    }

    /**
     * 通过本地 readiness 摘流。
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
     * 恢复本地 readiness。
     *
     * @return 是否完成恢复
     */
    @Override
    public boolean restoreTraffic() {
        readiness.acceptTraffic();
        return true;
    }

    /** @return standalone 能力 */
    @Override
    public GovernanceCapabilities capabilities() {
        return new GovernanceCapabilities(true, false, false, true);
    }
}
