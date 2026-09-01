package com.apex.scaffold.interfaces.security;

import java.security.Principal;

/**
 * 已认证用户信息。
 *
 * <p>作为 Spring Security 认证主体（{@link Principal}）承载认证成功后的用户标识信息：
 * 用户 ID、OpenID、租户 ID 与部门 ID。其中 compact 构造器重载允许不提供部门 ID。</p>
 *
 * @author Apex Team
 * @version 1.0
 * @since 1.0
 */
public record AuthenticatedUser(Long userId, String openId, String tenantId, Long deptId) implements Principal {

    /**
     * 构建不含部门信息的已认证用户。
     *
     * @param userId   用户 ID，可能为空
     * @param openId   OpenID，可能为空
     * @param tenantId 租户 ID，可能为空
     */
    public AuthenticatedUser(Long userId, String openId, String tenantId) {
        this(userId, openId, tenantId, null);
    }

    @Override
    public String getName() {
        return openId;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[userId=" + userId + ", openId=" + openId + ", tenantId=" + tenantId + "]";
    }
}
