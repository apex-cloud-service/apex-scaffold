package com.apex.scaffold.authcenter.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建认证配置请求DTO
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "创建认证配置请求")
public class CreateAuthConfigRequest {

    @NotBlank(message = "租户ID不能为空")
    @Schema(description = "租户ID", example = "tenant-001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String tenantId;

    @NotBlank(message = "配置类型不能为空")
    @Schema(
            description = "配置类型",
            example = "PASSWORD_POLICY",
            requiredMode = Schema.RequiredMode.REQUIRED,
            allowableValues = {"PASSWORD_POLICY", "SESSION_POLICY", "OAUTH2_CLIENT", "SECURITY_POLICY", "MFA_POLICY"})
    private String configType;

    @NotBlank(message = "配置键不能为空")
    @Schema(description = "配置键", example = "default", requiredMode = Schema.RequiredMode.REQUIRED)
    private String configKey;

    @NotBlank(message = "配置值不能为空")
    @Schema(
            description = "配置值（JSON格式）",
            example = "{\"minLength\":8,\"requireUppercase\":true}",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String configValue;

    @Schema(description = "配置描述", example = "Default password policy")
    private String description;

    @NotNull(message = "启用状态不能为空")
    @Schema(description = "是否启用", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;

    @Schema(description = "创建人ID", example = "1001")
    private Long createdBy;
}
