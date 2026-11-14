package com.apex.scaffold.workflowservice.interfaces.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新流程定义草稿请求 DTO。
 *
 * <p>仅草稿状态允许更新；流程编码与版本号不可修改。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Schema(description = "更新流程定义草稿请求")
public class UpdateWorkflowDefinitionRequest {

    @NotBlank(message = "流程名称不能为空")
    @Size(max = 200, message = "流程名称不能超过200字")
    @Schema(description = "流程名称", example = "请假审批流程", requiredMode = Schema.RequiredMode.REQUIRED)
    private String workflowName;

    @Size(max = 500, message = "流程描述不能超过500字")
    @Schema(description = "流程描述", example = "员工请假申请审批流程")
    private String description;

    @NotBlank(message = "流程定义 JSON 不能为空")
    @Size(max = 60000, message = "流程定义 JSON 过大")
    @Schema(
            description = "DAG JSON，包含 nodes 与 edges；首期仅支持 START/APPROVAL/END 单链",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String dagJson;
}
