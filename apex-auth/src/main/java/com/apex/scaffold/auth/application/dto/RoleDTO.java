package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色数据传输对象
 *
 * <p>用于前端与后端之间传输角色信息，包含角色基本信息、数据权限范围、启用状态等。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "角色信息")
public class RoleDTO {

    @Schema(description = "角色ID", example = "1")
    private Long id;

    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码长度不能超过50")
    @Schema(description = "角色编码", example = "ROLE_ADMIN", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 100, message = "角色名称长度不能超过100")
    @Schema(description = "角色名称", example = "系统管理员", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Size(max = 500, message = "角色描述长度不能超过500")
    @Schema(description = "角色描述", example = "系统超级管理员，拥有所有权限")
    private String description;

    @Schema(description = "数据权限范围（0=全部数据 1=本部门及子部门 2=本部门 3=本人）", example = "0")
    private Integer dataScope;

    @Schema(description = "是否启用（0=禁用 1=启用）", example = "1")
    private Integer enabled;

    @Schema(description = "租户ID", example = "tenant_001")
    private String tenantId;

    @Schema(description = "关联的权限ID集合", example = "[1, 2, 3]")
    private Set<Long> permissionIds;

    @Schema(description = "创建时间", example = "2024-01-01T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间", example = "2024-01-01T12:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "版本号（乐观锁）", example = "1")
    private Integer version;
}
