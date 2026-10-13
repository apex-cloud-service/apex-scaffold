package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 安全策略配置响应对象
 *
 * <p>用于返回系统安全策略配置信息，包括密码策略、登录锁定策略、验证码策略等。
 * 包含审计字段以追踪配置的创建和更新历史。
 *
 * @author Apex Scaffold Generator
 * @since 2026-09-19
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "安全策略配置响应对象")
public class SecurityPolicyDTO {

    @Schema(description = "配置 ID", example = "1")
    private Long id;

    @Schema(description = "租户 ID", example = "1")
    private Long tenantId;

    @Schema(description = "密码最小长度", example = "8")
    private Integer passwordMinLength;

    @Schema(description = "是否要求密码包含大写字母", example = "true")
    private Boolean passwordRequireUppercase;

    @Schema(description = "是否要求密码包含小写字母", example = "true")
    private Boolean passwordRequireLowercase;

    @Schema(description = "是否要求密码包含数字", example = "true")
    private Boolean passwordRequireDigit;

    @Schema(description = "是否要求密码包含特殊字符", example = "false")
    private Boolean passwordRequireSpecialChar;

    @Schema(description = "密码过期天数（0 表示永不过期）", example = "90")
    private Integer passwordExpirationDays;

    @Schema(description = "密码历史限制（不允许使用最近 N 次使用过的密码）", example = "3")
    private Integer passwordHistoryLimit;

    @Schema(description = "登录失败锁定阈值", example = "5")
    private Integer loginFailureLockThreshold;

    @Schema(description = "账户锁定时长（秒）", example = "1800")
    private Integer accountLockDuration;

    @Schema(description = "是否启用登录验证码", example = "true")
    private Boolean enableCaptcha;

    @Schema(description = "验证码触发阈值（0 表示始终要求）", example = "3")
    private Integer captchaThreshold;

    @Schema(description = "是否启用双因素认证", example = "false")
    private Boolean enableTwoFactorAuth;

    @Schema(description = "是否启用 IP 白名单", example = "false")
    private Boolean enableIpWhitelist;

    @Schema(description = "IP 白名单", example = "192.168.1.0/24,10.0.0.1")
    private String ipWhitelist;

    @Schema(description = "创建时间", example = "2026-09-19T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间", example = "2026-09-19T10:00:00")
    private LocalDateTime updatedAt;

    @Schema(description = "创建人 ID", example = "1")
    private Long createdBy;

    @Schema(description = "更新人 ID", example = "1")
    private Long updatedBy;
}
