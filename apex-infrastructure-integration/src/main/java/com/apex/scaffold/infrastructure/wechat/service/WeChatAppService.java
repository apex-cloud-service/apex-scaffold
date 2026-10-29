package com.apex.scaffold.infrastructure.wechat.service;

import com.apex.scaffold.infrastructure.wechat.config.WeChatAppProperties;
import com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 微信APP服务
 *
 * <p>提供微信APP登录、AccessToken获取、用户信息查询、手机号获取等功能。
 * 所有接口均基于微信开放平台移动应用接入规范实现，
 * 通过 {@link WebClient} 以响应式编程方式调用微信 API。
 *
 * <h3>支持的功能</h3>
 * <ul>
 *   <li>微信 APP 授权登录（OAuth 2.0 授权码模式）</li>
 *   <li>刷新 AccessToken</li>
 *   <li>获取微信用户基本信息（昵称、头像等）</li>
 *   <li>校验 AccessToken 有效性</li>
 *   <li>获取全局 AccessToken（Client Credentials 模式）</li>
 *   <li>发送模板消息</li>
 *   <li>发送客服消息</li>
 *   <li>获取微信绑定手机号</li>
 * </ul>
 *
 * <h3>错误处理</h3>
 * <p>所有方法均统一检查微信 API 返回的 {@code errcode} 字段，
 * 非 0 时抛出 {@link WeChatApiException}。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see WeChatApiException
 * @see WeChatAppProperties
 */
@Slf4j
@Service
public class WeChatAppService {

    /**
     * 微信 API 返回的用户 OpenID 字段名
     */
    private static final String OPEN_ID_KEY = "openid";

    /**
     * 微信 APP 配置属性，包含 AppID 和 AppSecret
     */
    private final WeChatAppProperties properties;

    /**
     * HTTP 客户端，用于调用微信 API。
     *
     * <p>优化说明（Netty/线程池/IO 性能优化方案 P2-1）：
     * 统一使用 {@code @Qualifier("weChatApiWebClient")} Bean，
     * 替代原 {@code ManagedWebClientFactory.create()}，
     * 使微信 API 调用共享 wechat-api-pool 连接池，避免在 external-http 池额外占用连接。
     */
    private final WebClient webClient;

    /**
     * 构造微信 APP 服务。
     *
     * @param properties 微信 APP 配置属性
     * @param webClient  微信公众号 API WebClient（通过 Qualifier 注入）
     */
    public WeChatAppService(WeChatAppProperties properties, @Qualifier("weChatApiWebClient") WebClient webClient) {
        this.properties = properties;
        this.webClient = webClient;
    }

