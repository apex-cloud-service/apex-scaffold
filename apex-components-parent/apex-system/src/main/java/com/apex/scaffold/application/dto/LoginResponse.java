package com.apex.scaffold.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 *
 * <p>微信APP登录成功后的响应数据
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "登录响应")
public class LoginResponse {

    /**
     * 访问令牌（Access Token）。
     *
     * <p>用于后续 API 请求的 Bearer 认证，有效期较短（默认 2 小时）。</p>
     */
    @Schema(description = "访问令牌（accessToken），用于后续API请求的认证，有效期较短（默认2小时）")
    private String accessToken;

    /**
     * 刷新令牌（Refresh Token）。
     *
     * <p>用于在 Access Token 过期后换取新的 Access Token，有效期较长（默认 7 天）。</p>
     */
    @Schema(description = "刷新令牌（refreshToken），用于换取新的accessToken，有效期较长（默认7天）")
    private String refreshToken;

    /**
     * Token 类型。
     *
     * <p>固定为 Bearer。</p>
     */
    @Schema(description = "Token类型，固定为Bearer")
    private String tokenType;

    /**
     * Access Token 过期时间（秒）。
     *
     * <p>客户端可据此判断是否需要提前刷新 Token。</p>
     */
    @Schema(description = "accessToken过期时间（秒）")
    private Long expiresIn;

    /**
     * 微信 OpenID。
     *
     * <p>用户在微信开放平台的唯一标识。</p>
     */
    @Schema(description = "用户OpenID，微信用户的唯一标识")
    private String openId;

    /**
     * 微信 UnionID（可选）。
     *
     * <p>同一开放平台主体下的用户唯一标识，仅在已绑定微信开放平台时返回。</p>
     */
    @Schema(description = "用户UnionID（可选），同一主体下的唯一标识，需要绑定微信开放平台账号")
    private String unionId;

    /**
     * 是否新用户。
     *
     * <p>{@code true} 表示首次登录，客户端应引导注册流程；{@code false} 表示已有账号。</p>
     */
    @Schema(description = "是否新用户（true-首次登录，false-已存在用户）")
    private Boolean isNewUser;

    /**
     * 认证流程下一步动作。
     *
     * <p>客户端必须据此跳转：</p>
     * <ul>
     *   <li>{@code AUTHENTICATED} — 登录完成，可直接进入主页；</li>
     *   <li>{@code REGISTER_REQUIRED} — 需进入注册页完成资料填写；</li>
     *   <li>{@code PHONE_BIND_REQUIRED} — 微信用户需先验证手机号。</li>
     * </ul>
     */
    @Schema(
            description = "认证流程下一步；客户端必须据此跳转。AUTHENTICATED 表示登录完成，"
                    + "REGISTER_REQUIRED 表示进入注册页，PHONE_BIND_REQUIRED 表示微信用户先验证手机号",
            allowableValues = {"AUTHENTICATED", "REGISTER_REQUIRED", "PHONE_BIND_REQUIRED"})
    private String nextAction;

    /**
     * 一次性短期票据。
     *
     * <p>不同 {@code nextAction} 下返回不同票据：</p>
     * <ul>
     *   <li>{@code REGISTER_REQUIRED} — 返回 {@code registrationTicket}，传给注册接口；</li>
     *   <li>{@code PHONE_BIND_REQUIRED} — 返回 {@code wechatTicket}，传给微信绑定手机号接口；</li>
     *   <li>{@code AUTHENTICATED} — 不返回此字段。</li>
     * </ul>
     */
    @Schema(
            description = "一次性短期票据：REGISTER_REQUIRED 时传给注册接口的 registrationTicket；"
                    + "PHONE_BIND_REQUIRED 时传给微信绑定手机号接口的 wechatTicket；AUTHENTICATED 时不返回")
    private String authTicket;

    /**
     * 一次性票据剩余有效期（秒）。
     *
     * <p>{@code authTicket} 的剩余有效时间，客户端需在过期前使用。</p>
     */
    @Schema(description = "一次性票据剩余有效期（秒）")
    private Long authTicketExpiresIn;

    /**
     * 用户信息（可选）。
     *
     * <p>仅当用户已授权获取用户信息时返回，包含昵称、头像、性别等。</p>
     */
    @Schema(description = "用户信息（可选），如果用户授权获取用户信息，则返回用户信息")
    private UserInfo userInfo;

    /**
     * 用户信息内部类
     *
     * <p>封装微信用户的基本信息，包含昵称、头像、性别、地区等字段，
     * 在登录响应中随Token一起返回给客户端。
     *
     * @author Apex Team
     * @since 1.0.0
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        /**
         * 用户昵称。
         *
         * <p>微信用户设置的昵称。</p>
         */
        @Schema(description = "用户昵称")
        private String nickName;

        /**
         * 用户头像 URL。
         *
         * <p>微信用户头像的远程地址。</p>
         */
        @Schema(description = "用户头像URL")
        private String avatarUrl;

        /**
         * 用户性别。
         *
         * <p>取值：0-未知，1-男，2-女。</p>
         */
        @Schema(description = "用户性别（0-未知，1-男，2-女）")
        private Integer gender;

        /**
         * 用户手机号（可选）。
         *
         * <p>仅当用户授权获取手机号时返回。</p>
         */
        @Schema(description = "用户手机号（可选），如果用户授权获取手机号，则返回手机号")
        private String phone;

        /**
         * 生日。
         *
         * <p>格式为 {@code YYYY-MM-DD}。</p>
         */
        @Schema(description = "生日，格式为YYYY-MM-DD")
        private java.time.LocalDate birthday;

        /**
         * 国家。
         *
         * <p>微信资料中的国家信息。</p>
         */
        @Schema(description = "国家")
        private String country;

        /**
         * 省份。
         *
         * <p>微信资料中的省份信息。</p>
         */
        @Schema(description = "省份")
        private String province;

        /**
         * 城市。
         *
         * <p>微信资料中的城市信息。</p>
         */
        @Schema(description = "城市")
        private String city;
    }
}
