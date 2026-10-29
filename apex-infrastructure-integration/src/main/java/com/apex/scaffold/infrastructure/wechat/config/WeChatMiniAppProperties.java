package com.apex.scaffold.infrastructure.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信小程序配置属性。
 *
 * <p>绑定 {@code wechat.miniapp} 配置前缀，提供小程序 AppID、AppSecret 等
 * jscode2session 与小程序 API 调用所需的凭据参数，与 {@link WeChatAppProperties}
 * （微信开放平台移动应用）互相独立、互不影响。
 *
 * <h2>多端渠道约定</h2>
 * <p>AppID 缺省（未配置或空串）表示该渠道未开通：小程序登录入口返回
 * {@code WECHAT_CHANNEL_NOT_CONFIGURED} 明确错误码，不影响其他微信渠道。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wechat.miniapp")
public class WeChatMiniAppProperties {

    /** 微信小程序 AppID */
    private String appId;

    /** 微信小程序 AppSecret */
    private String appSecret;
}
