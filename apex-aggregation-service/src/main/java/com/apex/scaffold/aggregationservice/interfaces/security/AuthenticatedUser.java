package com.apex.scaffold.aggregationservice.interfaces.security;

import java.security.Principal;

/**
 * 已认证用户信息。
 *
 * <p>作为 Spring Security 认证主体（{@link Principal}）承载统一待办查询所需的身份字段：
 * 用户 ID 与租户 ID。本类为聚合服务的本地实现，身份来源于
 * {@link IdentityRelayAuthenticationFilter} 对已验签 JWT 的声明解析，
 * {@code X-User-Id}、{@code X-Tenant-Id} 请求头不作为认证依据。
 *
 * @param userId   用户标识
 * @param tenantId 租户标识
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
public record AuthenticatedUser(Long userId, String tenantId) implements Principal {

    /**
     * 获取认证主体的名称标识。
     *
     * @return 用户 ID 的字符串形式；用户 ID 为空时返回 {@code null}
     */
    @Override
    public String getName() {
        return userId != null ? String.valueOf(userId) : null;
    }

    /**
     * 返回认证用户的字符串表示。
     *
     * @return 包含 userId 与 tenantId 的字符串表示
     */
    @Override
    public String toString() {
        return "AuthenticatedUser[userId=" + userId + ", tenantId=" + tenantId + "]";
    }
}
