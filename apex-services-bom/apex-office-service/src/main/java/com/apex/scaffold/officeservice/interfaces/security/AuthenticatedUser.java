package com.apex.scaffold.officeservice.interfaces.security;

import java.security.Principal;

/**
 * 已认证用户信息。
 *
 * <p>作为 Spring Security 认证主体（{@link Principal}）承载已验证的用户标识信息：
 * 用户 ID、OpenID、租户 ID 与部门 ID。其中 compact 构造器重载允许不提供部门 ID。
 *
 * <p>本类为办公服务的本地实现，由 {@link IdentityRelayAuthenticationFilter}
 * 从独立验签的 JWT 或一次性握手票据构建，不接受客户端身份头。
 *
 * @param userId 用户标识
 * @param openId 第三方登录标识
 * @param tenantId 租户标识
 * @param deptId 所属部门标识
 * @author Apex Team
 * @since 1.0.0
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

    /**
     * 获取认证主体的名称标识。
     *
     * @return 主体名称字符串
     */
    @Override
    public String getName() {
        return userId != null ? String.valueOf(userId) : openId;
    }

    /**
     * 返回认证用户的字符串表示。
     *
     * @return 格式化的字符串表示
     */
    @Override
    public String toString() {
        return "AuthenticatedUser[userId=" + userId + ", openId=" + openId + ", tenantId=" + tenantId + "]";
    }
}
