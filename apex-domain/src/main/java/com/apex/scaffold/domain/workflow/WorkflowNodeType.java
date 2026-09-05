package com.apex.scaffold.domain.workflow;

/**
 * 工作流节点类型枚举。
 *
 * <p>定义工作流图中节点的类型（如开始节点、审批节点、结束节点等）。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum WorkflowNodeType {
    START,
    APPROVAL,
    CONDITION,
    END
}
