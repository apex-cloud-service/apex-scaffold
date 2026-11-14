package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 密码找回确认重设请求。
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Data
@Schema(description = "密码找回确认重设请求")
public class PasswordRecoveryConfirmRequest {

    @NotBlank(message = "恢复单ID不能为空")
    @Schema(description = "恢复单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String recoveryId;

    @NotBlank(message = "确认令牌不能为空")
    @Schema(description = "校验阶段颁发的一次性确认令牌", requiredMode = Schema.RequiredMode.REQUIRED)
    private String confirmToken;

    @NotBlank(message = "新密码不能为空")
    @Schema(description = "新密码（须符合密码策略）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
