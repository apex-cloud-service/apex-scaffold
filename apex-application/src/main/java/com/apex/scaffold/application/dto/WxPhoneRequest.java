package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信手机号授权请求（微信小程序手机号快捷验证）。
 *
 * <p>用于微信小程序场景下，通过微信 SDK 返回的手机号授权凭证（{@code phoneCode}）换取用户手机号。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code phoneCode} - 微信手机号快捷验证 code（临时凭证）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
/**n * WxPhoneRequestn *n * <p>TODO: 补充类的详细说明n *n * @author Apex Teamn * @version 1.0.0n * @since 1.0.0n */
public class WxPhoneRequest {

    @Schema(description = "微信手机号快捷验证 code（临时凭证）")
    private String phoneCode;
}
