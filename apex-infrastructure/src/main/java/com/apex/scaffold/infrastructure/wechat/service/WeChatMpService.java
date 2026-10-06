package com.apex.scaffold.infrastructure.wechat.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.infrastructure.wechat.config.WeChatMpProperties;
import com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 微信公众号服务（{@code wechat.mp} 渠道）。
 *
 * <p>封装微信公众号网页授权（OAuth 2.0）登录所需的轻量 API：
 * <ul>
 *   <li>{@code /sns/oauth2/access_token} — 网页授权 code 换取 OpenID / UnionID</li>
 *   <li>授权跳转 URL 拼装（{@code open.weixin.qq.com/connect/oauth2/authorize}）</li>
 *   <li>OAuth state 一次性防 CSRF 凭证（Redis 签发/原子校验消费）</li>
 * </ul>
 *
 * <h2>渠道隔离</h2>
 * <p>与 {@link WeChatAppService}（开放平台移动应用，{@code wechat.app}）、
 * {@link WeChatMiniProgramService}（小程序，{@code wechat.miniapp}）互相独立：
 * 使用 {@code wechat.mp} 的 AppID/AppSecret，凭据缺失时抛出
 * {@code BusinessException(WECHAT_CHANNEL_NOT_CONFIGURED)}，不影响其他微信渠道。
 *
 * <h2>错误处理</h2>
 * <p>统一通过 {@link WeChatApiResponse} 检查微信 {@code errcode}，非 0 抛出
 * {@link WeChatApiException}；AppSecret 严禁写入代码仓库，须经环境变量注入。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 * @see WeChatApiResponse
 * @see WeChatApiException
 * @see WeChatMpProperties
 */
@Slf4j
@Service
@SuppressWarnings({"AlibabaJavaNaming", "PMD.ClassNamingShouldBeCamelRule"})
public class WeChatMpService {

    /** 微信网页授权跳转 URL 前缀 */
    private static final String OAUTH_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";

    /** 网页授权 code 换取 access_token 接口路径 */
    private static final String OAUTH_ACCESS_TOKEN_PATH = "/sns/oauth2/access_token";

    /** OAuth state 的 Redis 键前缀 */
    private static final String OAUTH_STATE_KEY_PREFIX = "wechat:mp:oauth:state:";

    /** OAuth state 有效期（分钟）：覆盖授权跳转往返耗时，一次性消费 */
    private static final long OAUTH_STATE_TTL_MINUTES = 5;

    /** 静默授权 scope：仅获取 openid，不弹授权页 */
    public static final String SCOPE_SNSAPI_BASE = "snsapi_base";

    /** 微信公众号配置属性（wechat.mp） */
    private final WeChatMpProperties properties;

    /** 微信 API WebClient（baseUrl https://api.weixin.qq.com，共享连接池） */
    private final WebClient webClient;

    /** Redisson 客户端（OAuth state 一次性凭证存储） */
    private final RedissonClient redissonClient;

    /**
     * 构造微信公众号服务。
     *
     * @param properties     微信公众号配置属性（wechat.mp）
     * @param webClient      微信 API WebClient（{@code weChatApiWebClient} Bean）
     * @param redissonClient Redisson 客户端
     */
    public WeChatMpService(
            WeChatMpProperties properties,
            @Qualifier("weChatApiWebClient") WebClient webClient,
            RedissonClient redissonClient) {
        this.properties = properties;
        this.webClient = webClient;
        this.redissonClient = redissonClient;
    }

