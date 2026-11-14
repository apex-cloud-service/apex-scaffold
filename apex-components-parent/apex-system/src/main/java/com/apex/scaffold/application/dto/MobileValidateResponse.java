package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 本机号校验响应（创蓝闪验）
 *
 * <p>用于返回创蓝闪验服务的本机号码校验结果。闪验是一种基于运营商网关的
 * 号码验证技术，可以在不发送短信验证码的情况下，快速验证用户当前使用的
 * 手机号是否与 SIM 卡号码一致。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>一键登录：用户无需输入手机号和验证码，直接完成登录</li>
 *   <li>快速注册：验证用户手机号真实性，减少短信成本</li>
 *   <li>安全验证：在敏感操作前快速验证用户身份</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li>客户端获取运营商网关 Token</li>
 *   <li>将 Token 发送到后端</li>
 *   <li>后端调用创蓝闪验 API 进行验证</li>
 *   <li>返回此 DTO，包含校验结果</li>
 * </ol>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "手机号校验响应")
public class MobileValidateResponse {

    /**
     * 校验结果
     *
     * <p>标识当前设备使用的手机号是否与待验证的手机号一致。
     *
     * <p>取值：
     * <ul>
     *   <li>{@code "1"}：本机号码，验证通过</li>
     *   <li>{@code "0"}：非本机号码，验证失败</li>
     * </ul>
     *
     * <p>注意：此字段使用字符串类型以保持与创蓝 API 响应格式一致
     */
    @Schema(
            description = "校验结果: 1=本机号码, 0=非本机号码",
            allowableValues = {"0", "1"})
    private String isVerify;

    /**
     * 交易流水号
     *
     * <p>创蓝闪验服务返回的唯一交易标识，用于问题排查和对账。
     * 每次校验请求都会生成一个唯一的流水号。
     *
     * <p>示例：{@code CL20261209123456789012}
     */
    @Schema(description = "交易流水号（创蓝返回的唯一标识）", example = "CL20261209123456789012")
    private String tradeNo;

    /**
     * 是否校验成功
     *
     * <p>标识本次校验请求是否成功完成。即使号码不匹配，只要请求正常完成，
     * 此字段也应为 {@code true}。只有在网络异常、参数错误等情况下才为 {@code false}。
     *
     * <p>判断逻辑：
     * <ul>
     *   <li>{@code true}：请求成功，{@code isVerify} 字段有效</li>
     *   <li>{@code false}：请求失败，{@code isVerify} 和 {@code tradeNo} 均为 null</li>
     * </ul>
     */
    @Schema(description = "是否校验成功（请求是否正常完成，与号码是否匹配无关）")
    private Boolean success;

    /**
     * 构造校验成功响应
     *
     * <p>用于创蓝闪验 API 调用成功时构造响应对象。
     *
     * @param isVerify 校验结果（"1" 表示本机号码，"0" 表示非本机号码）
     * @param tradeNo  交易流水号
     * @return 校验成功的响应对象
     */
    public static MobileValidateResponse success(String isVerify, String tradeNo) {
        return new MobileValidateResponse(isVerify, tradeNo, true);
    }

    /**
     * 构造校验失败响应
     *
     * <p>用于创蓝闪验 API 调用失败时构造响应对象（如网络异常、参数错误等）。
     *
     * @return 校验失败的响应对象，所有字段为 null 或 false
     */
    public static MobileValidateResponse failure() {
        return new MobileValidateResponse(null, null, false);
    }
}
