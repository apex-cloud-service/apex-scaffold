package com.apex.scaffold.authcenter.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.*;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MFA 配置持久化对象
 *
 * <p>对应数据库表 auth_mfa_config
 *
 * @author Apex Team
 * @since 2026-09-21
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("auth_mfa_config")
public class MfaConfigPO {

    /**
     * 配置 ID
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
     * MFA 类型（TOTP、SMS、EMAIL、WEBAUTHN）
     */
    private String mfaType;

    /**
     * 是否启用（0-禁用，1-启用）
     */
    private Integer enabled;

    /**
     * 是否必需（0-非必需，1-必需）
     */
    private Integer required;

    /**
     * 优先级
     */
    private Integer priority;

    /**
     * 配置元数据（JSON 格式）
     */
    private String metadata;

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
