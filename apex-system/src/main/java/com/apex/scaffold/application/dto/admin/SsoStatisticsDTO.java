package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSO 统计信息响应对象
 *
 * <p>用于返回 SSO 单点登录的统计数据，包含活跃会话数、登录成功率、提供商使用情况等。
 * 用于管理员监控 SSO 系统的运行状态和使用情况。
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>会话统计：活跃会话数、今日新增会话数、今日撤销会话数</li>
 *   <li>登录统计：今日登录次数、登录成功率、登录失败次数</li>
 *   <li>提供商统计：各提供商的使用次数和成功率</li>
 *   <li>性能统计：平均登录耗时、最大登录耗时</li>
 * </ul>
 *
 * @author Apex Scaffold Generator
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SSO 统计信息响应对象")
public class SsoStatisticsDTO {

    @Schema(description = "活跃会话总数", example = "1523")
    private Long activeSessionCount;

    @Schema(description = "今日新增会话数", example = "156")
    private Long todayNewSessionCount;

    @Schema(description = "今日撤销会话数", example = "12")
    private Long todayRevokedSessionCount;

    @Schema(description = "今日登录总次数", example = "230")
    private Long todayLoginCount;

    @Schema(description = "今日登录成功次数", example = "218")
    private Long todayLoginSuccessCount;

    @Schema(description = "今日登录失败次数", example = "12")
    private Long todayLoginFailureCount;

    @Schema(description = "今日登录成功率（百分比）", example = "94.78")
    private Double todayLoginSuccessRate;

    @Schema(description = "今日平均登录耗时（毫秒）", example = "125")
    private Long todayAvgLoginDuration;

    @Schema(description = "今日最大登录耗时（毫秒）", example = "890")
    private Long todayMaxLoginDuration;

    @Schema(description = "启用的提供商数量", example = "3")
    private Integer enabledProviderCount;

    @Schema(description = "总提供商数量", example = "5")
    private Integer totalProviderCount;

    @Schema(description = "最常用提供商名称", example = "企业 SAML 认证")
    private String mostUsedProviderName;

    @Schema(description = "最常用提供商使用次数", example = "180")
    private Long mostUsedProviderCount;
}
