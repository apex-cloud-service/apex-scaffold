package com.apex.scaffold.auth.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 密码找回验证码校验响应。
 *
 * @author Apex Security Team
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "密码找回验证码校验响应")
public class PasswordRecoveryVerifyResponse {

    @Schema(description = "一次性确认令牌，仅用于本恢复单的确认重设")
    private String confirmToken;

    @Schema(description = "确认令牌有效期（秒）", example = "600")
    private Long expiresIn;
}
