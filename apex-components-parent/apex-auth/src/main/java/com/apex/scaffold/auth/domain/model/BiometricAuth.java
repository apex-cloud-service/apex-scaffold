package com.apex.scaffold.auth.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 生物识别认证配置领域模型
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("biometric_auth")
public class BiometricAuth {

    /**
     * 主键ID
     */
    @Id
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 租户ID
     */
    private String tenantId;

    /**
     * 生物识别类型
     */
    private BiometricType type;

    /**
     * 生物特征模板ID（加密存储）
     */
    private String templateId;

    /**
     * 设备ID（绑定设备）
     */
    private String deviceId;

    /**
     * 设备名称
     */
    private String deviceName;

    /**
     * 公钥（用于验证签名）
     */
    private String publicKey;

    /**
     * 认证状态
     */
    private AuthStatus status;

    /**
     * 最后认证时间
     */
    private LocalDateTime lastAuthAt;

    /**
     * 认证失败次数
     */
    private Integer failedAttempts;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 生物识别类型枚举
     */
    public enum BiometricType {
        /**
         * 指纹识别
         */
        FINGERPRINT,

        /**
         * 人脸识别
         */
        FACE_ID,

        /**
         * 虹膜识别
         */
        IRIS,

        /**
         * 声纹识别
         */
        VOICE,

        /**
         * WebAuthn/FIDO2（支持硬件安全密钥）
         */
        WEBAUTHN
    }

    /**
     * 认证状态枚举
     */
    public enum AuthStatus {
        /**
         * 待激活
         */
        PENDING,

        /**
         * 已激活
         */
        ACTIVE,

        /**
         * 已禁用
         */
        DISABLED,

        /**
         * 已锁定
         */
        LOCKED
    }

    /**
     * 判断是否已激活
     *
     * @return 认证状态为激活时返回true，否则返回false
     */
    public boolean isActive() {
        return AuthStatus.ACTIVE.equals(status);
    }

    /**
     * 判断是否已锁定
     *
     * @return 认证状态为锁定时返回true，否则返回false
     */
    public boolean isLocked() {
        return AuthStatus.LOCKED.equals(status);
    }

    /**
     * 增加失败次数
     */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /**
     * 增加失败次数，达到上限时锁定认证。
     */
    public void incrementFailedAttempts() {
        this.failedAttempts = (this.failedAttempts == null ? 0 : this.failedAttempts) + 1;

        if (this.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            this.status = AuthStatus.LOCKED;
        }
    }

    /**
     * 重置失败次数
     */
    public void resetFailedAttempts() {
        this.failedAttempts = 0;
        if (AuthStatus.LOCKED.equals(this.status)) {
            this.status = AuthStatus.ACTIVE;
        }
    }

    /**
     * 更新最后认证时间
     */
    public void updateLastAuth() {
        this.lastAuthAt = LocalDateTime.now();
        resetFailedAttempts();
    }
}
