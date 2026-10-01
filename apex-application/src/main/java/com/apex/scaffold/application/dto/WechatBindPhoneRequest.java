package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 微信手机号绑定请求。
 *
 * <p>使用短信验证码校验手机号后，将手机号与微信授权信息绑定。</p>
 *
 * <p>主要字段：</p>
 * <ul>
 *   <li>{@code wechatTicket} - 微信授权临时票据</li>
 *   <li>{@code phone} - 待绑定手机号</li>
 *   <li>{@code smsCode} - 短信验证码</li>
 *   <li>{@code deviceId} - 设备 ID</li>
 *   <li>{@code clientIp} - 客户端 IP</li>
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
@Schema(description = "微信手机号绑定请求")
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
