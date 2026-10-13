package com.apex.scaffold.domain.model.entity;

import com.apex.scaffold.domain.model.sso.SsoSessionStatus;
import java.time.LocalDateTime;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

/**
 * SSO会话实体
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Getter
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class SysSsoSessionEntity extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private Long providerId;
    private Long userId;
    private String externalId;
    private String sessionIndex;
    private String assertion;
    private LocalDateTime loginTime;
    private LocalDateTime expireTime;
    private LocalDateTime logoutTime;
    private SsoSessionStatus status;
    private String tenantId;

    public SysSsoSessionEntity() {
        super();
    }

    /**
     * 设置 SSO 提供商 ID，赋值后刷新更新时间。
     *
     * @param providerId 关联的 SSO 提供商 ID
     */
    public void setProviderId(Long providerId) {
        this.providerId = providerId;
        touch();
    }

    /**
     * 设置用户 ID，赋值后刷新更新时间。
     *
     * @param userId 本系统用户 ID
     */
    public void setUserId(Long userId) {
        this.userId = userId;
        touch();
    }

    /**
     * 设置外部用户 ID，赋值后刷新更新时间。
     *
     * @param externalId 用户在 SSO 提供商侧的唯一标识
     */
    public void setExternalId(String externalId) {
        this.externalId = externalId;
        touch();
    }

    /**
     * 设置 SSO 会话索引，赋值后刷新更新时间。
     *
     * @param sessionIndex SSO 会话索引（如 SAML SessionIndex），用于会话匹配与单点登出
     */
    public void setSessionIndex(String sessionIndex) {
        this.sessionIndex = sessionIndex;
        touch();
    }

    /**
     * 设置认证断言数据，赋值后刷新更新时间。
     *
     * @param assertion SAML 断言或其他协议的认证数据原文
     */
    public void setAssertion(String assertion) {
        this.assertion = assertion;
        touch();
    }

    /**
     * 设置登录时间，赋值后刷新更新时间。
     *
     * @param loginTime SSO 登录成功时间
     */
    public void setLoginTime(LocalDateTime loginTime) {
        this.loginTime = loginTime;
        touch();
    }

    /**
     * 设置过期时间，赋值后刷新更新时间。
     *
     * @param expireTime 会话失效时间
     */
    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
        touch();
    }

    /**
     * 设置登出时间，赋值后刷新更新时间。
     *
     * @param logoutTime 会话登出时间，未登出时为 null
     */
    public void setLogoutTime(LocalDateTime logoutTime) {
        this.logoutTime = logoutTime;
        touch();
    }

    /**
     * 设置会话状态，赋值后刷新更新时间。
     *
     * @param status 会话状态（ACTIVE/EXPIRED/LOGOUT）
     */
    public void setStatus(SsoSessionStatus status) {
        this.status = status;
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
