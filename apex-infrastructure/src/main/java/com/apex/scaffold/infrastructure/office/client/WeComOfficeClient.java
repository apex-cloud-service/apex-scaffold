package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class WeComOfficeClient extends AbstractOfficeClient {
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
