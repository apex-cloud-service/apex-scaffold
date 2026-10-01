package com.apex.scaffold.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * SSO 提供商持久化对象
 *
 * <p>对应数据库表 {@code sys_sso_provider}，存储单点登录提供商的配置信息，支持多种 SSO 协议。
 * 继承 {@link BasePO} 获得通用字段（ID、创建时间、更新时间、逻辑删除等）。
 *
 * <h2>核心字段</h2>
 * <ul>
 *   <li>{@link #providerName} - 提供商名称（如 "企业 AD"、"飞书 SSO"）</li>
 *   <li>{@link #providerType} - 提供商类型（SAML、OAUTH2、LDAP、CAS）</li>
 *   <li>{@link #config} - 配置信息（JSON 格式，存储协议特定的配置参数）</li>
 *   <li>{@link #entityId} - 实体 ID（SAML 2.0 Entity ID）</li>
 *   <li>{@link #metadataUrl} - 元数据 URL（SAML 2.0 Metadata URL）</li>
 *   <li>{@link #loginUrl} - 登录 URL（SSO 登录跳转地址）</li>
 *   <li>{@link #logoutUrl} - 登出 URL（SSO 登出跳转地址）</li>
 *   <li>{@link #enabled} - 启用状态（0=禁用、1=启用）</li>
 *   <li>{@link #priority} - 优先级（多个提供商时的选择顺序，数字越小优先级越高）</li>
 *   <li>{@link #tenantId} - 租户标识（多租户隔离）</li>
 * </ul>
 *
 * <h2>支持的协议类型</h2>
 * <ul>
 *   <li>SAML：SAML 2.0 协议，企业级 SSO 标准</li>
 *   <li>OAUTH2：OAuth 2.0 / OpenID Connect 协议</li>
 *   <li>LDAP：轻量级目录访问协议</li>
 *   <li>CAS：中央认证服务协议</li>
 * </ul>
 *
 * <h2>配置字段说明</h2>
 * <p>{@code config} 字段为 JSON 格式，不同协议类型包含不同的配置项：
 * <ul>
 *   <li>SAML：certificate、privateKey、signatureAlgorithm 等</li>
 *   <li>OAuth2：clientId、clientSecret、scope、authorizationUri、tokenUri 等</li>
 *   <li>LDAP：host、port、baseDn、userDnPattern、searchFilter 等</li>
 *   <li>CAS：serverUrl、loginUrl、validateUrl、serviceUrl 等</li>
 * </ul>
 *
 * <h2>业务场景</h2>
 * <ul>
 *   <li>SSO 配置：管理员配置单点登录提供商</li>
 *   <li>多协议支持：同时支持多种 SSO 协议</li>
 *   <li>提供商选择：按优先级选择可用的 SSO 提供商</li>
 *   <li>动态切换：启用/禁用特定的 SSO 提供商</li>
 * </ul>
 *
 * <h2>注意事项</h2>
 * <ul>
 *   <li>配置信息 {@code config} 包含敏感数据（如密钥），需加密存储</li>
 *   <li>证书和私钥应定期更新，避免过期</li>
 *   <li>多租户场景下，每个租户可配置独立的 SSO 提供商</li>
 *   <li>禁用提供商不影响已登录的会话</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see BasePO
 * @see SysSsoSessionPO
 * @see SysSsoAuditLogPO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
@TableName("sys_sso_provider")
public class SysSsoProviderPO extends BasePO {

    private String providerName;
    private String providerType;
    private String config;
    private String entityId;
    private String metadataUrl;
    private String loginUrl;
    private String logoutUrl;
    private Integer enabled;
    private Integer priority;
    private String tenantId;
}
