package com.apex.scaffold.auth.domain.model.entity;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 自助密码找回恢复实体。
 *
 * <p>状态机：REQUESTED → VERIFIED → CONSUMED，异常路径 EXPIRED（过期或多次校验失败）与
 * MANUAL_REQUIRED（联系方式命中多个账号，走受审计的人工恢复）。任何状态跃迁必须通过仓储的
 * 条件更新完成，保证并发下最多一次成功。</p>
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordRecoveryEntity {

    /** 恢复通道：已验证邮箱。 */
    public static final String CHANNEL_EMAIL = "EMAIL";

    /** 恢复通道：已验证手机号（当前投递实现未接入，通道暂不接受申请）。 */
    public static final String CHANNEL_SMS = "SMS";

    /** 已申请，等待验证码校验。 */
    public static final String STATUS_REQUESTED = "REQUESTED";

    /** 验证码已通过，等待确认重设。 */
    public static final String STATUS_VERIFIED = "VERIFIED";

    /** 密码已重设，流程终结。 */
    public static final String STATUS_CONSUMED = "CONSUMED";

    /** 已过期或多次校验失败被阻断。 */
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** 联系方式命中多个账号，需受审计的人工恢复。 */
    public static final String STATUS_MANUAL_REQUIRED = "MANUAL_REQUIRED";

    /** 恢复单ID（UUID）。 */
    private String id;

    /** 解析命中的账号ID；多命中或人工恢复场景为空。 */
    private Long userId;

    /** 解析命中账号所属租户；由服务端可信入口解析，客户端不可指定。 */
    private String tenantId;

    /** 命中账号用户名，用于密码策略校验。 */
    private String username;

    /** 恢复通道：EMAIL / SMS。 */
    private String channel;

    /** 联系方式 SHA-256 摘要，不存明文。 */
    private String contactHash;

    /** 恢复单状态。 */
    private String status;

    /** 验证码校验失败次数。 */
    private Integer attemptCount;

    /** 申请来源 IP 摘要，仅用于限流与审计。 */
    private String requestIpHash;

    /** 验证码过期时间。 */
    private LocalDateTime expiresAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
