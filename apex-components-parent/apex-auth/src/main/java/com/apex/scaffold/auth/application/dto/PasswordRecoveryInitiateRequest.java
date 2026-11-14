package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 密码找回申请请求。
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Data
@Schema(description = "密码找回申请请求")
public class PasswordRecoveryInitiateRequest {

    @NotBlank(message = "恢复通道不能为空")
    @Pattern(regexp = "EMAIL|SMS", message = "恢复通道仅支持 EMAIL 或 SMS")
    @Schema(description = "恢复通道：EMAIL-已验证邮箱，SMS-已验证手机号", example = "EMAIL", requiredMode = Schema.RequiredMode.REQUIRED)
    private String channel;

    @NotBlank(message = "联系方式不能为空")
    @Schema(
            description = "已验证邮箱或手机号（服务端按通道校验格式，不写入日志）",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
    private String contact;
}
