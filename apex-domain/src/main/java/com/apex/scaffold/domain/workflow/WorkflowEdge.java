package com.apex.scaffold.domain.workflow;

import java.io.Serial;
import java.io.Serializable;
import lombok.Getter;

/**
 * 工作流边值对象。
 *
 * <p>描述工作流节点之间的连接关系和流转条件。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Getter
public class WorkflowEdge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String edgeId;
    private String sourceNodeId;
    private String targetNodeId;
    private String condition;

    protected WorkflowEdge() {}

    public static WorkflowEdge create(String edgeId, String sourceNodeId, String targetNodeId) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.edgeId = edgeId;
        edge.sourceNodeId = sourceNodeId;
        edge.targetNodeId = targetNodeId;
        return edge;
    }

    public static WorkflowEdge createConditional(
            String edgeId, String sourceNodeId, String targetNodeId, String condition) {
        WorkflowEdge edge = create(edgeId, sourceNodeId, targetNodeId);
        edge.condition = condition;
        return edge;
    }

    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }
}
