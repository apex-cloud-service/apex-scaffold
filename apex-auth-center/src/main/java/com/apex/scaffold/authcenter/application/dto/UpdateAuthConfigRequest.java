package com.apex.scaffold.authcenter.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新认证配置请求DTO
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新认证配置请求")
public class UpdateAuthConfigRequest {

    @Schema(description = "配置值（JSON格式）", example = "{\"minLength\":10,\"requireUppercase\":true}")
    private String configValue;

    @Schema(description = "配置描述", example = "Updated password policy")
    private String description;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "更新人ID", example = "1001")
    private Long updatedBy;
}
