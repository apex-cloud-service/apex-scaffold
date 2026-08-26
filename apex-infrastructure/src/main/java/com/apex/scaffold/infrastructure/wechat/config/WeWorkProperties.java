package com.apex.scaffold.infrastructure.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 企业微信配置属性
 *
 * <p>用于配置企业微信（WeChat Work / WeCom）相关的参数，
 * 包括企业ID、应用AgentId、应用Secret、Token和EncodingAESKey等。
 *
 * <h3>配置示例</h3>
 * <pre>{@code * wework:
 *   corp-id: ww1234567890abcdef
 *   agent-id: 1000001
 *   secret: your_app_secret
 *   token: your_token
 *   encoding-aes-key: your_encoding_aes_key
 * }</pre>
 *
 * <h3>与微信公众平台的区别</h3>
 * <ul>
 *   <li><b>企业微信</b>：面向企业内部沟通和办公，支持自建应用</li>
 *   <li><b>微信公众号/开放平台</b>：面向C端用户，支持消息推送和支付</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wework")
public class WeWorkProperties {

    /**
     * 构造企业微信配置属性
     */
    public WeWorkProperties() {}

    /**
     * 企业微信企业ID（CorpID）
     *
     * <p>在企业微信管理后台 - 我的企业 - 企业信息中查看
     */
    private String corpId;

    /**
     * 企业微信应用AgentId
     *
     * <p>在企业微信管理后台 - 应用管理 - 自建应用中查看
     */
    private String agentId;

    /**
     * 企业微信应用Secret
     *
     * <p>在企业微信管理后台 - 应用管理 - 自建应用 - 查看Secret中获取
     */
    private String secret;

    /**
     * 企业微信回调Token
     *
     * <p>用于验证回调消息的合法性，在企业微信管理后台设置
     */
    private String token;

    /**
     * 企业微信回调消息加密密钥
     *
     * <p>用于加密/解密回调消息体，在企业微信管理后台设置
     */
    private String encodingAesKey;
}
