package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 钉钉办公平台 OAuth 客户端。
 *
 * <p>继承 {@code AbstractOfficeClient}，实现 {@code OfficeOAuthClient} 与 {@code OfficePlatformClient}
 * SPI 端口，基于 {@code https://api.dingtalk.com} 拼装钉钉 OAuth 授权跳转地址、
 * 通过 {@code /v1.0/oauth2/userAccessToken} 以授权码换取用户访问令牌，
 * 并调用 {@code /v1.0/contact/users/me} 解析当前用户标识。
 *
 * <p>应用凭证、回跳地址等配置来自 {@code OfficePlatformProperties} 的钉钉平台段，
 * 缺失时抛出异常快速失败；统一超时控制与登录失败异常转换由父类完成。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Component
public class DingTalkOfficeClient extends AbstractOfficeClient {
    /** 构造钉钉 OAuth 客户端并指向钉钉 API 域名。 */
    public DingTalkOfficeClient(WebClient.Builder builder, OfficePlatformProperties properties) {
        super(builder.baseUrl("https://api.dingtalk.com").build(), properties, OfficePlatform.DINGTALK);
    }

    @Override
    public OfficePlatform platform() {
        return OfficePlatform.DINGTALK;
    }

    @Override
    public Mono<String> authorizationUrl(String state) {
        return Mono.fromSupplier(() -> "https://login.dingtalk.com/oauth2/auth?client_id="
                + required(properties.getAppId(), "钉钉 appId") + "&redirect_uri="
                + required(properties.getRedirectUri(), "钉钉 redirectUri") + "&state=" + required(state, "state"));
    }

    @Override
    public Mono<String> exchange(String code) {
        return postToken(
                "/v1.0/oauth2/userAccessToken",
                java.util.Map.of(
                        "clientId",
                        required(properties.getAppId(), "钉钉 appId"),
                        "clientSecret",
                        required(properties.getSecretReference(), "钉钉 secret"),
                        "code",
                        code));
    }

    @Override
    public Mono<String> resolveUser(String accessToken) {
        return getUser("/v1.0/contact/users/me", accessToken, "access_token");
    }
}
