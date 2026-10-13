package com.apex.scaffold.domain.model.entity;

import com.apex.scaffold.domain.model.sso.SsoProtocolType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * SSO单点登录提供商实体
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SysSsoProviderEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String providerName;
    private SsoProtocolType providerType;
    private String config;
    private String entityId;
    private String metadataUrl;
    private String loginUrl;
    private String logoutUrl;
    private Integer enabled;
    private Integer priority;
    private String tenantId;

    public SysSsoProviderEntity() {
        super();
    }

    /**
     * 设置 SSO 提供商名称，赋值后刷新更新时间。
     *
     * @param providerName 提供商名称
     */
    public void setProviderName(String providerName) {
        this.providerName = providerName;
        touch();
    }

    /**
     * 设置 SSO 认证协议类型，赋值后刷新更新时间。
     *
     * @param providerType 认证协议类型（SAML/CAS/LDAP/OIDC）
     */
    public void setProviderType(SsoProtocolType providerType) {
        this.providerType = providerType;
        touch();
    }

    /**
     * 设置提供商扩展配置，赋值后刷新更新时间。
     *
     * @param config 提供商配置内容（JSON 格式）
     */
    public void setConfig(String config) {
        this.config = config;
        touch();
    }

    /**
     * 设置 SAML EntityID，赋值后刷新更新时间。
     *
     * @param entityId SAML EntityID 唯一实体标识
     */
    public void setEntityId(String entityId) {
        this.entityId = entityId;
        touch();
    }

    /**
     * 设置元数据获取地址，赋值后刷新更新时间。
     *
     * @param metadataUrl IdP 元数据（Metadata）获取 URL
     */
    public void setMetadataUrl(String metadataUrl) {
        this.metadataUrl = metadataUrl;
        touch();
    }

    /**
     * 设置单点登录地址，赋值后刷新更新时间。
     *
     * @param loginUrl 单点登录（SSO Login）URL
     */
    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
        touch();
    }

    /**
     * 设置单点登出地址，赋值后刷新更新时间。
     *
     * @param logoutUrl 单点登出（SLO Logout）URL
     */
    public void setLogoutUrl(String logoutUrl) {
        this.logoutUrl = logoutUrl;
        touch();
    }

    /**
     * 设置是否启用该提供商，赋值后刷新更新时间。
     *
     * @param enabled 是否启用（0-禁用，1-启用）
     */
    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
        touch();
    }

    /**
     * 设置提供商排序优先级，赋值后刷新更新时间。
     *
     * @param priority 排序优先级，数值越小优先级越高
     */
    public void setPriority(Integer priority) {
        this.priority = priority;
        touch();
    }

    /**
     * 设置租户标识，赋值后刷新更新时间。
     *
     * @param tenantId 租户标识，用于多租户数据隔离
     */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
        touch();
    }
}
