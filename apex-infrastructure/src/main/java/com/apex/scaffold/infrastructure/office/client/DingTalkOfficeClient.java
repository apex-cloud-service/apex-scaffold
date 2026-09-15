package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class DingTalkOfficeClient extends AbstractOfficeClient {
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
