package com.apex.scaffold.application.dto.admin;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/**
 * 后台可配置角色。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record AdminRoleResponse(@JsonSerialize(using = ToStringSerializer.class) Long id, String name, String code) {}
