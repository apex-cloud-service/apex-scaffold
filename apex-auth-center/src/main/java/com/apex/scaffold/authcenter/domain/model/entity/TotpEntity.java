package com.apex.scaffold.authcenter.domain.model.entity;

import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TOTP（Time-based One-Time Password）实体
 *
 * <p>用于管理用户的 TOTP 多因素认证配置，包括密钥、验证状态和备份码。
 * TOTP 是一种基于时间的一次性密码算法，常用于 Google Authenticator 等认证器应用。
 *
 * @author Apex Scaffold Team
 * @since 2026-09-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TotpEntity {

    private static final int CODE_DIGITS = 6;

    /**
     * TOTP 配置 ID
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
     * TOTP 密钥（加密存储）
     *
     * <p>该密钥用于生成 TOTP 验证码，必须安全存储，通常使用 AES-256-GCM 加密。
     */
    private String secret;

    /**
     * 是否已验证
     *
     * <p>用户首次设置 TOTP 后需要验证一次，验证通过后才能启用 MFA。
     */
    private Boolean verified;

    /**
     * 备份码列表（加密存储，逗号分隔）
     *
     * <p>当用户无法访问认证器应用时，可以使用备份码登录。
     * 每个备份码只能使用一次，使用后应标记为已使用。
     */
    private String backupCodes;

    /**
     * 已使用的备份码列表（逗号分隔）
     */
    private String usedBackupCodes;

    /**
     * 启用状态
     */
    private Boolean enabled;

    /**
     * 最后验证时间
     */
    private LocalDateTime lastVerifiedAt;

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
     * 验证 TOTP 密钥
     *
     * @param code 用户输入的 6 位验证码
     * @return 验证是否成功
     */
    public boolean verify(String code) {
        if (code == null || code.length() != CODE_DIGITS) {
            return false;
        }

        // 实际验证逻辑在 TotpService 中实现
        return true;
    }

    /**
     * 标记为已验证
     */
    public void markAsVerified() {
        this.verified = true;
        this.lastVerifiedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 启用 TOTP
     */
    public void enable() {
        if (!Boolean.TRUE.equals(this.verified)) {
            throw new IllegalStateException("TOTP must be verified before enabling");
        }
        this.enabled = true;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 禁用 TOTP
     */
    public void disable() {
        this.enabled = false;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 使用备份码
     *
     * @param backupCode 备份码
     * @return 是否成功使用
     */
    public boolean useBackupCode(String backupCode) {
        if (backupCode == null || this.backupCodes == null) {
            return false;
        }

        // 检查备份码是否有效
        List<String> codes = List.of(this.backupCodes.split(","));
        if (!codes.contains(backupCode)) {
            return false;
        }

        // 检查是否已使用
        if (this.usedBackupCodes != null && this.usedBackupCodes.contains(backupCode)) {
            return false;
        }

        // 标记为已使用
        if (this.usedBackupCodes == null) {
            this.usedBackupCodes = backupCode;
        } else {
            this.usedBackupCodes = this.usedBackupCodes + "," + backupCode;
        }

        this.lastVerifiedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        return true;
    }

    /**
     * 检查是否已启用
     *
     * @return 是否已启用
     */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(this.enabled) && Boolean.TRUE.equals(this.verified);
    }

    /**
     * 获取剩余备份码数量
     *
     * @return 剩余备份码数量
     */
    public int getRemainingBackupCodes() {
        if (this.backupCodes == null) {
            return 0;
        }

        int total = this.backupCodes.split(",").length;
        int used = this.usedBackupCodes == null ? 0 : this.usedBackupCodes.split(",").length;
        return total - used;
    }
}
