package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 账号注销二次验证请求。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Schema(name = "AccountDeletionRequest", description = "账号注销短信二次验证请求")
public class AccountDeletionRequest {

    /**
     * 短信验证码。
     *
     * <p>手机号始终从当前登录账号读取，客户端不能通过请求体指定手机号。</p>
     */
    @NotBlank(message = "注销验证码不能为空")
    @Pattern(regexp = "\\d{6}", message = "注销验证码必须为6位数字")
    @Schema(description = "发送到当前账号绑定手机号的 6 位短信验证码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String code;

    /** @return 验证码 */
    public String getCode() {
        return code;
    }

    /** @param code 验证码 */
    public void setCode(String code) {
        this.code = code;
    }
}
