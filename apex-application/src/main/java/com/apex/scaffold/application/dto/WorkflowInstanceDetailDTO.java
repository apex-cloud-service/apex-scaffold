package com.apex.scaffold.application.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流实例详情 DTO。
 *
 * <p>包含流程实例、流程定义、任务列表等完整信息，用于详情页展示。</p>
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceDetailDTO {

    /** 流程实例基本信息 */
    private WorkflowInstanceDTO instance;

    /** 流程定义（用于渲染流程图） */
    private WorkflowDefinitionDTO definition;

    /** 任务列表 */
    private List<WorkflowTaskDTO> tasks;

    /** 当前节点 ID */
    private String currentNodeId;
}
