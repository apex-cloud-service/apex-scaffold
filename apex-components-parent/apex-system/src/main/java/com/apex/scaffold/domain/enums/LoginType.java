package com.apex.scaffold.domain.enums;

/**
 * 登录类型枚举
 *
 * <p>定义系统支持的微信登录方式，用于策略模式动态选择登录实现。
 * 每种登录类型对应一种具体的应用层登录策略实现。
 *
 * <ul>
 *   <li>{@link #WX_CODE} - 微信验证码登录：通过微信授权码(code)获取openId进行登录</li>
 *   <li>{@link #WX_PHONE} - 微信一键登录：通过手机号授权凭证(phoneCode)获取手机号进行登录</li>
 *   <li>{@link #WX_SMS} - 微信短信验证码登录：通过短信验证码进行登录</li>
 *   <li>{@link #CL_PHONE} - 创蓝一键登录：通过创蓝闪验SDK token置换手机号进行登录（默认）</li>
 *   <li>{@link #CL_SMS} - 创蓝短信验证码登录：通过创蓝253短信平台发送验证码进行登录</li>
 *   <li>{@link #PASSWORD} - 账号密码登录：传统用户名+密码（BCrypt），面向管理后台场景</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public enum LoginType {

    /**
     * 微信验证码登录
     *
     * <p>使用微信 OAuth2 授权码换取 access_token 和 openId，
     * 通过 openId 查询或创建用户进行登录。
     * 适用于标准微信授权登录场景。
     */
    WX_CODE,

    /**
     * 微信一键登录
     *
     * <p>通过微信手机号授权凭证获取用户手机号，
     * 根据手机号查询或创建用户进行登录。
     * 适用于需要快速登录的场景，减少用户操作步骤。
     */
    WX_PHONE,

    /**
     * 短信验证码登录
     *
     * <p>用户输入手机号后，系统发送短信验证码到该手机，
     * 用户输入验证码完成登录。
     * 适用于用户在一键登录时选择使用其他手机号的场景。
     */
    WX_SMS,

    /**
     * 创蓝一键登录
     *
     * <p>通过创蓝闪验SDK实现的一键登录功能。
     * App端集成创蓝闪验SDK，用户授权后获取临时token，
     * 服务端通过token置换用户真实手机号完成登录。
     * 适用于App默认的免密登录场景，用户体验最佳。
     *
     * @see com.apex.scaffold.domain.gateway.ChuanglanGateway
     */
    CL_PHONE,

    /**
     * 创蓝短信验证码登录
     *
     * <p>通过创蓝253短信平台发送验证码实现的登录功能。
     * 当创蓝一键登录失败或用户选择使用其他手机号时，
     * 用户输入手机号获取短信验证码完成登录。
     * 作为一键登录的降级方案，确保用户始终能够完成登录。
     *
     * @see com.apex.scaffold.domain.service.SmsCodeService
     */
    CL_SMS,

    /**
     * 账号密码登录
     *
     * <p>传统用户名+密码登录（BCrypt 密码校验），面向管理后台与
     * 需要账号体系的 B 端场景。由 {@code PasswordLoginStrategy}
     * 实现，依赖 {@code sys_user.username / password} 字段
     * （V3 迁移引入）。
     *
     * @since 2.2.0
     */
    PASSWORD,

    /**
     * 微信小程序登录
     *
     * <p>服务端以 {@code wx.login()} 的临时凭证（code）调用微信
     * {@code /sns/jscode2session} 接口换取 OpenID / UnionID / session_key，
     * 按社交身份绑定表（{@code sys_user_social}，渠道=WECHAT_MINI）归一账户；
     * 可选携带手机号授权凭证（phoneCode）换取手机号完成手机号归一。
     * 由 {@code WeChatMiniProgramLoginStrategy} 实现，依赖
     * {@code wechat.miniapp} 配置（V59+ 多端改造引入）。
     *
     * @since 3.3.0
     */
    WX_MINI,

    /**
     * 微信公众号网页授权登录
     *
     * <p>公众号 H5 内通过网页授权（snsapi_base / snsapi_userinfo）获取授权 code，
     * 服务端以 {@code wechat.mp} 渠道凭据调用 {@code /sns/oauth2/access_token}
     * 换取 OpenID / UnionID，按社交身份绑定表（渠道=WECHAT_MP）归一账户；
     * 未绑定手机号的用户签发 {@code WECHAT_BIND} 票据引导短信验证绑定。
     * 由 {@code WeChatMpOAuthLoginStrategy} 实现，依赖
     * {@code wechat.mp} 配置（V59+ 多端改造引入）。
     *
     * @since 3.3.0
     */
    WX_OAUTH
}
