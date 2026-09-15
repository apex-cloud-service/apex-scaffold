package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

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
