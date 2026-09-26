package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 飞书办公平台 OAuth 客户端。
 *
 * <p>继承 {@code AbstractOfficeClient}，实现 {@code OfficeOAuthClient} 与 {@code OfficePlatformClient}
 * SPI 端口，基于 {@code https://open.feishu.cn} 拼装飞书 OAuth 授权跳转地址、
 * 通过 {@code /open-apis/auth/v3/oidc/access_token} 以授权码换取用户访问令牌，
 * 并调用 {@code /open-apis/authen/v1/user_info} 解析当前用户标识。
 *
 * <p>应用凭证、回跳地址等配置来自 {@code OfficePlatformProperties} 的飞书平台段，
 * 缺失时抛出异常快速失败；统一超时控制与登录失败异常转换由父类完成。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Component
public class FeishuOfficeClient extends AbstractOfficeClient {
    public FeishuOfficeClient(WebClient.Builder builder, OfficePlatformProperties properties) {
        super(builder.baseUrl("https://open.feishu.cn").build(), properties, OfficePlatform.FEISHU);
    }

    @Override
    public OfficePlatform platform() {
        return OfficePlatform.FEISHU;
    }

    @Override
    public Mono<String> authorizationUrl(String state) {
        return Mono.fromSupplier(() -> "https://open.feishu.cn/open-apis/authen/v1/authorize?app_id="
                + required(properties.getAppId(), "飞书 appId") + "&redirect_uri="
                + required(properties.getRedirectUri(), "飞书 redirectUri") + "&state=" + required(state, "state"));
    }

    @Override
    public Mono<String> exchange(String code) {
        return postToken(
                "/open-apis/auth/v3/oidc/access_token",
                java.util.Map.of(
                        "app_id",
                        required(properties.getAppId(), "飞书 appId"),
                        "app_secret",
                        required(properties.getSecretReference(), "飞书 secret"),
                        "code",
                        code));
    }

    @Override
    public Mono<String> resolveUser(String accessToken) {
        return getUser("/open-apis/authen/v1/user_info", accessToken, "Authorization");
    }
}
