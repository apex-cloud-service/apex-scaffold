package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 微信手机号绑定请求：使用短信验证码校验手机号后与微信授权信息绑定。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class WechatBindPhoneRequest {

    @Schema(description = "微信授权临时票据")
    private String wechatTicket;

    @Schema(description = "待绑定手机号", example = "13800138000")
    private String phone;

    @Schema(description = "短信验证码")
    private String smsCode;

    @Schema(description = "设备 ID")
    private String deviceId;

    @Schema(description = "客户端 IP")
    private String clientIp;
}
