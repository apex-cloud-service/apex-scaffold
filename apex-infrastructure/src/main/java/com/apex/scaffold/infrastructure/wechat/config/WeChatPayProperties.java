package com.apex.scaffold.infrastructure.wechat.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 微信支付V3配置属性
 *
 * <p>用于配置微信支付商户相关参数，包括商户号、API密钥、证书路径等。
 * 配置前缀为 {@code wechat.pay}，对应 YAML 文件中的 {@code wechat.pay} 节点。
 *
 * <h3>配置示例</h3>
 * <pre>{@code * wechat:
 *   pay:
 *     app-id: wx1234567890abcdef
 *     mch-id: 1234567890
 *     api-v3-key: your_api_v3_key
 *     private-key-path: /path/to/apiclient_key.pem
 *     merchant-serial-number: ABC123456789
 *     notify-url: https://your-domain.com/api/pay/callback
 * }</pre>
 *
 * <h3>依赖说明</h3>
 * <ul>
 *   <li>{@code mchId} - 微信支付商户号，在微信商户平台获取</li>
 *   <li>{@code apiV3Key} - API v3密钥，在微信商户平台设置</li>
 *   <li>{@code privateKeyPath} - 商户API私钥证书文件路径（PEM格式）</li>
 *   <li>{@code merchantSerialNumber} - 商户API证书序列号</li>
 *   <li>{@code notifyUrl} - 支付结果回调通知URL，需为公网可访问的HTTPS地址</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "wechat.pay")
public class WeChatPayProperties {

    /**
     * 构造微信支付配置属性
     */
    public WeChatPayProperties() {}

    /**
     * 微信开放平台移动应用 AppID。
     *
     * <p>APP 支付统一下单和客户端调起签名必须使用同一个 AppID。</p>
     *
     */
    private String appId;

    /**
     * 微信支付商户号
     *
     * <p>在微信商户平台注册后获得，格式为10位数字字符串
     */
    private String mchId;

    /**
     * API v3密钥
     *
     * <p>在微信商户平台 API安全 中设置的32位密钥，
     * 用于回调通知中的签名验证和敏感信息解密
     */
    private String apiV3Key;

    /**
     * 商户API私钥证书路径
     *
     * <p>商户API证书对应的私钥文件路径，PEM格式。
     * 支持 classpath: 前缀，如 {@code classpath:cert/apiclient_key.pem}
     */
    private String privateKeyPath;

    /**
     * 商户API证书序列号
     *
     * <p>在微信商户平台下载的商户API证书的序列号，用于API请求签名
     */
    private String merchantSerialNumber;

    /**
     * 支付结果回调通知URL
     *
     * <p>接收微信支付结果通知的URL，必须是公网可访问的HTTPS地址。
     * 如果未配置，每次下单时可以动态传入
     */
    private String notifyUrl;

    /**
     * 微信支付平台公钥证书路径
     *
     * <p>TODO P0-WX-1: 配置此路径后，verifyCallbackSignature将使用正确的initVerify进行签名验证。
     * 平台公钥证书可从微信商户平台下载（通常为 pem 格式）。
     * 支持 classpath: 前缀，如 {@code classpath:cert/wechatpay_public_key.pem}
     *
     */
    private String platformPublicKeyPath;
}
