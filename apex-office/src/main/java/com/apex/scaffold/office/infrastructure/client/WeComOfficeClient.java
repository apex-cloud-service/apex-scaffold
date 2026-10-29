package com.apex.scaffold.office.infrastructure.client;

import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.infrastructure.config.OfficePlatformProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 企业微信办公平台 OAuth 客户端。
 *
 * <p>继承 {@code AbstractOfficeClient}，实现 {@code OfficeOAuthClient} 与 {@code OfficePlatformClient}
 * SPI 端口，基于 {@code https://qyapi.weixin.qq.com} 拼装企业微信 OAuth 授权跳转地址、
 * 通过 {@code /cgi-bin/gettoken} 以 corpid/corpsecret 换取应用访问令牌，
 * 并调用 {@code /cgi-bin/user/getuserinfo} 解析当前用户标识。
 *
 * <p>企业凭证、回跳地址等配置来自 {@code OfficePlatformProperties} 的企业微信平台段，
 * 缺失时抛出异常快速失败；统一超时控制与登录失败异常转换由父类完成。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Component
public class WeComOfficeClient extends AbstractOfficeClient {
    /**
     * 构造企业微信 OAuth 客户端并指向企业微信 API 域名。
     *
     * @param builder 用于创建企业微信 HTTP 客户端的构建器
     * @param properties 平台凭证、回跳地址及请求超时配置
     */
    public WeComOfficeClient(WebClient.Builder builder, OfficePlatformProperties properties) {
        super(builder.baseUrl("https://qyapi.weixin.qq.com").build(), properties, OfficePlatform.WECOM);
    }

    @Override
    public OfficePlatform platform() {
        return OfficePlatform.WECOM;
    }

    @Override
    public Mono<String> authorizationUrl(String state) {
        return Mono.fromSupplier(() -> "https://open.weixin.qq.com/connect/oauth2/authorize?appid="
                + required(properties.getAppId(), "企业微信 appId") + "&redirect_uri="
                + required(properties.getRedirectUri(), "企业微信 redirectUri") + "&state=" + required(state, "state")
                + "#wechat_redirect");
    }

    @Override
    public Mono<String> exchange(String code) {
        return postToken(
                "/cgi-bin/gettoken",
                java.util.Map.of(
                        "corpid",
                        required(properties.getAppId(), "企业微信 appId"),
                        "corpsecret",
                        required(properties.getSecretReference(), "企业微信 secret")));
    }

    @Override
    public Mono<String> resolveUser(String accessToken) {
        return getUser("/cgi-bin/user/getuserinfo", accessToken, "access_token");
    }
}
