package com.apex.scaffold.application.dto.admin;

import com.apex.scaffold.application.dto.MeVO;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 超级管理员用户详情；明确排除 accessToken、refreshToken 和登录 IP。
 *
 * @param id 用户ID（以字符串传输，避免前端精度丢失）
 * @param nickname 用户昵称
 * @param avatarUrl 头像 URL
 * @param gender 性别（0-未知，1-男，2-女）
 * @param birthday 出生日期
 * @param phone 手机号
 * @param country 国家/地区
 * @param province 省份
 * @param city 城市
 * @param status 账号状态（0-禁用，1-正常）
 * @param onboardingStatus 新用户引导状态码（如 ONBOARDING_PENDING 表示引导未完成）
 * @param language 语言偏好
 * @param activityStatus 活动状态（按心跳与最近登录时间推导：ONLINE-在线，ACTIVE-活跃，INACTIVE-不活跃）
 * @param lastLoginTime 最近登录时间
 * @param lastHeartbeatTime 最近心跳时间
 * @param createdAt 账号创建时间
 * @param updatedAt 资料更新时间
 * @param profile 用户基础信息（含会员与认证状态）
 * @param profileDetails 用户画像明细
 * @param roles 后台可配置角色列表
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "AdminUserDetailResponse", description = "超级管理员用户详情响应（不含认证令牌与登录 IP）")
public record AdminUserDetailResponse(
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
        @Schema(description = "出生日期", example = "1998-06-15") LocalDate birthday,
        @Schema(description = "手机号", example = "13800001234") String phone,
        @Schema(description = "国家/地区", example = "中国") String country,
        @Schema(description = "省份", example = "广东省") String province,
        @Schema(description = "城市", example = "深圳市") String city,
        @Schema(
                        description = "账号状态（0-禁用，1-正常）",
                        example = "1",
                        allowableValues = {"0", "1"})
                Integer status,
        @Schema(description = "新用户引导状态码（如 ONBOARDING_PENDING 表示引导未完成）", example = "ONBOARDING_PENDING")
                String onboardingStatus,
        @Schema(description = "语言偏好", example = "zh_CN") String language,
        @Schema(
                        description = "活动状态（按心跳与最近登录时间推导：ONLINE-在线，ACTIVE-活跃，INACTIVE-不活跃）",
                        example = "ONLINE",
                        allowableValues = {"ONLINE", "ACTIVE", "INACTIVE"})
                String activityStatus,
        @Schema(description = "最近登录时间", example = "2026-09-26T10:00:00") LocalDateTime lastLoginTime,
        @Schema(description = "最近心跳时间", example = "2026-09-26T10:00:00") LocalDateTime lastHeartbeatTime,
        @Schema(description = "账号创建时间", example = "2026-01-01T08:00:00") LocalDateTime createdAt,
        @Schema(description = "资料更新时间", example = "2026-09-26T09:30:00") LocalDateTime updatedAt,
        @Schema(description = "用户基础信息（含会员与认证状态）") MeVO profile,
        @Schema(description = "用户画像明细") AdminUserProfileResponse profileDetails,
        @Schema(description = "后台可配置角色列表") List<AdminRoleResponse> roles) {}
