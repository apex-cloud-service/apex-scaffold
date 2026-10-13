package com.apex.scaffold.interfaces.governance.platform;

/**
 * 部署平台治理策略。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface DeploymentGovernanceStrategy {
    /**
     * 返回策略对应的部署模式。
     *
     * @return 部署模式
     */
    DeploymentMode mode();

    /**
     * 准备接收流量。
     */
    void prepareForTraffic();

    /**
     * 从流量中摘除当前实例。
     *
     * @param reason 摘流原因
     * @return 是否完成本地摘流
     */
    boolean withdrawFromTraffic(WithdrawalReason reason);

    /**
     * 恢复接收流量。
     *
     * @return 是否支持并完成恢复
     */
    boolean restoreTraffic();

    /**
     * 返回平台能力。
     *
     * @return 能力矩阵
     */
    GovernanceCapabilities capabilities();
}
