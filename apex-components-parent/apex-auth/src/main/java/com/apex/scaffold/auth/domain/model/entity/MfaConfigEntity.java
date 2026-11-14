package com.apex.scaffold.auth.domain.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MFA（Multi-Factor Authentication）配置实体
 *
 * <p>管理用户的多因素认证配置，包括 MFA 类型、启用状态和强制要求。
 * 支持多种 MFA 类型：TOTP、SMS、EMAIL、WEBAUTHN 等。
 *
 * @author Apex Scaffold Team
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MfaConfigEntity {

    /**
     * 配置 ID
     */
    private Long id;

    /**
     * 租户 ID
     */
    private String tenantId;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * MFA 类型
     *
     * <p>支持的类型：
     * <ul>
     *   <li>TOTP: Time-based One-Time Password（Google Authenticator）</li>
     *   <li>SMS: 短信验证码</li>
     *   <li>EMAIL: 邮件验证码</li>
     *   <li>WEBAUTHN: WebAuthn/FIDO2 硬件密钥</li>
     * </ul>
     */
    private String mfaType;

    /**
     * 是否启用
     *
     * <p>用户可以选择启用或禁用 MFA，但如果租户级别要求强制 MFA，则无法禁用。
     */
    private Boolean enabled;

    /**
     * 是否必需
     *
     * <p>租户级别的配置，强制用户启用 MFA。
     */
    private Boolean required;

    /**
     * 优先级
     *
     * <p>当用户启用多种 MFA 方式时，按优先级选择默认方式。
     * 数值越小优先级越高。
     */
    private Integer priority;

    /**
     * 配置元数据（JSON 格式）
     *
     * <p>存储特定 MFA 类型的额外配置，例如：
     * <ul>
     *   <li>SMS: 手机号</li>
     *   <li>EMAIL: 邮箱地址</li>
     *   <li>WEBAUTHN: 凭证 ID</li>
     * </ul>
     */
    private String metadata;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 乐观锁版本号
     */
    private Integer version;

    /**
     * 逻辑删除标记
     */
    private Boolean deleted;

    /**
     * MFA 类型枚举
     */
    public enum MfaType {
        /** TOTP（基于时间的一次性密码）因子。 */
        TOTP,

        /**
         * SMS（短信验证码）
         */
        SMS,

        /**
         * EMAIL（邮件验证码）
         */
        EMAIL,

        /**
         * WEBAUTHN（WebAuthn/FIDO2 硬件密钥）
         */
        WEBAUTHN
    }

    /**
     * 启用 MFA
     */
    public void enable() {
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 禁用 MFA
     *
     * @throws IllegalStateException 如果 MFA 是强制要求的
     */
    public void disable() {
        if (Boolean.TRUE.equals(this.required)) {
            throw new IllegalStateException("Cannot disable required MFA");
        }
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 设置为必需
     */
    public void makeRequired() {
        this.required = true;
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 取消必需
     */
    public void makeOptional() {
        this.required = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 检查是否已启用
     *
     * @return 是否已启用
     */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.enabled);
    }

    /**
     * 检查是否必需
     *
     * @return 是否必需
     */
    public boolean isRequired() {
        return Boolean.TRUE.equals(this.required);
    }

    /**
     * 检查 MFA 类型是否匹配
     *
     * @param type MFA 类型
     * @return 是否匹配
     */
    public boolean isMfaType(String type) {
        return this.mfaType != null && this.mfaType.equalsIgnoreCase(type);
    }

    /**
     * 检查 MFA 类型是否匹配
     *
     * @param type MFA 类型枚举
     * @return 是否匹配
     */
    public boolean isMfaType(MfaType type) {
        return this.mfaType != null && this.mfaType.equalsIgnoreCase(type.name());
    }
}
