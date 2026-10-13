package com.apex.scaffold.office.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 办公平台登录响应 DTO
 *
 * <p>办公平台 OAuth 登录成功后返回的响应数据。为解耦单体应用层，本 DTO 在办公模块内
 * 独立维护，字段与单体 {@code com.apex.scaffold.application.dto.LoginResponse} 保持一致，
 * 由基础设施适配层负责跨模块转换。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
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
     * 外部平台 OpenID。
     *
     * <p>用户在办公平台的唯一标识。</p>
     */
    @Schema(description = "用户OpenID，办公平台用户的唯一标识")
    private String openId;

    /**
     * 外部平台 UnionID（可选）。
     *
     * <p>同一开放平台主体下的用户唯一标识。</p>
     */
    @Schema(description = "用户UnionID（可选），同一主体下的唯一标识")
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
     * <p>办公平台登录成功后固定返回 {@code AUTHENTICATED}，客户端可直接进入主页。</p>
     */
    @Schema(
            description = "认证流程下一步；办公平台登录成功固定为 AUTHENTICATED",
            allowableValues = {"AUTHENTICATED", "REGISTER_REQUIRED", "PHONE_BIND_REQUIRED"})
    private String nextAction;

    /**
     * 一次性短期票据。
     *
     * <p>办公平台登录成功场景不返回此字段。</p>
     */
    @Schema(description = "一次性短期票据，办公平台登录成功场景不返回")
    private String authTicket;

    /**
     * 一次性票据剩余有效期（秒）。
     *
     * <p>{@code authTicket} 的剩余有效时间，办公平台登录成功场景不返回。</p>
     */
    @Schema(description = "一次性票据剩余有效期（秒）")
    private Long authTicketExpiresIn;

    /**
     * 用户信息（可选）。
     *
     * <p>包含昵称、头像、性别等基础资料。</p>
     */
    @Schema(description = "用户信息（可选）")
    private UserInfo userInfo;

    /**
     * 用户信息内部类
     *
     * <p>封装用户的基本信息，包含昵称、头像、性别、地区等字段，
     * 在登录响应中随 Token 一起返回给客户端。
     *
     * @author Apex Team
     * @since 1.3.0
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        /**
         * 用户昵称。
         */
        @Schema(description = "用户昵称")
        private String nickName;

        /**
         * 用户头像 URL。
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
         */
        @Schema(description = "用户手机号（可选）")
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
         */
        @Schema(description = "国家")
        private String country;

        /**
         * 省份。
         */
        @Schema(description = "省份")
        private String province;

        /**
         * 城市。
         */
        @Schema(description = "城市")
        private String city;
    }
}
