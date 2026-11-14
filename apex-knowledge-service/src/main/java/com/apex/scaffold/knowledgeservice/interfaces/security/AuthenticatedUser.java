package com.apex.scaffold.knowledgeservice.interfaces.security;

import java.security.Principal;

/**
 * 已认证用户信息。
 *
 * <p>作为 Spring Security 认证主体（{@link Principal}）承载已验证的用户与租户标识；
 * 由 {@link KnowledgeIdentityRelayAuthenticationFilter} 从独立验签的 JWT 构建，
 * 不接受客户端身份头。
 *
 * @param userId 用户 ID
 * @param tenantId 租户 ID
 * @author Apex Team
 * @since 1.3.2
 */
public record AuthenticatedUser(Long userId, String tenantId) implements Principal {

    /**
     * 获取认证主体的名称标识。
     *
     * @return 主体名称字符串
     */
    @Override
    public String getName() {
        return userId != null ? String.valueOf(userId) : null;
    }
}
