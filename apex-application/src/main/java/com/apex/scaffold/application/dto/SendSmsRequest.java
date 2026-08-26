package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 发送短信验证码请求。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class SendSmsRequest {

    @Schema(description = "目标手机号", example = "13800138000")
    private String phone;
}
