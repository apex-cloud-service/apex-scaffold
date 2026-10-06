package com.apex.scaffold.application.service.auth;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Redis 中保存的短期认证上下文。
 *
 * <p>承载用户在注册/绑定多步流程中的临时身份信息，包括微信授权 Token
 * 以及从微信接口拉取的用户资料（昵称、头像、地区等），确保多步流程中
 * 用户信息不丢失。</p>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>微信授权登录后，签发票据携带 openId 与 Token</li>
 *   <li>短信验证通过后，签发票据携带手机号</li>
 *   <li>最终注册/绑定时，消费票据完成流程闭环</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class AuthTicketPayload {
    /** 票据类型（绑定/注册）。 */
    private AuthTicketType type;
    /** 签发时间（毫秒）；用于账号注销后阻断旧的多步注册/绑定票据。 */
    private Long issuedAtEpochMs;
    /** 签发票据时的设备标识；存在时后续步骤必须由同一设备完成。 */
    private String deviceId;

    /** 手机号。 */
    private String phone;
    /** 微信 openId。 */
    private String openId;
    /** 微信 unionId。 */
    private String unionId;
    /** 微信 access_token。 */
    private String wechatAccessToken;
    /** 微信 refresh_token。 */
    private String wechatRefreshToken;
    /** 微信 Token 过期时间。 */
    private LocalDateTime wechatTokenExpireTime;

    /** 微信昵称（从 /sns/userinfo 拉取）。 */
    private String nickName;
    /** 微信头像 URL（从 /sns/userinfo 拉取）。 */
    private String avatarUrl;
    /** 微信性别：0-未知, 1-男, 2-女（从 /sns/userinfo 拉取）。 */
    private Integer gender;
    /** 微信国家（从 /sns/userinfo 拉取）。 */
    private String country;
    /** 微信省份（从 /sns/userinfo 拉取）。 */
    private String province;
    /** 微信城市（从 /sns/userinfo 拉取）。 */
    private String city;
    /** 微信语言（从 /sns/userinfo 拉取）。 */
    private String language;

    /** 注册来源（{@link com.apex.scaffold.domain.enums.RegisterChannel} 编码），从登录请求推导。 */
    private String registerChannel;

    /**
     * 社交渠道（{@link com.apex.scaffold.domain.enums.SocialChannel} 编码）。
     *
     * <p>标识票据中 openId 所属的微信渠道（开放平台 App / 小程序 / 公众号），
     * 注册/绑定完成后据此写入 {@code sys_user_social} 对应渠道的绑定行；
     * 为空时按 WECHAT_APP 处理（兼容批次 1 之前的存量票据）。
     *
     * @since 3.3.0
     */
    private String channel;
}
