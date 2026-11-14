package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 密码找回申请响应。对账号存在与否保持同一响应形态，防止账号枚举。
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "密码找回申请响应")
public class PasswordRecoveryInitiateResponse {

    @Schema(description = "恢复单ID", example = "b1e0a6d4-6f2c-4a5e-9d3a-1f2b3c4d5e6f")
    private String recoveryId;

    @Schema(description = "验证码有效期（秒）", example = "600")
    private Long expiresIn;
}
