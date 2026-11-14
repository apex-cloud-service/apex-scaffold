package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 密码找回验证码校验请求。
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Data
@Schema(description = "密码找回验证码校验请求")
public class PasswordRecoveryVerifyRequest {

    @NotBlank(message = "恢复单ID不能为空")
    @Schema(description = "恢复单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String recoveryId;

    @NotBlank(message = "验证码不能为空")
    @Schema(description = "一次性验证码", example = "482913", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;
}
