package com.apex.scaffold.application.dto.admin;

import jakarta.validation.constraints.NotNull;

/**
 * 启用或禁用目标用户账号的请求。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record AdminUserStatusRequest(@NotNull Boolean enabled) {}
