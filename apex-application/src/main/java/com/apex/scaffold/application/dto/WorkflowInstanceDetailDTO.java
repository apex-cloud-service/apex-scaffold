package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流实例详情 DTO。
 *
 * <p>工作流实例详情页的响应对象，聚合流程实例基本信息、流程定义、
 * 任务列表与当前节点，用于完整渲染单条流程实例的运行状态。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code instance}：流程实例基本信息。</li>
 *   <li>{@code definition}：流程定义，用于渲染流程图。</li>
 *   <li>{@code tasks}：任务列表。</li>
 *   <li>{@code currentNodeId}：当前节点 ID。</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "工作流实例详情DTO，聚合流程实例基本信息、流程定义、任务列表与当前节点，用于实例详情页展示")
public class WorkflowInstanceDetailDTO {

    /** 流程实例基本信息 */
    @Schema(description = "流程实例基本信息")
    private WorkflowInstanceDTO instance;

    /** 流程定义（用于渲染流程图） */
    @Schema(description = "流程定义，用于渲染流程图")
    private WorkflowDefinitionDTO definition;

    /** 任务列表 */
    @Schema(description = "实例下的任务列表")
    private List<WorkflowTaskDTO> tasks;

    /** 当前节点 ID */
    @Schema(description = "当前节点ID", example = "node_2")
    private String currentNodeId;
}
