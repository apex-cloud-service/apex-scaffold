package com.apex.scaffold.officeservice.interfaces.dto.office.approval;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 撤销审批请求
 *
 * <p>用于接收撤销审批的HTTP请求参数。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "撤销审批请求")
public class CancelApprovalRequest {

    /**
     * 撤销原因
     */
    @NotBlank(message = "撤销原因不能为空")
    @Schema(description = "撤销原因", example = "申请有误，需重新提交", required = true)
    private String reason;
}
