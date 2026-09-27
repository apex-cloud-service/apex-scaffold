package com.apex.scaffold.authcenter.domain.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证配置实体
 *
 * <p>存储认证授权系统的各项配置参数，包括密码策略、会话策略、OAuth2配置等。
 * 支持多租户隔离和配置热更新。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthConfigEntity {

    /**
     * 配置ID
     */
    private Long id;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 配置类型（PASSWORD_POLICY、SESSION_POLICY、OAUTH2_CLIENT、SECURITY_POLICY、MFA_POLICY）
     */
    private String configType;

    /**
     * 配置键（在同一类型下唯一）
     */
    private String configKey;

    /**
     * 配置值（JSON格式）
     */
    private String configValue;

    /**
     * 配置描述
     */
    private String description;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 是否为系统默认配置
     */
    private Boolean isDefault;

    /**
     * 配置版本号（用于乐观锁和配置更新追踪）
     */
    private Integer version;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 更新人ID
     */
    private Long updatedBy;

    /**
     * 检查配置是否启用
     *
     * @return 如果配置启用返回true
     */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * 检查是否为密码策略配置
     *
     * @return 如果是密码策略配置返回true
     */
    public boolean isPasswordPolicy() {
        return "PASSWORD_POLICY".equals(configType);
    }

    /**
     * 检查是否为会话策略配置
     *
     * @return 如果是会话策略配置返回true
     */
    public boolean isSessionPolicy() {
        return "SESSION_POLICY".equals(configType);
    }

    /**
     * 检查是否为OAuth2客户端配置
     *
     * @return 如果是OAuth2客户端配置返回true
     */
    public boolean isOauth2Client() {
        return "OAUTH2_CLIENT".equals(configType);
    }

    /**
     * 检查是否为安全策略配置
     *
     * @return 如果是安全策略配置返回true
     */
    public boolean isSecurityPolicy() {
        return "SECURITY_POLICY".equals(configType);
    }

    /**
     * 检查是否为MFA策略配置
     *
     * @return 如果是MFA策略配置返回true
     */
    public boolean isMfaPolicy() {
        return "MFA_POLICY".equals(configType);
    }

    /**
     * 检查是否为系统默认配置
     *
     * @return 如果是系统默认配置返回true
     */
    public boolean isDefaultConfig() {
        return Boolean.TRUE.equals(isDefault);
    }

    /**
     * 启用配置
     */
    public void enable() {
        this.enabled = true;
    }

    /**
     * 禁用配置
     */
    public void disable() {
        this.enabled = false;
    }

    /**
     * 增加版本号
     */
    public void incrementVersion() {
        if (this.version == null) {
            this.version = 1;
        } else {
            this.version++;
        }
    }
}
