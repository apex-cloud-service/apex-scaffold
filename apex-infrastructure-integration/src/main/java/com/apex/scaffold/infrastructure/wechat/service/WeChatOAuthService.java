package com.apex.scaffold.infrastructure.wechat.service;

import com.apex.scaffold.infrastructure.wechat.config.WeChatAppProperties;
import com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 微信网页授权服务
 *
 * <p>提供微信网页授权（OAuth 2.0）相关的完整功能，包括授权URL生成、
 * Code换取用户信息、授权Token刷新等。
 *
 * <p><strong>微信网页授权流程</strong></p>
 * <ol>
 *   <li><b>第一步：引导用户进入授权页面</b>
 *     <p>调用 {@link #buildOauthUrl(String, String, String)} 生成授权URL，
 *     用户点击后跳转到微信授权页面同意授权。</li>
 *   <li><b>第二步：通过Code换取网页授权AccessToken</b>
 *     <p>用户在微信中同意授权后，微信会回调redirect_uri并带上code参数，
 *     调用 {@link #getAccessTokenByCode(String)} 换取access_token和openid。</li>
 *   <li><b>第三步：获取用户信息</b>
 *     <p>调用 {@link #getUserInfoByAccessToken(String, String)} 获取用户
 *     的昵称、头像等详细信息。</li>
 *   <li><b>第四步：刷新AccessToken（可选）</b>
 *     <p>当access_token过期时，调用 {@link #refreshAccessToken(String)} 获取新token。</li>
 * </ol>
 *
 * <p><strong>Scope权限说明</strong></p>
 * <ul>
 *   <li>{@code snsapi_base} - 静默授权，只获取openid，不弹出授权页面</li>
 *   <li>{@code snsapi_userinfo} - 显式授权，获取用户详细信息，会弹出授权页面</li>
 * </ul>
 *
 * <p><strong>使用示例</strong></p>
 * <pre>{@code * // 1. 生成授权URL
 * String oauthUrl = oAuthService.buildOAuthUrl(
 *     "https://example.com/oauth/callback",
 *     "snsapi_userinfo",
 *     "custom_state_value"
 * );
 *
 * // 2. 在回调处理中用code换取用户信息
 * oAuthService.getAccessTokenByCode(code)
 *     .flatMap(tokenInfo -> oAuthService.getUserInfoByAccessToken(
 *         (String) tokenInfo.get("access_token"),
 *         (String) tokenInfo.get("openid")
 *     ))
 *     .subscribe(userInfo -> {
 *         // 处理用户信息
 *     });
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see WeChatAppProperties
 * @see WeChatAppService
 */
@Slf4j
@Service
@SuppressWarnings({"AlibabaJavaNaming", "PMD.ClassNamingShouldBeCamelRule"})
public class WeChatOAuthService {

    /**
     * 微信网页授权基础URL
     */
    private static final String OAUTH_AUTHORIZE_URL = "https://open.weixin.qq.com/connect/oauth2/authorize";

    /**
     * 微信网页授权AccessToken接口URL
     */
    private static final String OAUTH_ACCESS_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/access_token";

    /**
     * 微信网页授权刷新Token接口URL
     */
    private static final String OAUTH_REFRESH_TOKEN_URL = "https://api.weixin.qq.com/sns/oauth2/refresh_token";

    /**
     * 微信网页授权用户信息接口URL
     */
    private static final String OAUTH_USERINFO_URL = "https://api.weixin.qq.com/sns/userinfo";

    /**
     * 微信网页授权校验Token接口URL
     */
    private static final String OAUTH_AUTH_URL = "https://api.weixin.qq.com/sns/auth";

    /**
     * 微信API错误码字段名
     */
    private static final String ERR_CODE_KEY = "errcode";

    /**
     * 微信API错误消息字段名
     */
    private static final String ERR_MSG_KEY = "errmsg";

    /**
     * 微信APP配置属性
     */
    private final WeChatAppProperties properties;

    /**
     * HTTP 客户端，用于调用微信 OAuth API
     *
     * <p>优化说明（Netty/线程池/IO 性能优化方案 P2-1）：
     * 统一使用 @Qualifier("weChatApiWebClient") Bean，替代原 ManagedWebClientFactory.create()，
     * 使微信 API 调用共享 weChatApi 连接池，避免在 external-http 池额外占用连接。
     */
    private final WebClient webClient;

    /**
     * 构造器注入所有依赖。
     *
     * @param properties 微信 APP 配置属性
     * @param webClient 注入的微信 API WebClient
     */
    public WeChatOAuthService(WeChatAppProperties properties, @Qualifier("weChatApiWebClient") WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
    }

    /**
     * 生成网页授权URL
     *
     * <p>根据业务需求生成微信网页授权的完整URL，引导用户在微信客户端中打开
     * 以完成授权流程。生成的URL格式如下：
     * <pre>{@code * https://open.weixin.qq.com/connect/oauth2/authorize?
     *   appid=APPID&
     *   redirect_uri=REDIRECT_URI&
     *   response_type=code&
     *   scope=SCOPE&
     *   state=STATE#wechat_redirect
     * }</pre>
     *
     * <p><strong>参数说明</strong></p>
     * <ul>
     *   <li>{@code redirectUri} - 授权后重定向的回调链接地址，需要使用urlencode编码</li>
     *   <li>{@code scope} - 应用授权作用域：
     *     <ul>
     *       <li>{@code snsapi_base} - 静默授权，不弹出授权页面，直接跳转，
     *           只能获取用户openid</li>
     *       <li>{@code snsapi_userinfo} - 弹出授权页面，
     *           可通过openid拿到昵称、性别、所在地等信息</li>
     *     </ul>
     *   </li>
     *   <li>{@code state} - 重定向后会带上state参数，可用于防止CSRF攻击或
     *       携带自定义业务参数，建议使用随机字符串</li>
     * </ul>
     *
     * <p><strong>注意事项</strong></p>
     * <ul>
     *   <li>redirect_uri的域名必须在微信公众平台配置的授权回调域名下</li>
     *   <li>scope为snsapi_userinfo时会弹出授权页面</li>
     *   <li>URL最终必须以#wechat_redirect结尾</li>
     * </ul>
     *
     * <p><strong>{@code redirectUri} 为空时的行为</strong></p>
     * <p>如果传入的redirectUri为空或null，方法不会抛出异常，
     * 所生成的授权URL将不包含redirect_uri参数，但这样的URL在实际场景中并不完整。
     *
     * @param redirectUri 授权完成后的回调地址，不可为空，需要进行urlencode
     * @param scope 授权作用域，可选值：{@code snsapi_base} 或 {@code snsapi_userinfo}
     * @param state 业务状态参数，用于防CSRF或携带自定义参数，可为null
     * @return 完整的网页授权URL字符串
     */
    public String buildOauthUrl(String redirectUri, String scope, String state) {
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
        urlBuilder.append("&scope=").append(scope != null ? scope : "snsapi_base");
        if (state != null && !state.isEmpty()) {
            urlBuilder.append("&state=").append(state);
        }
        urlBuilder.append("#wechat_redirect");

        String oauthUrl = urlBuilder.toString();
        log.info("生成网页授权URL成功, scope: {}, redirectUri: {}", scope, redirectUri);
        return oauthUrl;
    }

    /**
     * 通过授权Code换取网页授权AccessToken
     *
     * <p>用户同意授权后，微信会将授权code传递到redirect_uri上，
     * 服务端通过该code换取网页授权access_token和openid等信息。
     *
     * <p><strong>返回数据说明</strong></p>
     * <p>成功时返回的Map包含以下字段：
     * <ul>
     *   <li>{@code access_token} - 网页授权接口调用凭证</li>
     *   <li>{@code expires_in} - access_token有效期（秒）</li>
     *   <li>{@code refresh_token} - 刷新token凭证</li>
     *   <li>{@code openid} - 用户唯一标识</li>
     *   <li>{@code scope} - 用户授权的作用域</li>
     *   <li>{@code unionid} - 用户统一标识（仅绑定开放平台后有）</li>
     * </ul>
     *
     * <p><strong>常见错误码</strong></p>
     * <ul>
     *   <li>{@code 40029} - code无效（code已被使用或过期）</li>
     *   <li>{@code 40163} - code已被使用</li>
     *   <li>{@code 41001} - 缺少access_token参数</li>
     *   <li>{@code 41002} - 缺少appid参数</li>
     * </ul>
     *
     * @param code 微信网页授权回调携带的code参数，每个code只能使用一次
     * @return 包含access_token、openid等授权信息的Map
     * @throws WeChatApiException 当微信API返回错误时抛出
     */
    public Mono<Map<String, Object>> getAccessTokenByCode(String code) {
        log.info("通过Code换取网页授权AccessToken, code: {}", code);
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(OAUTH_ACCESS_TOKEN_URL)
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .queryParam("code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .<Map<String, Object>>handle((result, sink) -> {
                    if (result.containsKey(ERR_CODE_KEY)) {
                        Number errcode = (Number) result.get(ERR_CODE_KEY);
                        String errmsg = (String) result.get(ERR_MSG_KEY);
                        if (errcode.intValue() != 0) {
                            log.error("通过Code换取AccessToken失败, errcode: {}, errmsg: {}", errcode, errmsg);
                            sink.error(new WeChatApiException(errcode.intValue(), errmsg));
                            return;
                        }
                    }
                    log.info("通过Code换取AccessToken成功, openid: {}", result.get("openid"));
                    sink.next(result);
                })
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("通过Code换取AccessToken请求失败", e);
                    }
                });
    }

    /**
     * 刷新网页授权AccessToken
     *
     * <p>网页授权access_token的过期时间较短（默认为7200秒），
     * 当access_token过期后，可以使用refresh_token获取新的access_token。
     *
     * <p><strong>返回数据说明</strong></p>
     * <p>成功时返回的Map包含以下字段：
     * <ul>
     *   <li>{@code access_token} - 新的网页授权接口调用凭证</li>
     *   <li>{@code expires_in} - 新的access_token有效期（秒）</li>
     *   <li>{@code refresh_token} - 新的刷新token凭证</li>
     *   <li>{@code openid} - 用户唯一标识</li>
     *   <li>{@code scope} - 用户授权的作用域</li>
     * </ul>
     *
     * <p><strong>注意事项</strong></p>
     * <ul>
     *   <li>refresh_token有效期较长（默认为30天）</li>
     *   <li>refresh_token过期后需要用户重新授权</li>
     *   <li>每次刷新后会返回新的refresh_token，旧的refresh_token同时失效</li>
     * </ul>
     *
     * @param refreshToken 网页授权刷新令牌
     * @return 包含新access_token、refresh_token等信息的Map
     * @throws WeChatApiException 当微信API返回错误时抛出
     */
    public Mono<Map<String, Object>> refreshAccessToken(String refreshToken) {
        log.info("刷新网页授权AccessToken");
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(OAUTH_REFRESH_TOKEN_URL)
                        .queryParam("appid", properties.getAppId())
                        .queryParam("grant_type", "refresh_token")
                        .queryParam("refresh_token", refreshToken)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .<Map<String, Object>>handle((result, sink) -> {
                    if (result.containsKey(ERR_CODE_KEY)) {
                        Number errcode = (Number) result.get(ERR_CODE_KEY);
                        String errmsg = (String) result.get(ERR_MSG_KEY);
                        if (errcode.intValue() != 0) {
                            log.error("刷新网页授权AccessToken失败, errcode: {}, errmsg: {}", errcode, errmsg);
                            sink.error(new WeChatApiException(errcode.intValue(), errmsg));
                            return;
                        }
                    }
                    log.info("刷新网页授权AccessToken成功");
                    sink.next(result);
                })
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("刷新网页授权AccessToken请求失败", e);
                    }
                });
    }

    /**
     * 通过网页授权AccessToken获取用户信息
     *
     * <p>使用网页授权access_token和openid拉取用户详细信息。
     * 此接口需scope为{@code snsapi_userinfo}时才能成功调用，
     * 会返回用户的昵称、头像、性别、省份、城市、国家等信息。
     *
     * <p><strong>返回数据说明</strong></p>
     * <p>成功时返回的Map包含以下字段：
     * <ul>
     *   <li>{@code openid} - 用户唯一标识</li>
     *   <li>{@code nickname} - 用户昵称</li>
     *   <li>{@code sex} - 性别（1-男性，2-女性，0-未知）</li>
     *   <li>{@code province} - 省份</li>
     *   <li>{@code city} - 城市</li>
     *   <li>{@code country} - 国家</li>
     *   <li>{@code headimgurl} - 用户头像URL</li>
     *   <li>{@code privilege} - 用户特权信息（JSON数组）</li>
     *   <li>{@code unionid} - 用户统一标识（绑定开放平台后有）</li>
     * </ul>
     *
     * <p><strong>常见错误码</strong></p>
     * <ul>
     *   <li>{@code 40003} - 不合法的OpenID</li>
     *   <li>{@code 40014} - 不合法的AccessToken</li>
     *   <li>{@code 48001} - API功能未授权</li>
     * </ul>
     *
     * @param accessToken 网页授权access_token
     * @param openId 用户OpenID
     * @return 包含用户详细信息的Map
     * @throws WeChatApiException 当微信API返回错误时抛出
     */
    public Mono<Map<String, Object>> getUserInfoByAccessToken(String accessToken, String openId) {
        log.info("通过网页授权获取用户信息, openId: {}", openId);
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(OAUTH_USERINFO_URL)
                        .queryParam("access_token", accessToken)
                        .queryParam("openid", openId)
                        .queryParam("lang", "zh_CN")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .<Map<String, Object>>handle((result, sink) -> {
                    if (result.containsKey(ERR_CODE_KEY)) {
                        Number errcode = (Number) result.get(ERR_CODE_KEY);
                        String errmsg = (String) result.get(ERR_MSG_KEY);
                        if (errcode.intValue() != 0) {
                            log.error("获取网页授权用户信息失败, errcode: {}, errmsg: {}", errcode, errmsg);
                            sink.error(new WeChatApiException(errcode.intValue(), errmsg));
                            return;
                        }
                    }
                    log.info("获取网页授权用户信息成功, openId: {}", openId);
                    sink.next(result);
                })
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("获取网页授权用户信息请求失败", e);
                    }
                });
    }

    /**
     * 校验网页授权AccessToken是否有效
     *
     * <p>向微信服务器验证指定的网页授权access_token和openid是否仍然有效。
     * 常用于判断用户的网页授权登录态是否已失效，需要刷新或重新授权。
     *
     * <p><strong>适用场景</strong></p>
     * <ul>
     *   <li>在调用获取用户信息接口前先校验token是否有效</li>
     *   <li>定期检查用户的网页授权登录态</li>
     *   <li>作为定时任务批量刷新即将过期的token</li>
     * </ul>
     *
     * @param accessToken 网页授权access_token
     * @param openId 用户OpenID
     * @return 有效返回true，无效或请求失败返回false
     */
    public Mono<Boolean> checkAccessToken(String accessToken, String openId) {
        log.info("校验网页授权AccessToken, openId: {}", openId);
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path(OAUTH_AUTH_URL)
                        .queryParam("access_token", accessToken)
                        .queryParam("openid", openId)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> {
                    Number errcode = (Number) result.get(ERR_CODE_KEY);
                    boolean valid = errcode.intValue() == 0;
                    log.info("网页授权AccessToken校验结果: {}", valid ? "有效" : "无效");
                    return valid;
                })
                .onErrorResume(e -> {
                    log.error("校验网页授权AccessToken请求失败", e);
                    return Mono.just(false);
                });
    }

    /**
     * 一键完成网页授权全流程——Code换取用户信息
     *
     * <p>简化版本的网页授权流程，将"Code换Token"和"获取用户信息"两步合并为一步。
     * 适用于只需要用户信息而不需要独立管理access_token的场景。
     *
     * <p><strong>处理流程</strong></p>
     * <ol>
     *   <li>调用微信API使用code换取access_token和openid</li>
     *   <li>使用上一步获取的access_token和openid获取用户详细信息</li>
     * </ol>
     *
     * @param code 微信网页授权回调中的code
     * @return 包含用户昵称、头像等详细信息的Map
     */
    public Mono<Map<String, Object>> getUserInfoByCode(String code) {
        log.info("通过授权Code直接获取用户信息, code: {}", code);
        return getAccessTokenByCode(code).flatMap(tokenInfo -> {
            String accessToken = (String) tokenInfo.get("access_token");
            String openId = (String) tokenInfo.get("openid");
            return getUserInfoByAccessToken(accessToken, openId);
        });
    }
}
