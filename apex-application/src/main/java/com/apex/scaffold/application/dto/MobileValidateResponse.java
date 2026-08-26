package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本机号校验响应（创蓝闪验）。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MobileValidateResponse {

    @Schema(description = "校验结果: 1=本机号码, 0=非本机号码")
    private String isVerify;

    @Schema(description = "交易流水号")
    private String tradeNo;

    @Schema(description = "是否校验成功")
    private Boolean success;

    /** 构造校验成功响应。 */
    public static MobileValidateResponse success(String isVerify, String tradeNo) {
        return new MobileValidateResponse(isVerify, tradeNo, true);
    }

    /** 构造校验失败响应。 */
    public static MobileValidateResponse failure() {
        return new MobileValidateResponse(null, null, false);
    }
}
