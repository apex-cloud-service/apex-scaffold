package com.apex.scaffold.interfaces.dto.office.approval;

import com.apex.scaffold.domain.office.approval.ApprovalAction;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 审批操作请求DTO
 *
 * <p>用于接收审批人执行审批操作（同意/拒绝/转审等）的请求参数。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "审批操作请求")
public class ApproveRequest {
    
    /**
     * 审批人ID
     */
    @NotBlank(message = "审批人ID不能为空")
    @Schema(description = "审批人ID", example = "user_002", required = true)
    private String approverId;
    
    /**
     * 审批操作
     */
    @NotNull(message = "审批操作不能为空")
    @Schema(description = "审批操作", example = "APPROVE", required = true)
    private ApprovalAction action;
    
    /**
     * 审批意见
     */
    @Schema(description = "审批意见", example = "同意")
    private String comment;
}
