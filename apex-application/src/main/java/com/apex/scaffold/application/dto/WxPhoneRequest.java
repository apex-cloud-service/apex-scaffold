package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 微信手机号授权请求（微信小程序手机号快捷验证）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class WxPhoneRequest {

    @Schema(description = "微信手机号快捷验证 code（临时凭证）")
    private String phoneCode;
}
