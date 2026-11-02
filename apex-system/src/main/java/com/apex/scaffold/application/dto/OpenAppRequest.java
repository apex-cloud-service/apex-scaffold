package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 开放应用创建/更新请求（D2）。
 *
 * <p>开放应用复用既有 OAUTH2_CLIENT 注册表存储，回调与 scope 落在客户端既有
 * {@code redirectUris/scopes} 字段，其余开放元数据（限流档/联系人/状态）落在
 * {@code clientSettings} JSON 的 {@code openPlatform.*} 键下，不引入新表结构。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Schema(description = "开放应用创建/更新请求")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("PMD.ClassNamingShouldBeCamelRule")
public class OpenAppRequest {

    /** 客户端 ID（创建必填，更新时以路径参数为准） */
    @Schema(description = "客户端ID，全局唯一标识", example = "partner-data-platform")
    private String clientId;

    /** 客户端密钥（创建时可选提供，未提供自动生成；明文仅本次返回） */
    @Schema(description = "客户端密钥，用于身份验证", example = "secret-abc123xyz")
    private String clientSecret;

    /** 应用名称（创建必填） */
    @Schema(description = "开放应用名称", example = "合作方数据平台")
    private String appName;

    /** 应用描述 */
    @Schema(description = "开放应用描述", example = "对接文件与工作流开放接口的合作方应用")
    private String description;

    /** 回调地址集合（复用 OAuth2 redirectUris） */
    @Schema(description = "回调地址集合", example = "[\"https://partner.example.com/oauth/callback\"]")
    private Set<String> redirectUris;

    /** scope 清单（须全部在开放平台 scope 注册表内） */
    @Schema(description = "开放 scope 清单", example = "[\"open:file:read\", \"open:workflow:read\"]")
    private Set<String> scopes;

    /** 限流档（如 standard/high，语义由网关限流规则解释） */
    @Schema(description = "限流档", example = "standard")
    private String rateLimitTier;

    /** 技术联系人 */
    @Schema(description = "技术联系人", example = "partner@example.com")
    private String contact;

    /** 状态（ENABLED/DISABLED，缺省 ENABLED） */
    @Schema(description = "状态", example = "ENABLED")
    private String status;
}
