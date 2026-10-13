package com.apex.scaffold.workflowservice.interfaces.security;

import java.security.Principal;

/**
 * 已认证用户信息。
 *
 * <p>作为 Spring Security 认证主体（{@link Principal}）承载网关下发的用户标识信息：
 * 用户 ID、OpenID、租户 ID 与部门 ID。其中 compact 构造器重载允许不提供部门 ID。
 *
 * <p>本类为工作流服务的本地实现，用户身份来源于网关
 * {@code UserContextRelayFilter} 注入的可信请求头（{@code X-User-Id}、{@code X-Tenant-Id}），
 * 由 {@link IdentityRelayAuthenticationFilter} 解析后构建。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
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
     * <p>实现 {@link Principal#getName()} 接口，返回用户 ID 的字符串形式作为主体名称；
     * 当用户 ID 为空时回退返回 OpenID。
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
     * <p>重写 {@link Object#toString()}，返回包含 userId、openId 和 tenantId 的字符串表示，
     * 不包含 deptId 以保持输出简洁。
     *
     * @return 格式化的字符串表示
     */
    @Override
    public String toString() {
        return "AuthenticatedUser[userId=" + userId + ", openId=" + openId + ", tenantId=" + tenantId + "]";
    }
}
