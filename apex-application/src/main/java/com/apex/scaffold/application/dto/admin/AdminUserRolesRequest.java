package com.apex.scaffold.application.dto.admin;

import jakarta.validation.constraints.NotNull;
import java.util.Set;

/**
 * 用目标集合替换用户角色。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record AdminUserRolesRequest(@NotNull Set<Long> roleIds) {}
