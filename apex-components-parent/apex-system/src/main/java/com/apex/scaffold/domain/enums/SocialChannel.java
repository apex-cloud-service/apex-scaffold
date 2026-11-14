package com.apex.scaffold.domain.enums;

/**
 * 社交渠道枚举
 *
 * <p>定义用户社交身份绑定（{@code sys_user_social}）支持的渠道。
 * 同一用户在不同微信应用（开放平台 App / 小程序 / 公众号）下的 OpenID 不同，
 * 通过本枚举区分渠道后，每个渠道的 OpenID 各占一条绑定记录，
 * 以 {@code (channel, open_id)} 唯一约束保证同一渠道身份只归属一个用户。
 *
 * <ul>
 *   <li>{@link #WECHAT_APP} - 微信开放平台移动应用（App 内微信 SDK 授权登录）</li>
 *   <li>{@link #WECHAT_MINI} - 微信小程序（jscode2session 登录）</li>
 *   <li>{@link #WECHAT_MP} - 微信公众号（网页授权 snsapi 登录）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.domain.model.entity.UserSocialEntity
 */
public enum SocialChannel {

    /**
     * 微信开放平台移动应用：App 内通过微信 SDK 拉起授权，
     * 服务端以授权码调用 /sns/oauth2/access_token 置换 OpenID。
     */
    WECHAT_APP,

    /**
     * 微信小程序：服务端以 wx.login 的临时凭证调用
     * /sns/jscode2session 置换 OpenID。
     */
    WECHAT_MINI,

    /**
     * 微信公众号：H5 内通过网页授权（snsapi_base / snsapi_userinfo）
     * 获取 OpenID。
     */
    WECHAT_MP;

    /**
     * 渠道编码解析。
     *
     * @param code 渠道编码（不区分大小写），允许为 {@code null}
     * @return 对应渠道；编码为空或无法识别时返回 {@code null}
     */
    public static SocialChannel fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
