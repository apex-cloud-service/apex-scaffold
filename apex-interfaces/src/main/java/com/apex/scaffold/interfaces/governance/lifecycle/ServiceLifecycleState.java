package com.apex.scaffold.interfaces.governance.lifecycle;

/**
 * 服务生命周期状态。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ServiceLifecycleState {
    /** 正在启动。 */
    STARTING,
    /** 可以接收流量。 */
    READY,
    /** 部分能力降级。 */
    DEGRADED,
    /** 暂停接收流量。 */
    OUT_OF_SERVICE,
    /** 正在排空。 */
    DRAINING,
    /** 启动或运行失败。 */
    FAILED,
    /** 已停止。 */
    STOPPED
}
