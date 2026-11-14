package com.apex.scaffold.auth.domain.model.vo;

import java.time.LocalDateTime;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Token验证结果值对象
 *
 * <p>封装Token验证的结果，包括验证状态、用户信息、权限等。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TokenValidationResult {

    /**
     * 验证是否成功
     */
    private Boolean valid;

    /**
     * 失败原因（仅在验证失败时填充）
     */
    private String failureReason;

    /**
     * 令牌标识。
     */
    private Long tokenId;

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
     * Token类型（ACCESS、REFRESH）
     */
    private String tokenType;

    /**
     * Token签发时间
     */
    private LocalDateTime issuedAt;

    /**
     * Token过期时间
     */
    private LocalDateTime expiresAt;

    /**
     * 用户角色集合
     */
    private Set<String> roles;

    /**
     * 用户权限集合
     */
    private Set<String> permissions;

    /**
     * Token是否已被撤销
     */
    private Boolean revoked;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 检查Token是否有效
     *
     * @return 如果Token有效返回true
     */
    public boolean isValid() {
        return Boolean.TRUE.equals(valid) && !Boolean.TRUE.equals(revoked);
    }

    /**
     * 检查Token是否已过期
     *
     * @return 如果Token已过期返回true
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 检查Token是否已被撤销
     *
     * @return 如果Token已被撤销返回true
     */
    public boolean isRevoked() {
        return Boolean.TRUE.equals(revoked);
    }

    /**
     * 检查是否为访问令牌
     *
     * @return 如果是访问令牌返回true
     */
    public boolean isAccessToken() {
        return "ACCESS".equals(tokenType);
    }

    /**
     * 检查是否为刷新令牌
     *
     * @return 如果是刷新令牌返回true
     */
    public boolean isRefreshToken() {
        return "REFRESH".equals(tokenType);
    }

    /**
     * 创建无效Token结果
     *
     * @param reason 失败原因
     * @return Token验证失败结果
     */
    public static TokenValidationResult invalid(String reason) {
        return TokenValidationResult.builder()
                .valid(false)
                .failureReason(reason)
                .build();
    }

    /**
     * 创建已过期Token结果
     *
     * @return Token已过期结果
     */
    public static TokenValidationResult expired() {
        return TokenValidationResult.builder()
                .valid(false)
                .failureReason("Token has expired")
                .build();
    }

    /**
     * 创建已撤销Token结果
     *
     * @return Token已撤销结果
     */
    public static TokenValidationResult revoked() {
        return TokenValidationResult.builder()
                .valid(false)
                .revoked(true)
                .failureReason("Token has been revoked")
                .build();
    }
}
