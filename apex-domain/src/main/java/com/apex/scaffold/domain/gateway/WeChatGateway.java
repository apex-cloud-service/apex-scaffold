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
