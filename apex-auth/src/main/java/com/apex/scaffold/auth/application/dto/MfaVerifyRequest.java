package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MFA 验证请求 DTO
 *
 * <p>用于验证 TOTP 验证码或备份码。
 *
 * @author Apex Scaffold Team
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MFA 验证请求")
public class MfaVerifyRequest {

    /**
     * 用户 ID
     */
    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123")
    private Long userId;

    /**
     * TOTP 验证码（6 位数字）
     *
     * <p>与 backupCode 二选一
     */
    @Size(min = 6, max = 6, message = "验证码必须是6位")
    @Schema(description = "TOTP 验证码（6位数字）", example = "123456")
    private String code;

    /**
     * 备份码（8 位数字）
     *
     * <p>与 code 二选一
     */
    @Size(min = 8, max = 8, message = "备份码必须是8位")
    @Schema(description = "备份码（8位数字）", example = "12345678")
    private String backupCode;

    /**
     * 租户 ID
     */
    @NotBlank(message = "租户ID不能为空")
    @Schema(description = "租户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "tenant-001")
    private String tenantId;
}
