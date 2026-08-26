package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求DTO
 *
 * <p>微信APP登录请求参数封装，支持多种登录方式。
 * 目前支持微信授权码登录、微信一键登录、微信短信验证码登录、
 * 以及创蓝一键登录（闪验）和创蓝短信验证码登录。
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 */
@Data
public class LoginRequest {

    /** 构造登录请求 DTO */
    public LoginRequest() {}

    /**
     * 登录类型
     *
     * <p>指定登录方式：WX_CODE-微信验证码登录，WX_PHONE-微信一键登录，WX_SMS-短信验证码登录
     * 默认值为 WX_CODE
     */
    @Schema(description = "登录类型")
    private String loginType = "WX_CODE";

    /**
     * 微信授权码（code）
     *
     * <p>用户同意授权后，微信返回的授权码，有效期5分钟
     * 用于 WX_CODE 登录方式
     */
    @Schema(description = "微信授权码")
    @Size(min = 1, max = 100, message = "{param.size_invalid}")
    private String code;

    /**
     * 微信手机号授权凭证（phoneCode）
     *
     * <p>由App端通过微信SDK获取的手机号授权凭证
     * 用于 WX_PHONE 登录方式
     */
    @Schema(description = "手机号授权码（可选）")
    @Size(min = 1, max = 200, message = "{param.size_invalid}")
    private String phoneCode;

    /**
     * 创蓝闪验token
     *
     * <p>App端集成创蓝闪验SDK后，用户授权一键登录时SDK返回的临时令牌。
     * 服务端通过该token调用创蓝闪验API置换用户手机号。
     * 用于 CL_PHONE 创蓝一键登录方式。
     */
    @Schema(description = "创蓝闪验Token")
    @Size(max = 500, message = "{param.size_invalid}")
    private String clToken;

    /**
     * 手机号
     *
     * <p>用户输入的手机号码
     * 用于 WX_SMS 短信验证码登录方式和 CL_SMS 创蓝短信验证码登录方式
     */
    @Schema(description = "手机号")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "{param.pattern_invalid}")
    private String phone;

    /**
     * 短信验证码
     *
     * <p>用户收到的短信验证码
     * 用于 WX_SMS 短信验证码登录方式
     */
    @Schema(description = "短信验证码")
    @Size(min = 6, max = 6, message = "{param.size_invalid}")
    private String smsCode;

    /**
     * 场景值（可选）
     *
     * <p>用于统计用户来源
     */
    @Schema(description = "场景标识")
    @Pattern(regexp = "^[0-9]*$", message = "{param.pattern_invalid}")
    @Size(max = 50, message = "{param.size_invalid}")
    private String scene;

    /**
     * 设备标识
     *
     * <p>客户端生成的唯一设备标识，用于绑定登录设备。
     * 在 Token 刷新时校验设备一致性，防止 Token 被跨设备盗用。
     *
     */
    @Schema(description = "设备标识（可选），用于绑定登录设备，增强Token刷新安全性")
    @Size(max = 200, message = "{param.size_invalid}")
    private String deviceId;

    /**
     * 客户端IP地址
     *
     * <p>从HTTP请求中提取的客户端真实IP，用于安全审计和异常登录检测。
     * 由 {@code AuthHandler} 在接收请求时自动设置，各登录策略无需手动处理。
     *
     */
    @Schema(description = "客户端IP地址（自动填充）", hidden = true)
    private String clientIp;

    /**
     * 邀请人用户ID（可选）
     *
     * <p>用于邀请关系绑定
     */
    @Schema(description = "邀请用户ID")
    @Pattern(regexp = "^[0-9]*$", message = "{param.pattern_invalid}")
    @Size(max = 20, message = "{param.size_invalid}")
    private String inviteUserId;

    /**
     * 客户端平台标识。
     *
     * <p>P0-CL-9修复：根据创蓝文档「一个应用对应一个appid」的要求，
     * 不同平台需使用各自的 AppID/AppKey 进行闪验 token 置换。
     * 常见值：android、ios、h5、harmony、wechat。
     * 为空时默认使用 android 平台配置。</p>
     *
     * @since 3.1.0
     */
    @Schema(description = "客户端平台标识（android/ios/h5/harmony/wechat）")
    @Size(max = 20, message = "{param.size_invalid}")
    private String platform;
}
