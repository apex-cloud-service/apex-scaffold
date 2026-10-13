package com.apex.scaffold.office.domain.approval;

/**
 * 审批状态枚举
 *
 * <p>定义审批流程的所有可能状态，覆盖从发起到完成的完整生命周期。
 *
 * @author Apex Team
 * @since 1.3.0
 */
public enum ApprovalStatus {

    /**
     * 待审批
     */
    PENDING("待审批"),

    /**
     * 审批中
     */
    IN_PROGRESS("审批中"),

    /**
     * 已通过
     */
    APPROVED("已通过"),

    /**
     * 已拒绝
     */
    REJECTED("已拒绝"),

    /**
     * 已撤销
     */
    CANCELLED("已撤销"),

    /**
     * 已转审
     */
    TRANSFERRED("已转审");

    private final String description;

    ApprovalStatus(String description) {
        this.description = description;
    }

    /**
     * 获取状态描述
     *
     * @return 状态描述
     */
    public String getDescription() {
        return description;
    }
}
