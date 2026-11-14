package com.apex.scaffold.infrastructure.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信开放平台移动应用配置属性。
 *
 * <p>绑定 {@code wechat.app} 配置前缀，提供 AppId、AppSecret 等 OAuth 2.0
 * 授权与 API 调用所需的凭据参数。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wechat.app")
public class WeChatAppProperties {

    /** 微信开放平台应用 ID */
    private String appId;

    /** 微信开放平台应用密钥 */
    private String appSecret;
}
