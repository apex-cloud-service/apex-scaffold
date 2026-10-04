package com.apex.scaffold.authcenter.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 为角色分配权限请求对象
 *
 * <p>用于接收前端为指定角色分配权限的请求，包含角色ID和权限ID集合。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 2024-01-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "为角色分配权限请求")
public class AssignPermissionsRequest {

    @NotNull(message = "角色ID不能为空")
    @Schema(description = "角色ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long roleId;

    @NotEmpty(message = "权限ID集合不能为空")
    @Schema(description = "权限ID集合", example = "[1, 2, 3]", requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<Long> permissionIds;
}
