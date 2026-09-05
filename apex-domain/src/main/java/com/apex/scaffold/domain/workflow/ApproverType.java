package com.apex.scaffold.domain.workflow;

/**
 * 审批人类型枚举。
 *
 * <p>定义审批人的指定方式（如用户、角色、部门等）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ApproverType {
    SPECIFIED_USER,
    ROLE,
    SUPERIOR,
    INITIATOR
}
