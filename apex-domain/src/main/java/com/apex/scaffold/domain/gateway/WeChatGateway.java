package com.apex.scaffold.domain.gateway;

import lombok.Data;
import reactor.core.publisher.Mono;

/**
 * 微信网关接口（可选集成 - 第三方登录示例）
 *
 * <p>
 * <strong>模块说明</strong>：本接口作为第三方OAuth登录集成的示例实现（微信登录）。
 * </p>
 *
 * <p>
 * <strong>使用场景</strong>：
 * - 移动应用的微信一键登录
 * - 小程序登录集成
 * - PC网站的扫码登录
 * </p>
 *
 * <p>
 * <strong>可选性说明</strong>：
 * - 本模块为可选集成，不是核心脚手架功能
 * - 可作为其他OAuth登录（Google、GitHub、企业微信等）的参考实现
 * - 如不需要微信登录，可以删除相关代码
 * - 如需其他OAuth登录，可参照本模块实现
 * </p>
 *
 * <p>
 * 定义与微信开放平台交互的接口方法，包括登录认证和用户信息获取
 * </p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface WeChatGateway {

    /**
     * 微信登录认证
     *
     * <p>使用微信授权code换取access_token和用户信息
     *
     * @param code 微信授权code
     * @return 微信登录结果
     */
    Mono<WeChatLoginResult> login(String code);

    /**
     * 获取微信用户信息
     *
     * <p>使用access_token和openid获取用户详细信息
     *
     * @param accessToken 微信access_token
     * @param openId 用户openId
     * @return 微信用户信息
     */
    Mono<WeChatUserInfo> getUserInfo(String accessToken, String openId);

    /**
     * 获取微信手机号
     *
     * <p>使用手机号授权凭证获取用户手机号
     *
     * @param phoneCode 微信手机号授权凭证
     * @return 微信手机号结果
     */
    Mono<WeChatPhoneResult> getPhoneNumber(String phoneCode);

    /**
     * 微信小程序 code 换取会话（jscode2session）
     *
     * <p>使用小程序 {@code wx.login()} 返回的临时登录凭证换取
     * OpenID、UnionID 与会话密钥。依赖 {@code wechat.miniapp} 配置，
     * 未配置渠道时抛出 {@code BusinessException(WECHAT_CHANNEL_NOT_CONFIGURED)}。
     *
     * @param jsCode 小程序临时登录凭证（wx.login 返回的 code）
     * @return 小程序会话信息（openid 必有；unionid 满足条件时返回）
     * @since 3.3.0
     */
    Mono<MiniProgramSession> miniProgramCodeToSession(String jsCode);

    /**
     * 获取小程序用户手机号
     *
     * <p>使用小程序手机号授权凭证（getPhoneNumber 返回的 code）换取用户手机号，
     * 凭证通过小程序 appid 的全局 AccessToken 调用
     * {@code /wxa/business/getuserphonenumber} 接口置换。
     *
     * @param phoneCode 小程序手机号授权凭证
     * @return 微信手机号结果
     * @since 3.3.0
     */
    Mono<WeChatPhoneResult> miniProgramPhoneNumber(String phoneCode);

    /**
     * 微信登录结果
     *
     * <p>封装微信登录认证返回的会话信息
     *
     * @author Apex Team
     */
    @Data
    class WeChatLoginResult {
        private String openid;
        private String unionid;
        private String accessToken;
        private String refreshToken;
        private Long expiresIn;
    }

    /**
     * 微信小程序会话信息
     *
     * <p>封装 {@code /sns/jscode2session} 接口返回的会话信息。
     *
     * @author Apex Team
     * @since 3.3.0
     */
    @Data
    class MiniProgramSession {
        /** 用户在当前小程序下的 OpenID */
        private String openid;
        /** 用户在微信开放平台主体下的 UnionID（需绑定开放平台账号，可能为空） */
        private String unionid;
        /** 会话密钥（用于解密敏感数据，本服务暂不落库） */
        private String sessionKey;
    }

    /**
     * 微信用户信息
     *
     * <p>封装微信用户详细信息
     *
     * @author Apex Team
     */
    @Data
    class WeChatUserInfo {
        private String openid;
        private String nickname;
        private String avatarUrl;
        private Integer gender;
        private String country;
        private String province;
        private String city;
        /** 语言（如 zh_CN / en） */
        private String language;
    }

    /**
     * 微信手机号结果
     *
     * <p>封装微信手机号授权返回的手机号信息
     *
     * @author Apex Team
     */
    @Data
    class WeChatPhoneResult {
        private String phoneNumber;
        private String purePhoneNumber;
        private String countryCode;
    }
}
