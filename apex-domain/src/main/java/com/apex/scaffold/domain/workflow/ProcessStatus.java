package com.apex.scaffold.domain.workflow;

/**
 * 流程状态枚举。
 *
 * <p>定义工作流流程实例的所有可能状态。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ProcessStatus {
    /** 草稿 */
    DRAFT,
    /** 运行中 */
    RUNNING,
    /** 已批准 */
    APPROVED,
    /** 已拒绝 */
    REJECTED,
    /** 已取消 */
    CANCELLED,
    /** 已终止 */
    TERMINATED
}