    /**
     * 微信APP登录
     *
     * <p>通过授权码（code）获取 access_token 和 openid，
     * 使用微信 OAuth 2.0 授权码模式，调用 {@code /sns/oauth2/access_token} 接口。
     *
     * <h3>请求参数</h3>
     * <ul>
     *   <li>{@code appid} - 应用唯一标识（必填）</li>
     *   <li>{@code secret} - 应用密钥 AppSecret（必填）</li>
     *   <li>{@code code} - 授权临时票据（必填，有效期10分钟，一次性使用）</li>
     *   <li>{@code grant_type} - 固定值 {@code authorization_code}（必填）</li>
     * </ul>
     *
     * <h3>成功响应字段</h3>
     * <ul>
     *   <li>{@code access_token} - 接口调用凭证</li>
     *   <li>{@code expires_in} - access_token 有效期（秒），默认7200</li>
     *   <li>{@code refresh_token} - 用户刷新 access_token 凭证（有效期180天）</li>
     *   <li>{@code openid} - 授权用户唯一标识</li>
     *   <li>{@code scope} - 用户授权的作用域</li>
     *   <li>{@code unionid} - 用户统一标识（仅当已获得 userinfo 授权时返回）</li>
     * </ul>
     *
     * @param code 微信授权码，由客户端通过微信 SDK 获取，每个code只能使用一次
     * @return 登录结果，包含 access_token、openid、unionid 等字段
     * @throws WeChatApiException 当微信 API 返回错误时抛出
     */
    public Mono<Map<String, Object>> login(String code) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sns/oauth2/access_token")
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .queryParam("code", code)
                        .queryParam("grant_type", "authorization_code")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("微信登录"))
                .doOnSuccess(result -> log.info("微信登录成功, openId: {}", result.get(OPEN_ID_KEY)))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("微信登录请求失败", e);
                    }
                });
    }

    /**
     * 刷新AccessToken
     *
     * <p>使用 refresh_token 刷新过期的 access_token，
     * 延长授权有效期，避免用户重新登录。
     * 调用微信开放平台 {@code /sns/oauth2/refresh_token} 接口。
     *
     * <h3>刷新行为</h3>
     * <ul>
     *   <li>若 access_token 已超时，获取新的 access_token</li>
     *   <li>若 access_token 未超时，返回相同 token 但刷新超时时间</li>
     * </ul>
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>refresh_token 有效期180天且无法续期</li>
     *   <li>每次刷新返回新的 refresh_token，旧的失效</li>
     *   <li>refresh_token 失效后需用户重新授权</li>
     * </ul>
     *
     * @param refreshToken 刷新令牌，由登录接口返回
     * @return 刷新结果，包含新的 access_token、refresh_token、openid 等字段
     * @throws WeChatApiException 当微信 API 返回错误时抛出
     */
    public Mono<Map<String, Object>> refreshToken(String refreshToken) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sns/oauth2/refresh_token")
                        .queryParam("appid", properties.getAppId())
                        .queryParam("grant_type", "refresh_token")
                        .queryParam("refresh_token", refreshToken)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("刷新Token"))
                .doOnSuccess(result -> log.info("刷新Token成功, openId: {}", result.get(OPEN_ID_KEY)))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("刷新Token请求失败", e);
                    }
                });
    }

    /**
     * 获取微信用户信息
     *
     * <p>使用 access_token 和 openid 获取微信用户的个人资料，
     * 调用微信开放平台 {@code /sns/userinfo} 接口。
     *
     * <h3>返回字段说明</h3>
     * <ul>
     *   <li>{@code openid} - 普通用户的标识</li>
     *   <li>{@code nickname} - 用户昵称</li>
     *   <li>{@code sex} - 性别（1-男性，2-女性，0-未知）</li>
     *   <li>{@code headimgurl} - 用户头像URL</li>
     *   <li>{@code privilege} - 用户特权信息（JSON数组）</li>
     *   <li>{@code unionid} - 用户统一标识（绑定开放平台后返回）</li>
     * </ul>
     *
     * <h3>重要提示</h3>
     * <p>根据微信开放平台公告（2021年10月20日起生效），
     * 该接口将不再返回用户性别（sex）及地区信息（province、city、country）。
     *
     * <h3>频率限制</h3>
     * <p>调用频率：50000次/分钟</p>
     *
     * @param accessToken 访问令牌，由登录接口返回
     * @param openId 用户 OpenID，微信用户唯一标识
     * @return 用户信息，包含 nickname、headimgurl 等字段
     * @throws WeChatApiException 当微信 API 返回错误时抛出
     */
    public Mono<Map<String, Object>> getUserInfo(String accessToken, String openId) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sns/userinfo")
                        .queryParam("access_token", accessToken)
                        .queryParam("openid", openId)
                        .queryParam("lang", "zh_CN")
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("获取用户信息"))
                .doOnSuccess(result -> log.info("获取用户信息成功, openId: {}", openId))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("获取用户信息请求失败", e);
                    }
                });
    }

    /**
     * 检验AccessToken是否有效
     *
     * <p>向微信开放平台验证指定的 access_token 和 openid 是否有效。
     * 调用 {@code /sns/auth} 接口。
     * 常用于判断用户登录态是否已失效。
     *
     * <h3>接口说明</h3>
     * <p>成功时返回 {@code {"errcode": 0, "errmsg": "ok"}}。
     *
     * @param accessToken 访问令牌
     * @param openId 用户 OpenID
     * @return 校验结果，有效返回 {@code true}，无效或请求失败返回 {@code false}
     */
    public Mono<Boolean> checkAccessToken(String accessToken, String openId) {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sns/auth")
                        .queryParam("access_token", accessToken)
                        .queryParam("openid", openId)
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result ->
                        Integer.valueOf(0).equals(WeChatApiResponse.from(result).errorCode()))
                .onErrorResume(e -> {
                    log.error("检验AccessToken失败", e);
                    return Mono.just(false);
                });
    }

    /**
     * 获取全局AccessToken
     *
     * <p>使用 Client Credentials 模式获取全局 AccessToken。
     * 调用微信开放平台 {@code /cgi-bin/token} 接口。
     * 该 Token 用于调用发送模板消息、客服消息等需要全局凭证的微信 API。
     * 调用失败时自动重试 1 次。
     *
     * <h3>响应说明</h3>
     * <ul>
     *   <li>{@code access_token} - 全局访问令牌</li>
     *   <li>{@code expires_in} - 有效期（秒），默认为7200秒</li>
     * </ul>
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>每天调用次数有限制（2000次/天）</li>
     *   <li>新获取的 token 会使旧 token 失效</li>
     *   <li>建议服务端缓存 token，在过期前刷新</li>
     * </ul>
     *
     * @return 全局 AccessToken 字符串
     * @throws WeChatApiException 当微信 API 返回错误时抛出
     */
    public Mono<String> getGlobalAccessToken() {
        return webClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/token")
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", properties.getAppId())
                        .queryParam("secret", properties.getAppSecret())
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .retry(1)
                .map(result -> WeChatApiResponse.from(result).requireSuccess("获取全局AccessToken"))
                .doOnSuccess(result -> log.info("获取全局AccessToken成功"))
                .map(result -> (String) result.get("access_token"))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("获取全局AccessToken请求失败", e);
                    }
                });
    }

    /**
     * 发送模板消息
     *
     * <p>通过微信模板消息接口向指定用户发送服务通知。
     * 调用微信公众平台 {@code /cgi-bin/message/template/send} 接口。
     * 调用失败时自动重试 1 次。
     *
     * <h3>接口说明</h3>
     * <ul>
     *   <li>仅认证服务号可用</li>
     *   <li>每个账号最多25个模板</li>
     *   <li>日调用上限10万次（粉丝超10W/100W/1000W时自动提升）</li>
     *   <li>模板中参数必须以 {@code .DATA} 结尾</li>
     * </ul>
     *
     * @param accessToken 全局 AccessToken，需先通过 {@link #getGlobalAccessToken()} 获取
     * @param templateData 模板消息数据，需符合微信模板消息规范，包含 touser、template_id、data 等字段
     * @return 发送结果，errmsg 为 "ok" 表示成功
     * @throws WeChatApiException 当微信 API 返回非 0 错误码时抛出
     */
    public Mono<Map<String, Object>> sendTemplateMessage(String accessToken, Map<String, Object> templateData) {
        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/message/template/send")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(templateData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .retry(1)
                .map(result -> WeChatApiResponse.from(result).requireSuccess("发送模板消息"))
                .doOnSuccess(result -> log.info("发送模板消息成功"))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("发送模板消息请求失败", e);
                    }
                });
    }

    /**
     * 发送客服消息
     *
     * <p>通过微信客服消息接口向指定用户发送消息。
     * 调用微信公众平台 {@code /cgi-bin/message/custom/send} 接口。
     * 调用失败时自动重试 1 次。
     *
     * <h3>接口说明</h3>
     * <ul>
     *   <li>用户与客服之间在48小时内有过交互才能发送</li>
     *   <li>支持文本、图片、语音、视频、音乐、图文等多种消息类型</li>
     * </ul>
     *
     * @param accessToken 全局 AccessToken，需先通过 {@link #getGlobalAccessToken()} 获取
     * @param messageData 客服消息数据，需符合微信客服消息规范，包含 touser、msgtype 等字段
     * @return 发送结果，errmsg 为 "ok" 表示成功
     * @throws WeChatApiException 当微信 API 返回非 0 错误码时抛出
     */
    public Mono<Map<String, Object>> sendCustomerMessage(String accessToken, Map<String, Object> messageData) {
        return webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/cgi-bin/message/custom/send")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(messageData)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .retry(1)
                .map(result -> WeChatApiResponse.from(result).requireSuccess("发送客服消息"))
                .doOnSuccess(result -> log.info("发送客服消息成功"))
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("发送客服消息请求失败", e);
                    }
                });
    }

    /**
     * 获取微信手机号
     *
     * <p>使用手机号授权凭证调用微信开放平台接口获取用户手机号。
     * 该方法首先通过 {@link #getGlobalAccessToken()} 获取全局 AccessToken，
     * 再调用微信开放平台的 {@code /wxa/business/getuserphonenumber} 接口，
     * 从返回结果的 {@code phone_info.purePhoneNumber} 字段中提取纯手机号。
     *
     * <h3>接口说明</h3>
     * <ul>
     *   <li>接口地址：{@code POST /wxa/business/getuserphonenumber?access_token=ACCESS_TOKEN}</li>
     *   <li>请求体：{@code {"code": "手机号授权凭证"}}</li>
     *   <li>响应格式：
     *     <pre>{@code * {
     *   "errcode": 0,
     *   "errmsg": "ok",
     *   "phone_info": {
     *     "phoneNumber": "13800138000",
     *     "purePhoneNumber": "13800138000",
     *     "countryCode": "86",
     *     "watermark": {
     *       "timestamp": 1637744274,
     *       "appid": "wxabcdef"
     *     }
     *   }
     * }
     *     }</pre>
     *   </li>
     *   <li>官方文档：<a href="https://developers.weixin.qq.com/doc/oplatform/Mobile_App/WeChat_Login/Get_phone_number.html">
     *       微信开放平台 - 获取手机号</a></li>
     * </ul>
     *
     * <h3>调用链路</h3>
     * <ol>
     *   <li>客户端通过微信 SDK 获取手机号授权凭证（phoneCode）</li>
     *   <li>应用层的微信手机号登录或手机号绑定流程
     *       通过 {@link com.apex.scaffold.domain.gateway.WeChatGateway} 调用本方法</li>
     *   <li>本方法先调用 {@link #getGlobalAccessToken()} 获取全局 AccessToken</li>
     *   <li>再通过 {@link WebClient} 向微信开放平台发起 POST 请求</li>
     *   <li>解析响应中的 {@code phone_info} 提取手机号返回</li>
     * </ol>
     *
     * <h3>错误码说明</h3>
     * <ul>
     *   <li>{@code 40001} - AppSecret 错误或 AccessToken 无效</li>
     *   <li>{@code 40013} - 无效的 AppID</li>
     *   <li>{@code 40029} - code 无效（手机号授权凭证已过期或已被使用）</li>
     *   <li>{@code 45011} - 频率限制，每个用户每分钟 100 次</li>
     * </ul>
     *
     * @param phoneCode 微信手机号授权凭证，由微信 SDK 在客户端获取后传入，不能为空
     * @return 包含手机号信息的 {@link Mono}，Map 中 key 为 {@code "phone"}，
     *         value 为不带国家码的纯手机号字符串（如 {@code "13800138000"}）
     * @throws WeChatApiException 当微信 API 返回非 0 错误码，
     *         或获取全局 AccessToken 失败，
     *         或响应中缺少 phone_info 字段时抛出
     * @see #getGlobalAccessToken()
     * @since 1.0.0
     */
    public Mono<Map<String, Object>> getPhoneNumber(String phoneCode) {
        log.info("获取微信手机号, phoneCode: {}", phoneCode);
        return getGlobalAccessToken().flatMap(accessToken -> webClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/wxa/business/getuserphonenumber")
                        .queryParam("access_token", accessToken)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("code", phoneCode))
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                .map(result -> WeChatApiResponse.from(result).requireSuccess("获取微信手机号"))
                .map(result -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> phoneInfo = (Map<String, Object>) result.get("phone_info");
                    if (phoneInfo == null) {
                        log.error("微信手机号接口返回数据中缺少 phone_info 字段");
                        throw new WeChatApiException(-1, "获取手机号失败：响应中缺少 phone_info");
                    }
                    String phoneNumber = (String) phoneInfo.get("purePhoneNumber");
                    String countryCode = (String) phoneInfo.get("countryCode");
                    log.info("获取微信手机号成功");
                    Map<String, Object> phoneResult = new java.util.HashMap<>();
                    phoneResult.put("phone", phoneNumber != null ? phoneNumber : (String) phoneInfo.get("phoneNumber"));
                    if (countryCode != null) {
                        phoneResult.put("countryCode", countryCode);
                    }
                    return phoneResult;
                })
                .doOnError(e -> {
                    if (!(e instanceof WeChatApiException)) {
                        log.error("获取微信手机号请求失败", e);
                    }
                }));
    }
}
