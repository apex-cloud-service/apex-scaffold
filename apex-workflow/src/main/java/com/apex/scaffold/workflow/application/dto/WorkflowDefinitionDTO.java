package com.apex.scaffold.workflow.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
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
 * @version 1.3.2
 * @since 1.3.2
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

    /** 生命周期状态 */
    @Schema(description = "生命周期状态：DRAFT(草稿)/PUBLISHED(已发布)/DISABLED(已停用)", example = "PUBLISHED")
    private String status;

    /** 发布时间，未发布时为空 */
    @Schema(description = "发布时间，未发布时为空")
    private LocalDateTime publishedAt;

    /** 发布人用户 ID，未发布时为空 */
    @Schema(description = "发布人用户ID，未发布时为空", example = "1")
    private Long publishedBy;

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

        /** 节点类型，首期执行器只支持 START/APPROVAL/END */
        @Schema(description = "节点类型：START(开始)/APPROVAL(审批)/END(结束)，首期不支持条件分支节点", example = "APPROVAL")
        private String nodeType;

        /** 节点名称 */
        @Schema(description = "节点名称", example = "部门经理审批")
        private String nodeName;

        /** 审批人类型，首期只支持指定用户与发起人 */
        @Schema(
                description = "审批人类型：SPECIFIED_USER(指定用户)/INITIATOR(发起人)，首期不支持 ROLE 与 SUPERIOR",
                example = "SPECIFIED_USER")
        private String approverType;

        /** 审批人值：指定用户时为单个正整数用户 ID */
        @Schema(
                description = "审批人值：approverType=SPECIFIED_USER 时为单个正整数用户ID；approverType=INITIATOR 时忽略",
                example = "200")
        private String approverValue;

        /** 审批策略，首期只支持单办理人 */
        @Schema(description = "审批策略：SINGLE(单办理人)。首期执行器不支持 OR/AND 多人审批，留空等同于 SINGLE", example = "SINGLE")
        private String approvalStrategy;

        /** 超时小时数 */
        @Schema(description = "审批超时小时数，正整数；设置 timeoutAction 时必须同时设置", example = "24")
        private Integer timeoutHours;

        /** 超时动作，与执行器支持的超时策略一致 */
        @Schema(description = "超时动作：AUTO_COMPLETE(自动通过)/ESCALATE(升级办理人)，需配合 timeoutHours 使用", example = "AUTO_COMPLETE")
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
