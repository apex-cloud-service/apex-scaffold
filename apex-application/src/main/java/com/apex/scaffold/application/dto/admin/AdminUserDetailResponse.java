package com.apex.scaffold.application.dto.admin;

import com.apex.scaffold.application.dto.MeVO;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 超级管理员用户详情；明确排除 accessToken、refreshToken 和登录 IP。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public record AdminUserDetailResponse(
        @JsonSerialize(using = ToStringSerializer.class) Long id,
        String nickname,
        String avatarUrl,
        Integer gender,
        LocalDate birthday,
        String phone,
        String country,
        String province,
        String city,
        Integer status,
        String onboardingStatus,
        String language,
        String activityStatus,
        LocalDateTime lastLoginTime,
        LocalDateTime lastHeartbeatTime,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        MeVO profile,
        AdminUserProfileResponse profileDetails,
        List<AdminRoleResponse> roles) {}
