package com.apex.scaffold.infrastructure.wechat.service;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.infrastructure.wechat.config.WeChatMiniAppProperties;
import com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 微信小程序服务（{@code wechat.miniapp} 渠道）。
 *
 * <p>封装微信小程序服务端 API：
 * <ul>
 *   <li>{@code /sns/jscode2session} — 临时登录凭证换取 OpenID / UnionID / session_key</li>
 *   <li>{@code /cgi-bin/token} — 小程序全局 AccessToken（供手机号置换等接口使用）</li>
 *   <li>{@code /wxa/business/getuserphonenumber} — 手机号授权凭证换取手机号</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <p>与 {@link WeChatAppService}（微信开放平台移动应用，{@code wechat.app}）互相独立：
 * 使用 {@code wechat.miniapp} 的 AppID/AppSecret，凭据缺失时抛出
 * {@code BusinessException(WECHAT_CHANNEL_NOT_CONFIGURED)}，不影响其他微信渠道。
 *
 * <h2>设计说明</h2>
 * <p>统一通过 {@link WeChatApiResponse} 检查微信 {@code errcode}，非 0 抛出
 * {@link WeChatApiException}；AppSecret 严禁写入代码仓库，须经环境变量注入。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 * @see WeChatApiResponse
 * @see WeChatApiException
 * @see WeChatMiniAppProperties
 */
@Slf4j
@Service
public class WeChatMiniProgramService {

    /** jscode2session 接口路径 */
    private static final String JSCODE2SESSION_PATH = "/sns/jscode2session";

    /** 小程序全局 AccessToken 接口路径 */
    private static final String GLOBAL_TOKEN_PATH = "/cgi-bin/token";

    /** 手机号置换接口路径 */
    private static final String PHONE_NUMBER_PATH = "/wxa/business/getuserphonenumber";

    /** 微信小程序配置属性（wechat.miniapp） */
    private final WeChatMiniAppProperties properties;

    /**
     * 微信 API WebClient（baseUrl https://api.weixin.qq.com，与公众号/开放平台共享连接池）
     */
    private final WebClient webClient;

    /**
     * 构造微信小程序服务。
     *
     * @param properties 微信小程序配置属性（wechat.miniapp）
     * @param webClient  微信 API WebClient（{@code weChatApiWebClient} Bean）
     */
    public WeChatMiniProgramService(
            WeChatMiniAppProperties properties, @Qualifier("weChatApiWebClient") WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
    }

    /**
     * 小程序登录凭证换取会话（jscode2session）。
     *
     * <p>调用 {@code /sns/jscode2session}，返回 OpenID、UnionID 与 session_key。
     * 渠道未配置时抛出 {@code WECHAT_CHANNEL_NOT_CONFIGURED}；微信返回错误码时
     * 抛出 {@link WeChatApiException}。
     *
     * @param jsCode 小程序临时登录凭证（wx.login 返回的 code，一次性有效）
     * @return openid / unionid / sessionKey
     */
    public Mono<Map<String, Object>> codeToSession(String jsCode) {
        if (!isChannelConfigured()) {
            return Mono.error(
                    new BusinessException(ResultCode.WECHAT_CHANNEL_NOT_CONFIGURED, "微信小程序渠道未配置（wechat.miniapp）"));
        }
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(JSCODE2SESSION_PATH)
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .queryParam("js_code", jsCode)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("小程序登录凭证换取会话"))
                .doOnSuccess(result -> log.info("小程序 jscode2session 成功, openid: {}", result.get("openid")))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("小程序 jscode2session 请求失败", e);
                    }
                });
    }

    /**
     * 获取小程序全局 AccessToken。
     *
     * <p>调用 {@code /cgi-bin/token}（grant_type=client_credential），使用
     * {@code wechat.miniapp} 凭据，与开放平台移动应用的全局 Token 相互独立。
     *
     * @return 全局 AccessToken 字符串
     */
    public Mono<String> getGlobalAccessToken() {
        if (!isChannelConfigured()) {
            return Mono.error(
                    new BusinessException(ResultCode.WECHAT_CHANNEL_NOT_CONFIGURED, "微信小程序渠道未配置（wechat.miniapp）"));
        }
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(GLOBAL_TOKEN_PATH)
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("获取小程序全局AccessToken"))
                .map(result -> (String) result.get("access_token"))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("获取小程序全局AccessToken请求失败", e);
                    }
                });
    }

    /**
     * 小程序手机号授权凭证换取手机号。
     *
     * <p>先获取小程序全局 AccessToken，再调用
     * {@code /wxa/business/getuserphonenumber} 置换手机号。
     *
     * @param phoneCode 小程序手机号授权凭证（getPhoneNumber 事件返回的 code）
     * @return 手机号信息（phone / countryCode）
     */
    public Mono<Map<String, Object>> getPhoneNumber(String phoneCode) {
        if (!isChannelConfigured()) {
            return Mono.error(
                    new BusinessException(ResultCode.WECHAT_CHANNEL_NOT_CONFIGURED, "微信小程序渠道未配置（wechat.miniapp）"));
        }
        return getGlobalAccessToken().flatMap(accessToken -> webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path(PHONE_NUMBER_PATH)
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", phoneCode))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("获取小程序手机号"))
                .doOnSuccess(result -> log.info("获取小程序手机号成功"))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("获取小程序手机号请求失败", e);
                    }
                }));
    }

    /**
     * 判断小程序渠道是否已配置（AppID 与 AppSecret 均非空）。
     *
     * @return true 表示渠道可用
     */
    private boolean isChannelConfigured() {
        String appId = properties.getAppId();
        String appSecret = properties.getAppSecret();
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}
