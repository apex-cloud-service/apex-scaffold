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
     * 登录返回的 MFA 挑战凭据（短 TTL RS256 JWT，统一认证收口 A4 二步登录）
     *
     * <p>仅登录二步验证（{@code /auth/v1/mfa/verify}）消费：服务层校验缺失时拒绝。
     * {@code /api/v1/mfa/**} 自助端点不消费此字段，可空。
     */
    @Schema(description = "登录二步验证的 MFA 挑战凭据，仅 /auth/v1/mfa/verify 必填", example = "eyJhbGciOiJSUzI1NiJ9...")
    private String challengeToken;

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
