package com.apex.scaffold.application.dto.admin;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 超级管理员用户列表项，不包含任何认证令牌。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "AdminUserSummaryResponse", description = "超级管理员用户列表项响应（不含任何认证令牌）")
public record AdminUserSummaryResponse(
        @JsonSerialize(using = ToStringSerializer.class)
                @Schema(type = "string", description = "用户ID（以字符串传输，避免前端精度丢失）", example = "1001")
                Long id,
        @Schema(description = "用户昵称", example = "张三") String nickname,
        @Schema(description = "头像 URL", example = "https://cdn.example.com/avatar/1001.png") String avatarUrl,
        @Schema(
                        description = "性别（0-未知，1-男，2-女）",
                        example = "1",
                        allowableValues = {"0", "1", "2"})
                Integer gender,
        @Schema(description = "手机号", example = "13800001234") String phone,
        @Schema(
                        description = "账号状态（0-禁用，1-正常）",
                        example = "1",
                        allowableValues = {"0", "1"})
                Integer status,
        @Schema(description = "新用户引导状态码（如 ONBOARDING_PENDING 表示引导未完成）", example = "ONBOARDING_PENDING")
                String onboardingStatus,
        @Schema(
                        description = "活动状态（按心跳与最近登录时间推导：ONLINE-在线，ACTIVE-活跃，INACTIVE-不活跃）",
                        example = "ONLINE",
                        allowableValues = {"ONLINE", "ACTIVE", "INACTIVE"})
                String activityStatus,
        @Schema(description = "最近登录时间", example = "2026-09-26T10:00:00") LocalDateTime lastLoginTime,
        @Schema(description = "账号创建时间", example = "2026-01-01T08:00:00") LocalDateTime createdAt,
        @Schema(description = "后台可配置角色列表") List<AdminRoleResponse> roles) {}
