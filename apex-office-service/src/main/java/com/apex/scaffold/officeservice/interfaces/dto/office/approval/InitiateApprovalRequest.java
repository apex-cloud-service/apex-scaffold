package com.apex.scaffold.officeservice.interfaces.dto.office.approval;

import com.apex.scaffold.office.domain.OfficePlatform;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发起审批请求DTO
 *
 * <p>用于接收前端发起审批的请求参数。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发起审批请求")
public class InitiateApprovalRequest {

    /**
     * 办公平台类型
     */
    @NotNull(message = "办公平台类型不能为空")
    @Schema(description = "办公平台类型", example = "FEISHU", required = true)
    private OfficePlatform platform;

    /**
     * 应用ID
     */
    @NotNull(message = "应用ID不能为空")
    @Schema(description = "应用ID", example = "1", required = true)
    private Long appId;

    /**
     * 流程定义ID
     */
    @NotBlank(message = "流程定义ID不能为空")
    @Schema(description = "流程定义ID（模板ID）", example = "approval_template_001", required = true)
    private String processDefinitionId;

    /**
     * 发起人ID
     */
    @NotBlank(message = "发起人ID不能为空")
    @Schema(description = "发起人ID", example = "user_001", required = true)
    private String initiatorId;

    /**
     * 表单数据
     */
    @NotBlank(message = "表单数据不能为空")
    @Schema(description = "表单数据（JSON格式）", example = "{\"title\":\"请假申请\",\"days\":3}", required = true)
    private String formData;
}
