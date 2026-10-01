package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import com.apex.scaffold.infrastructure.office.spi.OfficeOAuthClient;
import com.apex.scaffold.infrastructure.office.spi.OfficePlatformClient;
import java.util.Map;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

abstract class AbstractOfficeClient implements OfficeOAuthClient, OfficePlatformClient {
    /** 接口返回成功的错误码。 */
    private static final String SUCCESS_CODE = "0";

    private final java.time.Duration timeout;
    protected final WebClient webClient;
    protected final OfficePlatformProperties.Platform properties;

    protected AbstractOfficeClient(
            WebClient webClient,
            OfficePlatformProperties properties,
            com.apex.scaffold.domain.office.OfficePlatform platform) {
        this.timeout = properties.getTimeout();
        this.webClient = webClient;
        this.properties = properties.getPlatforms().getOrDefault(platform, new OfficePlatformProperties.Platform());
    }

    @Override
    public Mono<String> authorizationUrl(com.apex.scaffold.domain.office.OfficePlatformApp app, String state) {
        return Mono.fromSupplier(() -> {
            String endpoint =
                    switch (platform()) {
                        case FEISHU -> "https://accounts.feishu.cn/open-apis/authen/v1/authorize";
                        case DINGTALK -> "https://login.dingtalk.com/oauth2/auth";
                        case WECOM -> "https://open.weixin.qq.com/connect/oauth2/authorize";
                    };
            String idKey = platform() == com.apex.scaffold.domain.office.OfficePlatform.WECOM ? "appid" : "client_id";
            org.springframework.web.util.UriComponentsBuilder builder =
                    org.springframework.web.util.UriComponentsBuilder.fromUriString(endpoint)
                            .queryParam(idKey, app.getAppId())
                            .queryParam("redirect_uri", app.getRedirectUri())
                            .queryParam("response_type", "code")
                            .queryParam("state", state);
            if (platform() == com.apex.scaffold.domain.office.OfficePlatform.WECOM) {
                builder.queryParam("scope", "snsapi_base").fragment("wechat_redirect");
            } else if (platform() == com.apex.scaffold.domain.office.OfficePlatform.DINGTALK) {
                builder.queryParam("scope", "openid");
            }
            return builder.build().encode().toUriString();
        });
    }

    @Override
    public Mono<String> authenticate(com.apex.scaffold.domain.office.OfficePlatformApp app, String code) {
        return Mono.defer(() -> {
                    String secret =
                            required(System.getenv(required(app.getSecretReference(), "secret reference")), "secret");
                    return switch (platform()) {
                        case FEISHU -> jsonPost(
                                        "/open-apis/authen/v2/oauth/token",
                                        Map.of(
                                                "grant_type",
                                                "authorization_code",
                                                "client_id",
                                                app.getAppId(),
                                                "client_secret",
                                                secret,
                                                "code",
                                                code,
                                                "redirect_uri",
                                                app.getRedirectUri()))
                                .map(this::token)
                                .flatMap(token -> webClient
                                        .get()
                                        .uri("/open-apis/authen/v1/user_info")
                                        .headers(headers -> headers.setBearerAuth(token))
                                        .retrieve()
                                        .bodyToMono(Map.class))
                                .map(body -> identity(body, "open_id", true));
                        case DINGTALK -> jsonPost(
                                        "/v1.0/oauth2/userAccessToken",
                                        Map.of(
                                                "clientId",
                                                app.getAppId(),
                                                "clientSecret",
                                                secret,
                                                "code",
                                                code,
                                                "grantType",
                                                "authorization_code"))
                                .map(this::token)
                                .flatMap(token -> webClient
                                        .get()
                                        .uri("/v1.0/contact/users/me")
                                        .header("x-acs-dingtalk-access-token", token)
                                        .retrieve()
                                        .bodyToMono(Map.class))
                                .map(body -> identity(body, "unionId", false));
                        case WECOM -> webClient
                                .get()
                                .uri(builder -> builder.path("/cgi-bin/gettoken")
                                        .queryParam("corpid", app.getAppId())
                                        .queryParam("corpsecret", secret)
                                        .build())
                                .retrieve()
                                .bodyToMono(Map.class)
                                .map(this::token)
                                .flatMap(token -> webClient
                                        .get()
                                        .uri(builder -> builder.path("/cgi-bin/auth/getuserinfo")
                                                .queryParam("access_token", token)
                                                .queryParam("code", code)
                                                .build())
                                        .retrieve()
                                        .bodyToMono(Map.class))
                                .map(body -> identity(body, "UserId", false));
                    };
                })
                .timeout(timeout)
                .onErrorMap(error -> new com.apex.scaffold.common.exception.BusinessException(
                        com.apex.scaffold.common.result.ResultCode.LOGIN_FAILED));
    }

    private Mono<Map> jsonPost(String uri, Map<String, String> body) {
        return webClient.post().uri(uri).bodyValue(body).retrieve().bodyToMono(Map.class);
    }

    private String identity(Map<?, ?> body, String key, boolean nested) {
        Object error = body.containsKey("errcode") ? body.get("errcode") : body.get("code");
        if (error != null && !SUCCESS_CODE.equals(error.toString())) {
            throw new com.apex.scaffold.common.exception.BusinessException(
                    com.apex.scaffold.common.result.ResultCode.LOGIN_FAILED);
        }
        Map<?, ?> data = nested && body.get("data") instanceof Map<?, ?> value ? value : body;
        Object id = data.get(key);
        return required(id instanceof String value ? value : null, "identity");
    }

    protected Mono<String> postToken(String uri, Map<String, String> params) {
        return webClient
                .get()
                .uri(builder -> {
                    org.springframework.web.util.UriBuilder result = builder.path(uri);
                    params.forEach(result::queryParam);
                    return result.build();
                })
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::token);
    }

    protected Mono<String> getUser(String uri, String accessToken, String tokenParameter) {
        return webClient
                .get()
                .uri(builder -> builder.path(uri)
                        .queryParam(tokenParameter, accessToken)
                        .build())
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::user);
    }

    protected String token(Map<?, ?> body) {
        Object value = body.get("access_token");
        if (value == null) {
            value = body.get("accessToken");
        }
        if (value == null) {
            throw new IllegalStateException("办公平台未返回 access token");
        }
        return value.toString();
    }

    protected String user(Map<?, ?> body) {
        Object value = body.get("userid");
        if (value == null) {
            value = body.get("user_id");
        }
        if (value == null) {
            value = body.get("open_id");
        }
        if (value == null) {
            value = body.get("UserId");
        }
        if (value == null) {
            throw new IllegalStateException("办公平台未返回用户标识");
        }
        return value.toString();
    }

    protected String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + "未配置");
        }
        return value;
    }
}
