package com.apex.scaffold.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 自助密码找回恢复单 PO。
 *
 * <p>联系方式与来源 IP 只存 SHA-256 摘要，不存明文，不写入日志。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Data
@TableName("auth_password_recovery")
public class PasswordRecoveryPO {

    /** 恢复单ID（UUID）。 */
    @TableId(type = IdType.INPUT)
    private String id;

    /** 解析命中的账号ID；多命中或人工恢复场景为空。 */
    private Long userId;

    /** 解析命中账号所属租户。 */
    private String tenantId;

    /** 命中账号用户名，用于密码策略校验。 */
    private String username;

    /** 恢复通道：EMAIL / SMS。 */
    private String channel;

    /** 联系方式 SHA-256 摘要。 */
    private String contactHash;

    /** 恢复单状态：REQUESTED/VERIFIED/CONSUMED/EXPIRED/MANUAL_REQUIRED。 */
    private String status;

    /** 验证码校验失败次数。 */
    private Integer attemptCount;

    /** 申请来源 IP 摘要。 */
    private String requestIpHash;

    /** 验证码过期时间。 */
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
