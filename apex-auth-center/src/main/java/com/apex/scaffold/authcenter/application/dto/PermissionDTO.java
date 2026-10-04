package com.apex.scaffold.authcenter.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 权限数据传输对象
 *
 * <p>用于前端与后端之间传输权限信息，包含权限编码、资源类型、操作类型等。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "权限信息")
public class PermissionDTO {

    @Schema(description = "权限ID", example = "1")
    private Long id;

    @NotBlank(message = "权限编码不能为空")
    @Size(max = 100, message = "权限编码长度不能超过100")
    @Schema(description = "权限编码", example = "user:read", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank(message = "权限名称不能为空")
    @Size(max = 100, message = "权限名称长度不能超过100")
    @Schema(description = "权限名称", example = "查看用户", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotBlank(message = "资源标识不能为空")
    @Size(max = 200, message = "资源标识长度不能超过200")
    @Schema(description = "资源标识", example = "user", requiredMode = Schema.RequiredMode.REQUIRED)
    private String resource;

    @NotBlank(message = "操作类型不能为空")
    @Size(max = 50, message = "操作类型长度不能超过50")
    @Schema(description = "操作类型", example = "read", requiredMode = Schema.RequiredMode.REQUIRED)
    private String action;

    @Size(max = 500, message = "权限描述长度不能超过500")
    @Schema(description = "权限描述", example = "允许查看用户列表和详情")
    private String description;

    @Schema(description = "是否启用（0=禁用 1=启用）", example = "1")
    private Integer enabled;

    @Schema(description = "租户ID", example = "tenant_001")
    private String tenantId;

    @Schema(description = "创建时间", example = "2024-01-01T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间", example = "2024-01-01T12:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "版本号（乐观锁）", example = "1")
    private Integer version;
}
