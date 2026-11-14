package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开放应用响应（D2）。
 *
 * <p>{@code clientSecret} 仅在创建与重置密钥时返回一次，其余查询恒为 {@code null}。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Schema(description = "开放应用响应")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class OpenAppResponse {

    /** 客户端 ID */
    @Schema(description = "客户端ID", example = "partner-data-platform")
    private String clientId;

    /** 应用名称 */
    @Schema(description = "开放应用名称", example = "合作方数据平台")
    private String appName;

    /** 应用描述 */
    @Schema(description = "开放应用描述")
    private String description;

    /** 回调地址集合 */
    @Schema(description = "回调地址集合")
    private Set<String> redirectUris;

    /** scope 清单 */
    @Schema(description = "开放 scope 清单")
    private Set<String> scopes;

    /** 限流档 */
    @Schema(description = "限流档", example = "standard")
    private String rateLimitTier;

    /** 技术联系人 */
    @Schema(description = "技术联系人")
    private String contact;

    /** 状态 */
    @Schema(description = "状态", example = "ENABLED")
    private String status;

    /** 创建时间（ISO-8601） */
    @Schema(description = "创建时间", example = "2026-11-01T10:00:00")
    private String createdAt;

    /** 明文密钥（仅创建/重置时返回一次） */
    @Schema(description = "客户端密钥明文，仅创建/重置时返回")
    @lombok.ToString.Exclude
    private String clientSecret;

    @Schema(description = "主体用户ID")
    private Long subjectUserId;
}
