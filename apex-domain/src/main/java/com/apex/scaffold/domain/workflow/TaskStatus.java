package com.apex.scaffold.domain.workflow;

/**
 * 任务状态枚举。
 *
 * <p>定义工作流任务的所有可能状态。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum TaskStatus {
    /** 待处理 */
    PENDING,
    /** 已批准 */
    APPROVED,
    /** 已拒绝 */
    REJECTED,
    /** 已转交 */
    TRANSFERRED,
    /** 已取消 */
    CANCELLED
}
