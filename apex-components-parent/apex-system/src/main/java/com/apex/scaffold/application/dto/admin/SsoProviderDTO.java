package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSO 提供商配置响应对象
 *
 * <p>用于返回 SSO 单点登录提供商的配置信息，包含协议类型、端点地址、认证配置等。
 * 包含审计字段以追踪配置的创建和更新历史。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>SSO 提供商列表展示</li>
 *   <li>SSO 提供商详情查看</li>
 *   <li>SSO 提供商配置编辑</li>
 * </ul>
 *
 * @author Apex Scaffold Generator
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SSO 提供商配置响应对象")
public class SsoProviderDTO {

    @Schema(description = "提供商 ID", example = "1")
    private Long id;

    @Schema(description = "租户 ID", example = "1")
    private Long tenantId;

    @Schema(description = "提供商名称", example = "企业 SAML 认证")
    private String providerName;

    @Schema(description = "协议类型（SAML/CAS/LDAP/OIDC）", example = "SAML")
    private String protocolType;

    @Schema(description = "认证端点 URL", example = "https://sso.example.com/saml/login")
    private String authEndpoint;

    @Schema(description = "注销端点 URL", example = "https://sso.example.com/saml/logout")
    private String logoutEndpoint;

    @Schema(description = "实体 ID（SAML Entity ID 或 CAS Service URL）", example = "urn:apex:scaffold")
    private String entityId;

    @Schema(description = "证书内容（SAML 协议需要）", example = "-----BEGIN CERTIFICATE-----")
    private String certificate;

    @Schema(description = "元数据 URL（SAML 协议可选）", example = "https://sso.example.com/saml/metadata")
    private String metadataUrl;

    @Schema(description = "扩展配置（JSON 格式）", example = "{\"nameIdFormat\": \"emailAddress\"}")
    private String configJson;

    @Schema(description = "是否启用", example = "true")
    private Boolean enabled;

    @Schema(description = "描述", example = "企业内部 SAML 2.0 单点登录")
    private String description;

    @Schema(description = "排序值", example = "1")
    private Integer sortOrder;

    @Schema(description = "创建时间", example = "2026-09-19T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间", example = "2026-09-19T10:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "创建人 ID", example = "1")
    private Long createdBy;

    @Schema(description = "更新人 ID", example = "1")
    private Long updatedBy;
}
