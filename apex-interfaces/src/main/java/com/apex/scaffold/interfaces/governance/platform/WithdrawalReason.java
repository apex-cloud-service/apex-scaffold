package com.apex.scaffold.interfaces.governance.platform;

/**
 * 实例摘流原因。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum WithdrawalReason {
    /** 应用收到正常停止信号。 */
    SHUTDOWN,
    /** 应用主动进入降级状态。 */
    DEGRADATION,
    /** 运维主动摘除实例。 */
    ADMINISTRATION
}
