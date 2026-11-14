package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SSO 提供商配置请求对象
 *
 * <p>用于创建或更新 SSO 单点登录提供商配置，支持 SAML 2.0、CAS、LDAP 和 OIDC 等多种协议。
 * 所有参数均包含 JSR-303 校验注解以确保数据合法性。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>SAML - SAML 2.0 标准协议</li>
 *   <li>CAS - Central Authentication Service 协议</li>
 *   <li>LDAP - 轻量级目录访问协议</li>
 *   <li>OIDC - OpenID Connect 协议</li>
 * </ul>
 *
 * @author Apex Scaffold Generator
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "SSO 提供商配置请求对象")
public class SsoProviderRequest {

    @NotBlank(message = "提供商名称不能为空")
    @Size(max = 100, message = "提供商名称长度不能超过 100 字符")
    @Schema(description = "提供商名称", required = true, example = "企业 SAML 认证")
    private String providerName;

    @NotBlank(message = "协议类型不能为空")
    @Schema(description = "协议类型（SAML/CAS/LDAP/OIDC）", required = true, example = "SAML")
    private String protocolType;

    @NotBlank(message = "认证端点 URL 不能为空")
    @Size(max = 500, message = "认证端点 URL 长度不能超过 500 字符")
    @Schema(description = "认证端点 URL", required = true, example = "https://sso.example.com/saml/login")
    private String authEndpoint;

    @Size(max = 500, message = "注销端点 URL 长度不能超过 500 字符")
    @Schema(description = "注销端点 URL", example = "https://sso.example.com/saml/logout")
    private String logoutEndpoint;

    @NotBlank(message = "实体 ID 不能为空")
    @Size(max = 200, message = "实体 ID 长度不能超过 200 字符")
    @Schema(description = "实体 ID（SAML Entity ID 或 CAS Service URL）", required = true, example = "urn:apex:scaffold")
    private String entityId;

    @Schema(description = "证书内容（SAML 协议需要）", example = "-----BEGIN CERTIFICATE-----\n...\n-----END CERTIFICATE-----")
    private String certificate;

    @Schema(description = "元数据 URL（SAML 协议可选）", example = "https://sso.example.com/saml/metadata")
    private String metadataUrl;

    @Schema(
            description = "扩展配置（JSON 格式，存储协议特定配置）",
            example = "{\"nameIdFormat\": \"urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress\"}")
    private String configJson;

    @NotNull(message = "是否启用不能为空")
    @Schema(description = "是否启用", required = true, example = "true")
    private Boolean enabled;

    @Schema(description = "描述", example = "企业内部 SAML 2.0 单点登录")
    private String description;

    @Schema(description = "排序值（越小越靠前）", example = "1")
    private Integer sortOrder;
}
