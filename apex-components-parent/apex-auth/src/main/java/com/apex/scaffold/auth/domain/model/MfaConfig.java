package com.apex.scaffold.auth.domain.model;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * 多因素认证(MFA)配置领域模型
 * @author Apex Team
 * @since 1.2.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("mfa_config")
public class MfaConfig {

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
     * MFA类型
     */
    private MfaType type;

    /**
     * MFA状态
     */
    private MfaStatus status;

    /**
     * 密钥（加密存储）
     */
    private String secret;

    /**
     * 备用验证码（加密存储，逗号分隔）
     */
    private String backupCodes;

    /**
     * 设备信息（用于设备绑定）
     */
    private String deviceInfo;

    /**
     * 最后验证时间
     */
    private LocalDateTime lastVerifiedAt;

    /**
     * 验证失败次数
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
     * MFA类型枚举
     */
    public enum MfaType {
        /**
         * TOTP - 基于时间的一次性密码（Google Authenticator）
         */
        TOTP,

        /**
         * SMS - 短信验证码
         */
        SMS,

        /**
         * EMAIL - 邮箱验证码
         */
        EMAIL,

        /**
         * BACKUP_CODE - 备用验证码
         */
        BACKUP_CODE
    }

    /**
     * MFA状态枚举
     */
    public enum MfaStatus {
        /**
         * 待激活 - 已配置但未完成验证
         */
        PENDING,

        /**
         * 已激活 - 正常使用中
         */
        ACTIVE,

        /**
         * 已禁用 - 用户主动禁用
         */
        DISABLED,

        /**
         * 已锁定 - 验证失败次数过多
         */
        LOCKED
    }

    /**
     * 判断是否已激活
     *
     * @return MFA状态为激活时返回true，否则返回false
     */
    public boolean isActive() {
        return MfaStatus.ACTIVE.equals(status);
    }

    /**
     * 判断是否已锁定
     *
     * @return MFA状态为锁定时返回true，否则返回false
     */
    public boolean isLocked() {
        return MfaStatus.LOCKED.equals(status);
    }

    /**
     * 增加失败次数
     */
    private static final int MAX_FAILED_ATTEMPTS = 5;

    /**
     * 增加失败次数，达到上限时锁定MFA。
     */
    public void incrementFailedAttempts() {
        this.failedAttempts = (this.failedAttempts == null ? 0 : this.failedAttempts) + 1;

        if (this.failedAttempts >= MAX_FAILED_ATTEMPTS) {
            this.status = MfaStatus.LOCKED;
        }
    }

    /**
     * 重置失败次数
     */
    public void resetFailedAttempts() {
        this.failedAttempts = 0;
        if (MfaStatus.LOCKED.equals(this.status)) {
            this.status = MfaStatus.ACTIVE;
        }
    }

    /**
     * 更新最后验证时间
     */
    public void updateLastVerified() {
        this.lastVerifiedAt = LocalDateTime.now();
        resetFailedAttempts();
    }
}
