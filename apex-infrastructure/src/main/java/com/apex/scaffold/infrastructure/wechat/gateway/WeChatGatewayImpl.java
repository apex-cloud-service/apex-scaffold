package com.apex.scaffold.infrastructure.wechat.gateway;

import com.apex.scaffold.domain.gateway.WeChatGateway;
import com.apex.scaffold.infrastructure.circuitbreaker.CircuitBreakerService;
import com.apex.scaffold.infrastructure.wechat.service.WeChatAppService;
import com.apex.scaffold.infrastructure.wechat.service.WeChatMiniProgramService;
import com.apex.scaffold.infrastructure.wechat.service.WeChatMpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 微信网关实现类
 *
 * <p>实现WeChatGateway接口，通过WeChatAppService（开放平台移动应用）、
 * WeChatMiniProgramService（小程序）与 WeChatMpService（公众号）与微信服务端
 * 进行交互。所有外部API调用均通过断路器保护，防止级联故障。
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><b>领域对象封装</b>：将API原始响应转换为领域层可用的结果对象</li>
 *   <li><b>断路器保护</b>：所有外部API调用均通过断路器保护</li>
 *   <li><b>异常处理统一</b>：所有API异常被捕获并转换为降级结果</li>
 *   <li><b>响应式编程</b>：使用 Project Reactor 的 Mono 进行非阻塞调用</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.2.0
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WeChatGatewayImpl implements WeChatGateway {

    /**
     * 微信应用服务
     *
     * <p>用于与微信开放平台进行交互，提供登录、获取用户信息等能力
     */
    private final WeChatAppService weChatAppService;

    /**
     * 微信小程序服务
     *
     * <p>用于与微信小程序服务端 API 交互（jscode2session / 手机号置换）。
     */
    private final WeChatMiniProgramService weChatMiniProgramService;

    /**
     * 微信公众号服务
     *
     * <p>用于与微信公众号网页授权 API 交互（code 换会话 / 授权跳转 / state）。
     */
    private final WeChatMpService weChatMpService;

    /**
     * 断路器服务
     *
     * <p>用于保护外部API调用，防止级联故障。
     *
     * @see CircuitBreakerService
     */
    private final CircuitBreakerService circuitBreakerService;

    /**
     * 微信登录认证
     *
     * <p>使用微信授权code换取access_token和用户信息。
     * 调用通过断路器保护，当微信服务不可用时返回降级结果。
     *
     * <h3>断路器保护</h3>
     * <p>使用断路器保护微信API调用，当失败率超过阈值时自动熔断，防止级联故障。</p>
     *
     * @param code 微信授权code
     * @return 微信登录结果，断路器打开时返回降级结果
     * @see CircuitBreakerService
     */
    @Override
    public Mono<WeChatLoginResult> login(String code) {
        Mono<WeChatLoginResult> apiCall = weChatAppService
                .login(code)
                .map(this::toLoginResult)
                .onErrorResume(e -> {
                    log.error("微信登录失败, code: {}", code, e);
                    return Mono.just(createLoginFallbackResult());
                });

        // 使用断路器保护外部API调用
        return circuitBreakerService.withCircuitBreaker("wechat-api", apiCall, Mono.just(createLoginFallbackResult()));
    }

    /**
     * 获取微信用户信息
     *
     * <p>使用access_token和openid获取用户详细信息。
     * 调用通过断路器保护，当微信服务不可用时返回降级结果。
     *
     * @param accessToken 微信access_token
     * @param openId 用户openId
     * @return 微信用户信息，断路器打开时返回降级结果
     */
    @Override
    public Mono<WeChatUserInfo> getUserInfo(String accessToken, String openId) {
        Mono<WeChatUserInfo> apiCall = weChatAppService
                .getUserInfo(accessToken, openId)
                .map(this::toUserInfo)
                .onErrorResume(e -> {
                    log.error("获取微信用户信息失败, openId: {}", openId, e);
                    return Mono.just(createUserInfoFallbackResult());
                });

        // 使用断路器保护外部API调用
        return circuitBreakerService.withCircuitBreaker(
                "wechat-api", apiCall, Mono.just(createUserInfoFallbackResult()));
    }

    /**
     * 获取微信手机号
     *
     * <p>使用手机号授权凭证获取用户手机号。
     * 调用通过断路器保护，当微信服务不可用时返回降级结果。
     *
     * @param phoneCode 微信手机号授权凭证
     * @return 微信手机号结果，断路器打开时返回降级结果
     */
    @Override
    public Mono<WeChatPhoneResult> getPhoneNumber(String phoneCode) {
        Mono<WeChatPhoneResult> apiCall = weChatAppService
                .getPhoneNumber(phoneCode)
                .map(this::toPhoneResult)
                .onErrorResume(e -> {
                    log.error("获取微信手机号失败", e);
                    return Mono.just(createPhoneResultFallbackResult());
                });

        // 使用断路器保护外部API调用
        return circuitBreakerService.withCircuitBreaker(
                "wechat-api", apiCall, Mono.just(createPhoneResultFallbackResult()));
    }

    /**
     * 微信小程序 code 换取会话（jscode2session）。
     *
     * <p>调用通过断路器保护；渠道未配置（{@code WECHAT_CHANNEL_NOT_CONFIGURED}）属于
     * 部署配置缺失，必须明确透传业务错误码而非静默降级，因此使用自定义降级逻辑区分：
     * 业务异常直接抛出，网络/服务类异常降级为空会话（由策略层判定登录失败）。
     *
     * @param jsCode 小程序临时登录凭证
     * @return 小程序会话信息
     */
    @Override
    public Mono<MiniProgramSession> miniProgramCodeToSession(String jsCode) {
        Mono<MiniProgramSession> apiCall =
                weChatMiniProgramService.codeToSession(jsCode).map(this::toMiniProgramSession);
        return circuitBreakerService.withCircuitBreaker("wechat-api", apiCall).onErrorResume(e -> {
            if (e instanceof com.apex.scaffold.common.exception.BusinessException) {
                return Mono.error(e);
            }
            log.error("小程序 jscode2session 失败, code: {}", jsCode, e);
            return Mono.just(new MiniProgramSession());
        });
    }

    /**
     * 获取小程序用户手机号。
     *
     * <p>调用通过断路器保护，当微信服务不可用时返回降级结果（手机号为空）。
     *
     * @param phoneCode 小程序手机号授权凭证
     * @return 微信手机号结果
     */
    @Override
    public Mono<WeChatPhoneResult> miniProgramPhoneNumber(String phoneCode) {
        Mono<WeChatPhoneResult> apiCall = weChatMiniProgramService
                .getPhoneNumber(phoneCode)
                .map(this::toPhoneResult)
                .onErrorResume(e -> {
                    log.error("获取小程序手机号失败", e);
                    return Mono.just(createPhoneResultFallbackResult());
                });
        return circuitBreakerService.withCircuitBreaker(
                "wechat-api", apiCall, Mono.just(createPhoneResultFallbackResult()));
    }

    /**
     * 将Map转换为小程序会话信息
     *
     * @param map 微信 jscode2session 接口返回的Map
     * @return 小程序会话信息
     */
    private MiniProgramSession toMiniProgramSession(java.util.Map<String, Object> map) {
        MiniProgramSession session = new MiniProgramSession();
        session.setOpenid((String) map.get("openid"));
        session.setUnionid((String) map.get("unionid"));
        session.setSessionKey((String) map.get("session_key"));
        return session;
    }

    /**
     * 公众号网页授权 code 换取会话。
     *
     * <p>调用通过断路器保护；渠道未配置（{@code WECHAT_CHANNEL_NOT_CONFIGURED}）属于
     * 部署配置缺失，必须明确透传业务错误码而非静默降级（与小程序渠道一致）；
     * 网络/服务类异常降级为空会话，由策略层判定登录失败。
     *
     * @param code 网页授权回调携带的 code
     * @return 公众号网页授权会话信息
     */
    @Override
    public Mono<MpAuthSession> mpAuthCodeToSession(String code) {
        Mono<MpAuthSession> apiCall = weChatMpService.getAccessTokenByCode(code).map(this::toMpAuthSession);
        return circuitBreakerService.withCircuitBreaker("wechat-api", apiCall).onErrorResume(e -> {
            if (e instanceof com.apex.scaffold.common.exception.BusinessException) {
                return Mono.error(e);
            }
            log.error("公众号网页授权失败, code: {}", code, e);
            return Mono.just(new MpAuthSession());
        });
    }

    /**
     * 生成公众号网页授权跳转 URL（委托 {@link WeChatMpService}，纯字符串拼装无 I/O）。
     *
     * @param redirectUri 授权后回跳的 H5 页面地址
     * @param scope       授权作用域，空时按 snsapi_base
     * @param state       防 CSRF 随机串
     * @return 完整授权跳转 URL
     */
    @Override
    public String mpAuthorizeUrl(String redirectUri, String scope, String state) {
        return weChatMpService.buildAuthorizeUrl(redirectUri, scope, state);
    }

    /**
     * 签发公众号 OAuth state 一次性防 CSRF 凭证（委托 {@link WeChatMpService}）。
     *
     * @return state 随机串（Redis 登记，5 分钟有效）
     */
    @Override
    public Mono<String> mpIssueAuthState() {
        return weChatMpService.issueAuthState();
    }

    /**
     * 原子校验并消费公众号 OAuth state（委托 {@link WeChatMpService}）。
     *
     * @param state 授权回跳携带的 state
     * @return true 表示校验通过（state 一次性消费）
     */
    @Override
    public Mono<Boolean> mpValidateAuthState(String state) {
        return weChatMpService.validateAuthState(state);
    }

    /**
     * 将Map转换为公众号网页授权会话信息
     *
     * @param map 微信 /sns/oauth2/access_token 接口返回的Map
     * @return 公众号网页授权会话信息
     */
    private MpAuthSession toMpAuthSession(java.util.Map<String, Object> map) {
        MpAuthSession session = new MpAuthSession();
        session.setOpenid((String) map.get("openid"));
        session.setUnionid((String) map.get("unionid"));
        session.setAccessToken((String) map.get("access_token"));
        session.setRefreshToken((String) map.get("refresh_token"));
        session.setScope((String) map.get("scope"));
        Object expiresIn = map.get("expires_in");
        if (expiresIn instanceof Number) {
            session.setExpiresIn(((Number) expiresIn).longValue());
        }
        return session;
    }

    /**
     * 将Map转换为微信登录结果
     *
     * @param map 微信API返回的登录结果Map
     * @return 微信登录结果
     */
    private WeChatLoginResult toLoginResult(java.util.Map<String, Object> map) {
        WeChatLoginResult result = new WeChatLoginResult();
        result.setOpenid((String) map.get("openid"));
        result.setUnionid((String) map.get("unionid"));
        result.setAccessToken((String) map.get("access_token"));
        result.setRefreshToken((String) map.get("refresh_token"));
        Object expiresIn = map.get("expires_in");
        if (expiresIn instanceof Number) {
            result.setExpiresIn(((Number) expiresIn).longValue());
        }
        return result;
    }

    /**
     * 将Map转换为微信用户信息
     *
     * @param map 微信API返回的用户信息Map
     * @return 微信用户信息
     */
    private WeChatUserInfo toUserInfo(java.util.Map<String, Object> map) {
        WeChatUserInfo result = new WeChatUserInfo();
        result.setOpenid((String) map.get("openid"));
        result.setNickname((String) map.get("nickname"));
        result.setAvatarUrl((String) map.get("headimgurl"));
        Object sex = map.get("sex");
        if (sex instanceof Integer) {
            result.setGender((Integer) sex);
        } else if (sex instanceof Number) {
            result.setGender(((Number) sex).intValue());
        }
        result.setCountry((String) map.get("country"));
        result.setProvince((String) map.get("province"));
        result.setCity((String) map.get("city"));
        result.setLanguage((String) map.get("language"));
        return result;
    }

    /**
     * 将Map转换为微信手机号结果
     *
     * <p>根据微信开放平台 {@code /wxa/business/getuserphonenumber} 接口返回的JSON结构解析手机号信息。
     * 微信API返回的实际结构为：
     * <pre>
     * {
     *   "phone_info": {
     *     "phoneNumber": "13800138000",
     *     "purePhoneNumber": "13800138000",
     *     "countryCode": "86"
     *   }
     * }
     * </pre>
     * 需要从嵌套的 {@code phone_info} 对象中提取手机号信息。
     *
     * @param map 微信API返回的手机号结果Map，包含嵌套的{@code "phone_info"}对象
     * @return 微信手机号结果，包含 phoneNumber、purePhoneNumber 和 countryCode
     * @see <a href="https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-info/phone-number/getPhoneNumber.html">微信获取手机号API文档</a>
     */
    private WeChatPhoneResult toPhoneResult(java.util.Map<String, Object> map) {
        WeChatPhoneResult result = new WeChatPhoneResult();
        Object phoneInfo = map.get("phone_info");
        if (phoneInfo instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> infoMap = (java.util.Map<String, Object>) phoneInfo;
            result.setPhoneNumber((String) infoMap.get("phoneNumber"));
            result.setPurePhoneNumber((String) infoMap.get("purePhoneNumber"));
            result.setCountryCode((String) infoMap.get("countryCode"));
        }
        return result;
    }

    /**
     * 创建微信登录降级结果
     *
     * <p>当断路器打开或微信服务不可用时，返回预设的降级结果。
     *
     * @return 降级的微信登录结果
     */
    private WeChatLoginResult createLoginFallbackResult() {
        WeChatLoginResult result = new WeChatLoginResult();
        result.setOpenid(null);
        result.setUnionid(null);
        result.setAccessToken(null);
        result.setRefreshToken(null);
        result.setExpiresIn(0L);
        log.warn("微信登录服务暂时不可用，返回降级结果");
        return result;
    }

    /**
     * 创建微信用户信息降级结果
     *
     * <p>当断路器打开或微信服务不可用时，返回预设的降级结果。
     *
     * @return 降级的微信用户信息结果
     */
    private WeChatUserInfo createUserInfoFallbackResult() {
        WeChatUserInfo result = new WeChatUserInfo();
        result.setOpenid(null);
        result.setNickname(null);
        result.setAvatarUrl(null);
        result.setGender(null);
        result.setCountry(null);
        result.setProvince(null);
        result.setCity(null);
        log.warn("微信用户信息服务暂时不可用，返回降级结果");
        return result;
    }

    /**
     * 创建微信手机号降级结果
     *
     * <p>当断路器打开或微信服务不可用时，返回预设的降级结果。
     *
     * @return 降级的微信手机号结果
     */
    private WeChatPhoneResult createPhoneResultFallbackResult() {
        WeChatPhoneResult result = new WeChatPhoneResult();
        result.setPhoneNumber(null);
        result.setPurePhoneNumber(null);
        log.warn("微信手机号服务暂时不可用，返回降级结果");
        return result;
    }
}
