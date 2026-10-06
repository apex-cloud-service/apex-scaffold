package com.apex.scaffold.infrastructure.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信公众号配置属性。
 *
 * <p>绑定 {@code wechat.mp} 配置前缀，提供公众号 AppID、AppSecret 等网页授权
 * （OAuth 2.0）所需的凭据参数，与 {@link WeChatAppProperties}（微信开放平台
 * 移动应用）、{@code WeChatMiniAppProperties}（小程序）互相独立、互不影响。
 *
 * <h2>多端渠道约定</h2>
 * <p>AppID 缺省（未配置或空串）表示该渠道未开通：公众号网页授权登录入口返回
 * {@code WECHAT_CHANNEL_NOT_CONFIGURED} 明确错误码，不影响其他微信渠道。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wechat.mp")
public class WeChatMpProperties {

    /** 构造微信公众号配置属性 */
    public WeChatMpProperties() {}

    /** 微信公众号 AppID */
    private String appId;

    /** 微信公众号 AppSecret */
    private String appSecret;
}
