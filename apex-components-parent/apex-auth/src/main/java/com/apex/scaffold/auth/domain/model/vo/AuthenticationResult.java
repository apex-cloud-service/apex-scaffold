package com.apex.scaffold.auth.domain.model.vo;

import java.time.LocalDateTime;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证结果值对象
 *
 * <p>封装认证成功后的所有信息，包括用户信息、Token、权限和会话等。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthenticationResult {

    /**
     * 认证是否成功
     */
    private Boolean success;

    /**
     * 失败原因（仅在失败时填充）
     */
    private String failureReason;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * Token对（访问令牌和刷新令牌）
     */
    private TokenPair tokenPair;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 用户角色集合
     */
    private Set<String> roles;

    /**
     * 用户权限集合
     */
    private Set<String> permissions;

    /**
     * Token过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 刷新令牌过期时间
     */
    private LocalDateTime refreshExpiresAt;

    /**
     * 认证时间
     */
    private LocalDateTime authenticatedAt;

    /**
     * 检查认证是否成功
     *
     * @return 如果认证成功返回true
     */
    public boolean isSuccess() {
        return Boolean.TRUE.equals(success);
    }

    /**
     * 检查是否有指定角色
     *
     * @param role 角色编码
     * @return 如果用户拥有该角色返回true
     */
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }

    /**
     * 检查是否有指定权限
     *
     * @param permission 权限编码
     * @return 如果用户拥有该权限返回true
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    /**
     * 创建失败结果
     *
     * @param reason 失败原因
     * @return 认证失败结果
     */
    public static AuthenticationResult failure(String reason) {
        return AuthenticationResult.builder()
                .success(false)
                .failureReason(reason)
                .build();
    }
}
