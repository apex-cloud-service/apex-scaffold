package com.apex.scaffold.workflowservice.interfaces.dto.workflow;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建流程定义草稿请求 DTO。
 *
 * <p>新草稿在同一流程编码下自动获得下一个版本号，创建后状态为 DRAFT 且未启用；
 * 草稿发布前可继续编辑，发布后该版本内容不可变。</p>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Data
@Schema(description = "创建流程定义草稿请求")
public class CreateWorkflowDefinitionRequest {

    @NotBlank(message = "流程编码不能为空")
    @Size(max = 100, message = "流程编码不能超过100字")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_-]*$", message = "流程编码必须以字母开头，且只允许字母、数字、下划线和短横线")
    @Schema(
            description = "流程编码，同一租户内相同编码按版本管理",
            example = "leave_approval",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String workflowCode;

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
