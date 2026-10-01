package com.apex.scaffold.authcenter.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MFA 设置响应 DTO
 *
 * <p>返回 TOTP 密钥、OTP Auth URL 和备份码，用于前端显示二维码和备份码。
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MFA 设置响应")
public class MfaSetupResponse {

    /**
     * TOTP 密钥（Base32 编码）
     */
    @Schema(description = "TOTP 密钥", example = "JBSWY3DPEHPK3PXP")
    private String secret;

    /**
     * OTP Auth URL（用于生成二维码）
     */
    @Schema(description = "OTP Auth URL", example = "otpauth://totp/Apex:user@example.com?secret=...")
    private String otpAuthUrl;

    /**
     * 备份码列表
     */
    @Schema(description = "备份码列表")
    private List<String> backupCodes;
}
