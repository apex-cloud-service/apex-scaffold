package com.apex.scaffold.domain.workflow;

import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;

/**
 * 工作流节点值对象。
 *
 * <p>描述工作流定义中的单个节点配置。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class WorkflowNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String nodeId;
    private WorkflowNodeType nodeType;
    private String nodeName;
    private ApproverType approverType;
    private String approverValue;
    private ApprovalStrategy approvalStrategy;
    private Integer timeoutHours;
    private String timeoutAction;

    protected WorkflowNode() {}

    /**
     * 创建基础工作流节点
     *
     * @param nodeId 节点唯一标识
     * @param nodeType 节点类型（开始/审批/条件/结束）
     * @param nodeName 节点名称
     * @return 已初始化的工作流节点
     */
    public static WorkflowNode create(String nodeId, WorkflowNodeType nodeType, String nodeName) {
        WorkflowNode node = new WorkflowNode();
        node.nodeId = nodeId;
        node.nodeType = nodeType;
        node.nodeName = nodeName;
        return node;
    }

    /**
     * 创建审批节点
     *
     * @param nodeId 节点唯一标识
     * @param nodeName 节点名称
     * @param approverType 审批人类型（指定用户/角色/上级/发起人）
     * @param approverValue 审批人取值（含义由审批人类型决定，如用户 ID 或角色标识）
     * @param approvalStrategy 审批通过策略，为 null 时默认使用或策略（OR）
     * @return 已初始化的审批节点
     */
    public static WorkflowNode createApprovalNode(
            String nodeId,
            String nodeName,
            ApproverType approverType,
            String approverValue,
            ApprovalStrategy approvalStrategy) {
        WorkflowNode node = create(nodeId, WorkflowNodeType.APPROVAL, nodeName);
        node.approverType = approverType;
        node.approverValue = approverValue;
        node.approvalStrategy = approvalStrategy != null ? approvalStrategy : ApprovalStrategy.OR;
        return node;
    }

    /**
     * 设置节点超时配置
     *
     * @param timeoutHours 超时小时数
     * @param timeoutAction 超时后的处理动作
     */
    public void setTimeout(Integer timeoutHours, String timeoutAction) {
        this.timeoutHours = timeoutHours;
        this.timeoutAction = timeoutAction;
    }

    /**
     * 设置节点唯一标识
     *
     * @param nodeId 节点唯一标识
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 设置节点类型
     *
     * @param nodeType 节点类型（开始/审批/条件/结束）
     */
    public void setNodeType(WorkflowNodeType nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * 设置节点名称
     *
     * @param nodeName 节点名称
     */
    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    /**
     * 设置审批人类型
     *
     * @param approverType 审批人类型（指定用户/角色/上级/发起人）
     */
    public void setApproverType(ApproverType approverType) {
        this.approverType = approverType;
    }

    /**
     * 设置审批人取值
     *
     * @param approverValue 审批人取值（含义由审批人类型决定，如用户 ID 或角色标识）
     */
    public void setApproverValue(String approverValue) {
        this.approverValue = approverValue;
    }

    /**
     * 设置审批通过策略
     *
     * @param approvalStrategy 审批通过策略（或策略/与策略）
     */
    public void setApprovalStrategy(ApprovalStrategy approvalStrategy) {
        this.approvalStrategy = approvalStrategy;
    }

    /**
     * 设置超时小时数
     *
     * @param timeoutHours 超时小时数
     */
    public void setTimeoutHours(Integer timeoutHours) {
        this.timeoutHours = timeoutHours;
    }

    /**
     * 设置超时后的处理动作
     *
     * @param timeoutAction 超时后的处理动作
     */
    public void setTimeoutAction(String timeoutAction) {
        this.timeoutAction = timeoutAction;
    }
}
