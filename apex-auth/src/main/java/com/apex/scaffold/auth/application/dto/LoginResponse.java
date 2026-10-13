package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 *
 * <p>登录成功后返回的用户信息和Token
 *
 * @author Apex Team
 * @since 2026-09-20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "用户ID", example = "1001")
    private Long userId;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "租户ID", example = "default")
    private String tenantId;

    /**
     * 统一账户ID（主应用 sys_user.id）。
     *
     * <p>PC 管理端统一账户体系关联：主应用据此装配统一身份与 RBAC 权限；
     * 尚未与主应用账户建立映射时为空。
     *
     * @since 3.3.0
     */
    @Schema(description = "统一账户ID（主应用 sys_user.id，未映射时为空）")
    private Long accountId;

    @Schema(description = "访问令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String accessToken;

    @Schema(description = "刷新令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String refreshToken;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "访问令牌有效期（秒）", example = "7200")
    private Long expiresIn;
}
