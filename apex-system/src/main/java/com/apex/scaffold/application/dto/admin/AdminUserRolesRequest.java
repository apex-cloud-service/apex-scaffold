package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * 用目标集合替换用户角色。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "AdminUserRolesRequest", description = "替换用户后台角色请求（以提交的角色集合整体替换现有角色）")
public record AdminUserRolesRequest(
        @NotNull
                @Schema(
                        description = "目标角色 ID 集合（空集合表示清空用户角色）",
                        example = "[1001, 1002]",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Set<Long> roleIds) {}
