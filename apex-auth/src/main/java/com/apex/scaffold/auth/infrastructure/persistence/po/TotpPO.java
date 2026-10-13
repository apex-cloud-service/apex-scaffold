package com.apex.scaffold.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * TOTP 持久化对象
 *
 * <p>对应数据库表 auth_totp
 *
 * @author Apex Team
 * @since 2026-09-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_totp")
public class TotpPO {

    /**
     * TOTP 配置 ID
     */
    @TableId(type = IdType.AUTO)
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
     */
    @ToString.Exclude
    private String secret;

    /**
     * 是否已验证（0-未验证，1-已验证）
     */
    private Integer verified;

    /**
     * 备份码列表（加密存储，逗号分隔）
     */
    @ToString.Exclude
    private String backupCodes;

    /**
     * 已使用的备份码列表（逗号分隔）
     */
    @ToString.Exclude
    private String usedBackupCodes;

    /**
     * 启用状态（0-禁用，1-启用）
     */
    private Integer enabled;

    /**
     * 最后验证时间
     */
    private LocalDateTime lastVerifiedAt;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    /**
     * 逻辑删除标记（0-未删除，1-已删除）
     */
    @TableLogic
    private Integer deleted;
}
