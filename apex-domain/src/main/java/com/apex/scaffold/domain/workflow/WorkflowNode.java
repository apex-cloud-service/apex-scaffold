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

    public static WorkflowNode create(String nodeId, WorkflowNodeType nodeType, String nodeName) {
        WorkflowNode node = new WorkflowNode();
        node.nodeId = nodeId;
        node.nodeType = nodeType;
        node.nodeName = nodeName;
        return node;
    }

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

    public void setTimeout(Integer timeoutHours, String timeoutAction) {
        this.timeoutHours = timeoutHours;
        this.timeoutAction = timeoutAction;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public void setNodeType(WorkflowNodeType nodeType) {
        this.nodeType = nodeType;
    }

    public void setNodeName(String nodeName) {
        this.nodeName = nodeName;
    }

    public void setApproverType(ApproverType approverType) {
        this.approverType = approverType;
    }

    public void setApproverValue(String approverValue) {
        this.approverValue = approverValue;
    }

    public void setApprovalStrategy(ApprovalStrategy approvalStrategy) {
        this.approvalStrategy = approvalStrategy;
    }

    public void setTimeoutHours(Integer timeoutHours) {
        this.timeoutHours = timeoutHours;
    }

    public void setTimeoutAction(String timeoutAction) {
        this.timeoutAction = timeoutAction;
    }
}
