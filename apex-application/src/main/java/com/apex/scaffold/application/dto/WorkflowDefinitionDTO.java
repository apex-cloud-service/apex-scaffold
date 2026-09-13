package com.apex.scaffold.application.dto;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流定义 DTO。
 *
 * <p>用于前端展示流程定义，包含节点和连线信息。</p>
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**n * WorkflowDefinitionDTOn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class WorkflowDefinitionDTO {

    /** 流程定义 ID */
    private Long id;

    /** 流程编码 */
    private String code;

    /** 流程名称 */
    private String name;

    /** 流程描述 */
    private String description;

    /** 版本号 */
    private Integer version;

    /** 是否启用 */
    private Boolean enabled;

    /** 节点列表 */
    private List<WorkflowNodeDTO> nodes;

    /** 连线列表 */
    private List<WorkflowEdgeDTO> edges;

    /** 节点定义 DTO */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowNodeDTO {
        /** 节点 ID */
        private String nodeId;

        /** 节点类型：START/APPROVAL/CONDITION/END */
        private String nodeType;

        /** 节点名称 */
        private String nodeName;

        /** 审批人类型：SPECIFIED_USER/ROLE/SUPERIOR/INITIATOR */
        private String approverType;

        /** 审批人值（用户ID、角色ID等） */
        private String approverValue;

        /** 审批策略：OR/AND */
        private String approvalStrategy;

        /** 超时小时数 */
        private Integer timeoutHours;

        /** 超时动作 */
        private String timeoutAction;

        /** 位置信息（前端用） */
        private Map<String, Object> position;
    }

    /** 连线定义 DTO */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkflowEdgeDTO {
        /** 连线 ID */
        private String edgeId;

        /** 源节点 ID */
        private String sourceNodeId;

        /** 目标节点 ID */
        private String targetNodeId;

        /** 条件表达式 */
        private String condition;
    }
}