    /**
     * 生成网页授权跳转 URL。
     *
     * <p>格式：{@code https://open.weixin.qq.com/connect/oauth2/authorize?appid=...&redirect_uri=...&response_type=code&scope=...&state=...#wechat_redirect}。
     * redirect_uri 的域名必须在公众平台配置的网页授权域名下。
     *
     * @param redirectUri 授权后回跳的 H5 页面地址（urlencode 后拼接）
     * @param scope       授权作用域（{@code snsapi_base}/{@code snsapi_userinfo}），空时按 snsapi_base
     * @param state       防 CSRF 随机串，可为空
     * @return 完整授权跳转 URL
     */
    public String buildAuthorizeUrl(String redirectUri, String scope, String state) {
        String encodedRedirectUri;
        try {
            encodedRedirectUri = URLEncoder.encode(redirectUri, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("redirectUri URL编码失败, redirectUri: {}", redirectUri, e);
            throw new IllegalArgumentException("redirectUri编码失败: " + redirectUri, e);
        }
        StringBuilder urlBuilder = new StringBuilder(OAUTH_AUTHORIZE_URL);
        urlBuilder.append("?appid=").append(properties.getAppId());
        urlBuilder.append("&redirect_uri=").append(encodedRedirectUri);
        urlBuilder.append("&response_type=code");
        urlBuilder.append("&scope=").append(scope != null && !scope.isBlank() ? scope : SCOPE_SNSAPI_BASE);
        if (state != null && !state.isBlank()) {
            urlBuilder.append("&state=").append(state);
        }
        urlBuilder.append("#wechat_redirect");
        return urlBuilder.toString();
    }

    /**
     * 网页授权 code 换取会话信息。
     *
     * <p>调用 {@code /sns/oauth2/access_token}（使用 {@code wechat.mp} 凭据），
     * 返回 OpenID、UnionID 与网页授权 token 信息。渠道未配置时抛出
     * {@code WECHAT_CHANNEL_NOT_CONFIGURED}；微信返回错误码时抛出
     * {@link WeChatApiException}。
     *
     * @param code 网页授权回调携带的 code（一次性有效）
     * @return openid / unionid / access_token / refresh_token / expires_in
     */
    public Mono<Map<String, Object>> getAccessTokenByCode(String code) {
        if (!isChannelConfigured()) {
            return Mono.error(new BusinessException(ResultCode.WECHAT_CHANNEL_NOT_CONFIGURED, "微信公众号渠道未配置（wechat.mp）"));
        }
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(OAUTH_ACCESS_TOKEN_PATH)
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .queryParam("code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("公众号网页授权"))
                .doOnSuccess(result -> log.info("公众号网页授权成功, openid: {}", result.get("openid")))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("公众号网页授权请求失败", e);
                    }
                });
    }

    /**
     * 签发 OAuth state 一次性防 CSRF 凭证。
     *
     * <p>生成随机 state 写入 Redis（TTL 5 分钟），授权回跳后由
     * {@link #validateAuthState(String)} 原子消费。渠道未配置时抛出
     * {@code WECHAT_CHANNEL_NOT_CONFIGURED}。
     *
     * @return state 随机串
     */
    public Mono<String> issueAuthState() {
        if (!isChannelConfigured()) {
            return Mono.error(new BusinessException(ResultCode.WECHAT_CHANNEL_NOT_CONFIGURED, "微信公众号渠道未配置（wechat.mp）"));
        }
        return Mono.fromCallable(() -> UUID.randomUUID().toString())
                .flatMap(state -> {
                    RBucket<String> bucket = redissonClient.getBucket(oauthStateKey(state), StringCodec.INSTANCE);
                    return Mono.fromCompletionStage(bucket.setAsync("1", Duration.ofMinutes(OAUTH_STATE_TTL_MINUTES)))
                            .thenReturn(state);
                })
                .doOnSuccess(state -> log.debug("签发公众号 OAuth state（防 CSRF）"));
    }

    /**
     * 原子校验并消费 OAuth state。
     *
     * <p>使用 Redis {@code GETDEL} 语义（Redisson {@code getAndDeleteAsync}）：
     * state 存在即有效并立即删除（一次性），不存在/已过期/已被消费返回 false。
     *
     * @param state 授权回跳携带的 state
     * @return true 表示校验通过
     */
    public Mono<Boolean> validateAuthState(String state) {
        if (state == null || state.isBlank()) {
            return Mono.just(false);
        }
        RBucket<String> bucket = redissonClient.getBucket(oauthStateKey(state), StringCodec.INSTANCE);
        return Mono.fromCompletionStage(bucket.getAndDeleteAsync())
                .map(storedValue -> storedValue != null)
                .defaultIfEmpty(false);
    }

    /**
     * 判断公众号渠道是否已配置（AppID 与 AppSecret 均非空）。
     *
     * @return true 表示渠道可用
     */
    private boolean isChannelConfigured() {
        String appId = properties.getAppId();
        String appSecret = properties.getAppSecret();
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }

    /**
     * 构建 OAuth state 的 Redis Key。
     */
    private String oauthStateKey(String state) {
        return OAUTH_STATE_KEY_PREFIX + state;
    }
}
