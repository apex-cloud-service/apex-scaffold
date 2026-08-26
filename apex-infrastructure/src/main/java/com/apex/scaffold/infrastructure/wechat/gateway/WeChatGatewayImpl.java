package com.apex.scaffold.infrastructure.wechat.gateway;

import com.apex.scaffold.domain.gateway.WeChatGateway;
import com.apex.scaffold.infrastructure.circuitbreaker.CircuitBreakerService;
import com.apex.scaffold.infrastructure.wechat.service.WeChatAppService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * 微信网关实现类
 *
 * <p>实现WeChatGateway接口，通过WeChatAppService与微信开放平台进行交互。
 * 所有外部API调用均通过断路器保护，防止级联故障。
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
 * @version 2.0.0
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class WeChatGatewayImpl implements WeChatGateway {
    private static final Logger log = LoggerFactory.getLogger(WeChatGatewayImpl.class);

    /**
     * 微信应用服务
     *
     * <p>用于与微信开放平台进行交互，提供登录、获取用户信息等能力
     */
    private final WeChatAppService weChatAppService;

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
    /**
     * login
     *
     * @param code 参数
     *
     * @return 结果
     */
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
    /**
     * 获取UserInfo
     *
     * @param accessToken 参数
     *
     * @param openId 参数
     *
     * @return 结果
     */
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
    /**
     * 获取PhoneNumber
     *
     * @param phoneCode 参数
     *
     * @return 结果
     */
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
