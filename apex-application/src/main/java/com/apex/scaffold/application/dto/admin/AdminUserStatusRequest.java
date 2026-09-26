package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 启用或禁用目标用户账号的请求。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "AdminUserStatusRequest", description = "启用或禁用目标用户账号请求")
public record AdminUserStatusRequest(
        @NotNull
                @Schema(
                        description = "是否启用账号（true-启用，false-禁用；不能操作当前登录管理员自己的账号）",
                        example = "true",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                Boolean enabled) {}
