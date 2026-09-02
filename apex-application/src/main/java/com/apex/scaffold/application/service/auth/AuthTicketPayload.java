package com.apex.scaffold.application.service.auth;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * Redis 中保存的短期认证上下文。
 *
 *
 *<p>承载用户在注册/绑定多步流程中的临时身份信息，包括微信授权 Token
 *以及从微信接口拉取的用户资料（昵称、头像、地区等），确保多步流程中
 *用户信息不丢失。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
public class AuthTicketPayload {
    private AuthTicketType type;
    /** 签发时间（毫秒）；用于账号注销后阻断旧的多步注册/绑定票据。 */
    private Long issuedAtEpochMs;
    /** 签发票据时的设备标识；存在时后续步骤必须由同一设备完成。 */
    private String deviceId;

    private String phone;
    private String openId;
    private String unionId;
    private String wechatAccessToken;
    private String wechatRefreshToken;
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
}
