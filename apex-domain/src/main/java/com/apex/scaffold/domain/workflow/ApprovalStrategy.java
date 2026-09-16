package com.apex.scaffold.domain.workflow;

/**
 * 审批策略枚举。
 *
 * <p>定义多人审批时的决策策略（如一票通过、一票否决等）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum ApprovalStrategy {
    /** 或策略（一票通过） */
    OR,
    /** 与策略（全票通过） */
    AND
}
