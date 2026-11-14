package com.apex.scaffold.application.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 安全策略配置请求对象
 *
 * <p>用于更新系统安全策略相关的配置参数，包括密码策略、登录锁定策略、验证码策略等。
 * 所有参数均包含 JSR-303 校验注解以确保数据合法性。
 *
 * @author Apex Scaffold Generator
 * @since 1.3.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "安全策略配置请求对象")
public class SecurityPolicyRequest {

    @NotNull(message = "密码最小长度不能为空")
    @Min(value = 6, message = "密码最小长度不能小于 6")
    @Max(value = 32, message = "密码最小长度不能大于 32")
    @Schema(description = "密码最小长度", required = true, example = "8")
    private Integer passwordMinLength;

    @NotNull(message = "是否要求密码包含大写字母不能为空")
    @Schema(description = "是否要求密码包含大写字母", required = true, example = "true")
    private Boolean passwordRequireUppercase;

    @NotNull(message = "是否要求密码包含小写字母不能为空")
    @Schema(description = "是否要求密码包含小写字母", required = true, example = "true")
    private Boolean passwordRequireLowercase;

    @NotNull(message = "是否要求密码包含数字不能为空")
    @Schema(description = "是否要求密码包含数字", required = true, example = "true")
    private Boolean passwordRequireDigit;

    @NotNull(message = "是否要求密码包含特殊字符不能为空")
    @Schema(description = "是否要求密码包含特殊字符", required = true, example = "false")
    private Boolean passwordRequireSpecialChar;

    @NotNull(message = "密码过期天数不能为空")
    @Min(value = 0, message = "密码过期天数不能为负数")
    @Schema(description = "密码过期天数（0 表示永不过期）", required = true, example = "90")
    private Integer passwordExpirationDays;

    @NotNull(message = "密码历史限制不能为空")
    @Min(value = 0, message = "密码历史限制不能为负数")
    @Schema(description = "密码历史限制（不允许使用最近 N 次使用过的密码，0 表示不限制）", required = true, example = "3")
    private Integer passwordHistoryLimit;

    @NotNull(message = "登录失败锁定阈值不能为空")
    @Min(value = 3, message = "登录失败锁定阈值不能小于 3")
    @Schema(description = "登录失败锁定阈值（连续失败 N 次后锁定账户）", required = true, example = "5")
    private Integer loginFailureLockThreshold;

    @NotNull(message = "账户锁定时长不能为空")
    @Min(value = 60, message = "账户锁定时长不能小于 60 秒")
    @Schema(description = "账户锁定时长（秒）", required = true, example = "1800")
    private Integer accountLockDuration;

    @NotNull(message = "是否启用验证码不能为空")
    @Schema(description = "是否启用登录验证码", required = true, example = "true")
    private Boolean enableCaptcha;

    @NotNull(message = "验证码触发阈值不能为空")
    @Min(value = 0, message = "验证码触发阈值不能为负数")
    @Schema(description = "验证码触发阈值（登录失败 N 次后要求验证码，0 表示始终要求）", required = true, example = "3")
    private Integer captchaThreshold;

    @NotNull(message = "是否启用双因素认证不能为空")
    @Schema(description = "是否启用双因素认证", required = true, example = "false")
    private Boolean enableTwoFactorAuth;

    @NotNull(message = "是否启用 IP 白名单不能为空")
    @Schema(description = "是否启用 IP 白名单", required = true, example = "false")
    private Boolean enableIpWhitelist;

    @Schema(description = "IP 白名单（逗号分隔，支持 CIDR）", example = "192.168.1.0/24,10.0.0.1")
    private String ipWhitelist;
}
