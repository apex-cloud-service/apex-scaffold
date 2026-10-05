package com.apex.scaffold.workflow.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "工作流定义DTO，用于前端展示流程定义，包含节点和连线信息")
public class WorkflowDefinitionDTO {

    /** 流程定义 ID */
    @Schema(description = "流程定义ID", example = "1001")
    private Long id;

    /** 流程编码 */
    @Schema(description = "流程编码", example = "leave_approval")
    private String code;

    /** 流程名称 */
    @Schema(description = "流程名称", example = "请假审批流程")
    private String name;

    /** 流程描述 */
    @Schema(description = "流程描述", example = "员工请假申请审批流程，支持多级审批")
    private String description;

    /** 版本号 */
    @Schema(description = "流程版本号", example = "1")
    private Integer version;

    /** 是否启用 */
    @Schema(description = "是否启用：true-启用，false-停用", example = "true")
    private Boolean enabled;

    /** 节点列表 */
    @Schema(description = "流程节点列表")
    private List<WorkflowNodeDTO> nodes;

    /** 连线列表 */
    @Schema(description = "流程连线列表")
    private List<WorkflowEdgeDTO> edges;

    /** 节点定义 DTO */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "工作流节点定义DTO，描述流程定义中的单个节点配置")
    public static class WorkflowNodeDTO {
        /** 节点 ID */
        @Schema(description = "节点ID，在流程定义内唯一", example = "node_1")
        private String nodeId;

        /** 节点类型：START/APPROVAL/CONDITION/END */
        @Schema(description = "节点类型：START(开始)/APPROVAL(审批)/CONDITION(条件)/END(结束)", example = "APPROVAL")
        private String nodeType;

        /** 节点名称 */
        @Schema(description = "节点名称", example = "部门经理审批")
        private String nodeName;

        /** 审批人类型：SPECIFIED_USER/ROLE/SUPERIOR/INITIATOR */
        @Schema(description = "审批人类型：SPECIFIED_USER(指定用户)/ROLE(角色)/SUPERIOR(上级)/INITIATOR(发起人)", example = "ROLE")
        private String approverType;

        /** 审批人值（用户ID、角色ID等） */
        @Schema(description = "审批人值，即审批人类型对应的用户ID或角色ID", example = "200")
        private String approverValue;

        /** 审批策略：OR/AND */
        @Schema(description = "审批策略：OR(任一审批人通过即通过)/AND(会签，所有审批人通过才通过)", example = "OR")
        private String approvalStrategy;

        /** 超时小时数 */
        @Schema(description = "审批超时小时数", example = "24")
        private Integer timeoutHours;

        /** 超时动作 */
        @Schema(description = "超时动作：AUTO_APPROVE(自动通过)/AUTO_REJECT(自动驳回)/REMIND(提醒)", example = "REMIND")
        private String timeoutAction;

        /** 位置信息（前端用） */
        @Schema(description = "节点在流程图中的位置信息（前端渲染用），如 x、y 坐标")
        private Map<String, Object> position;
    }

    /** 连线定义 DTO */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "工作流连线定义DTO，描述流程定义中节点间的流转关系")
    public static class WorkflowEdgeDTO {
        /** 连线 ID */
        @Schema(description = "连线ID，在流程定义内唯一", example = "edge_1")
        private String edgeId;

        /** 源节点 ID */
        @Schema(description = "源节点ID", example = "node_1")
        private String sourceNodeId;

        /** 目标节点 ID */
        @Schema(description = "目标节点ID", example = "node_2")
        private String targetNodeId;

        /** 条件表达式 */
        @Schema(description = "流转条件表达式，如 #days > 3，为空表示无条件流转", example = "#days > 3")
        private String condition;
    }
}
