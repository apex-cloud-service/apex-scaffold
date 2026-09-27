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

    /**
     * 创建无条件流转的工作流边。
     *
     * @param edgeId 边唯一标识
     * @param sourceNodeId 源节点 ID
     * @param targetNodeId 目标节点 ID
     * @return 新创建的工作流边对象
     */
    public static WorkflowEdge create(String edgeId, String sourceNodeId, String targetNodeId) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.edgeId = edgeId;
        edge.sourceNodeId = sourceNodeId;
        edge.targetNodeId = targetNodeId;
        return edge;
    }

    /**
     * 创建带流转条件的工作流边。
     *
     * @param edgeId 边唯一标识
     * @param sourceNodeId 源节点 ID
     * @param targetNodeId 目标节点 ID
     * @param condition 流转条件表达式，满足条件时才允许沿该边流转
     * @return 新创建的带条件工作流边对象
     */
    public static WorkflowEdge createConditional(
            String edgeId, String sourceNodeId, String targetNodeId, String condition) {
        WorkflowEdge edge = create(edgeId, sourceNodeId, targetNodeId);
        edge.condition = condition;
        return edge;
    }

    /**
     * 设置边唯一标识。
     *
     * @param edgeId 边唯一标识
     */
    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    /**
     * 设置源节点 ID。
     *
     * @param sourceNodeId 源节点 ID
     */
    public void setSourceNodeId(String sourceNodeId) {
        this.sourceNodeId = sourceNodeId;
    }

    /**
     * 设置目标节点 ID。
     *
     * @param targetNodeId 目标节点 ID
     */
    public void setTargetNodeId(String targetNodeId) {
        this.targetNodeId = targetNodeId;
    }

    /**
     * 设置流转条件表达式。
     *
     * @param condition 流转条件表达式，满足条件时才允许沿该边流转
     */
    public void setCondition(String condition) {
        this.condition = condition;
    }
}
