package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MFA 设置请求 DTO
 *
 * <p>用于初始化 TOTP MFA 设置，生成密钥和二维码。
 *
 * @author Apex Scaffold Team
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "MFA 设置请求")
public class MfaSetupRequest {

    /**
     * 用户 ID
     */
    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "123")
    private Long userId;

    /**
     * 租户 ID
     */
    @NotBlank(message = "租户ID不能为空")
    @Schema(description = "租户 ID", requiredMode = Schema.RequiredMode.REQUIRED, example = "tenant-001")
    private String tenantId;

    /**
     * 发行方名称（显示在认证器应用中）
     */
    @NotBlank(message = "发行方名称不能为空")
    @Schema(description = "发行方名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "Apex")
    private String issuer;

    /**
     * 账户名（显示在认证器应用中）
     */
    @NotBlank(message = "账户名不能为空")
    @Schema(description = "账户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "user@example.com")
    private String accountName;
}
