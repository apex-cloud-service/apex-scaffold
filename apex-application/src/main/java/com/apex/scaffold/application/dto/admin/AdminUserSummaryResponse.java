package com.apex.scaffold.application.dto.admin;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 超级管理员用户列表项，不包含任何认证令牌。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record AdminUserSummaryResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String nickname,
        String avatarUrl,
        Integer gender,
        String phone,
        Integer status,
        String onboardingStatus,
        String activityStatus,
        LocalDateTime lastLoginTime,
        LocalDateTime createdAt,
        List<AdminRoleResponse> roles) {}
