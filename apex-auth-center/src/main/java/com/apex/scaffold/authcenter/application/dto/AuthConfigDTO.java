package com.apex.scaffold.authcenter.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证配置DTO
 *
 * <p>用于API层的认证配置数据传输。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "认证配置DTO")
public class AuthConfigDTO {

    @Schema(description = "配置ID", example = "1")
    private Long id;

    @Schema(description = "租户ID", example = "tenant-001")
    private String tenantId;

    @Schema(
            description = "配置类型",
            example = "PASSWORD_POLICY",
            allowableValues = {"PASSWORD_POLICY", "SESSION_POLICY", "OAUTH2_CLIENT", "SECURITY_POLICY", "MFA_POLICY"})
    private String configType;

    @Schema(description = "配置键", example = "default")
    private String configKey;

    @Schema(description = "配置值（JSON格式）", example = "{\"minLength\":8,\"requireUppercase\":true}")
    private String configValue;

    @Schema(description = "配置描述", example = "Default password policy")
    private String description;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "是否为系统默认配置", example = "false")
    private Boolean isDefault;

    @Schema(description = "配置版本号", example = "1")
    private Integer version;

    @Schema(description = "创建时间", example = "2024-01-01T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间", example = "2024-01-01T12:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "创建人ID", example = "1001")
    private Long createdBy;

    @Schema(description = "更新人ID", example = "1001")
    private Long updatedBy;
}
