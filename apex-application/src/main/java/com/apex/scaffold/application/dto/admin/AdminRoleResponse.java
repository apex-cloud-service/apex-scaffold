package com.apex.scaffold.application.dto.admin;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 后台可配置角色。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "AdminRoleResponse", description = "后台可配置角色")
public record AdminRoleResponse(
        @JsonSerialize(using = ToStringSerializer.class)
                @Schema(type = "string", description = "角色ID（以字符串传输，避免前端精度丢失）", example = "1001")
                Long id,
        @Schema(description = "角色名称", example = "普通用户") String name,
        @Schema(description = "角色编码", example = "ROLE_USER") String code) {}
