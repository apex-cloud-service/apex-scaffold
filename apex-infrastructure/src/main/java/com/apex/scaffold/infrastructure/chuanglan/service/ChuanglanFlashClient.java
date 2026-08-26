package com.apex.scaffold.infrastructure.chuanglan.service;

import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanProperties;
import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanWebClientConfig;
import com.apex.scaffold.infrastructure.chuanglan.exception.ChuanglanApiException;
import com.apex.scaffold.infrastructure.chuanglan.strategy.PhoneDecryptStrategy;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 创蓝闪验子系统 HTTP Client。
 *
 * <p>本类封装 token 换号、本机号校验、闪验成功码、表单签名和手机号解密。短信协议与
 * 闪验协议不再共享一个大类，避免两个供应商子系统的配置和错误处理相互影响。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
public final class ChuanglanFlashClient {
    private static final Logger log = LoggerFactory.getLogger(ChuanglanFlashClient.class);

    /** 闪验成功响应码。 */
    private static final String SUCCESS_CODE = "200000";

    /** 当前支持的 AES 加密类型。 */
    private static final String AES_ENCRYPT_TYPE = "0";

    /** Map 泛型响应类型。 */
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<Map<String, Object>>() {};

    /** 创蓝配置。 */
    private final ChuanglanProperties properties;

    /** 共享 WebClient。 */
    private final WebClient webClient;

    /** 手机号解密策略。 */
    private final PhoneDecryptStrategy decryptStrategy;

    /** 闪验签名器。 */
    private final ChuanglanRequestSigner requestSigner;

    /**
     * 创建闪验子系统 Client。
     *
     * @param properties 创蓝配置
     * @param webClient 连接池化 WebClient
     * @param decryptStrategy 手机号解密策略
     * @param requestSigner 请求签名器
     */
    public ChuanglanFlashClient(
            ChuanglanProperties properties,
            WebClient webClient,
            PhoneDecryptStrategy decryptStrategy,
            ChuanglanRequestSigner requestSigner) {
        this.properties = properties;
        this.webClient = webClient;
        this.decryptStrategy = decryptStrategy;
        this.requestSigner = requestSigner;
    }

    /**
     * 使用 SDK token 置换并解密手机号。
     *
     * @param token SDK token
     * @param clientIp 客户端 IP
     * @param encryptType 加密类型
     * @param outId 调用方流水号
     * @param platform 客户端平台
     * @return 包含解密手机号的兼容响应
     */
    public Mono<Map<String, Object>> exchangeTokenForPhone(
            String token, String clientIp, String encryptType, String outId, String platform) {
        ChuanglanProperties.Flash config = properties.getFlash();
        ChuanglanProperties.FlashPlatform platformConfig = config.getPlatformConfig(platform);
        String effectiveEncryptType =
                encryptType == null || encryptType.isBlank() ? config.getEncryptType() : encryptType;
        if (!AES_ENCRYPT_TYPE.equals(effectiveEncryptType)) {
            return Mono.error(new ChuanglanApiException("UNSUPPORTED_ENCRYPT_TYPE", "当前服务仅支持AES手机号解密(encryptType=0)"));
        }
        // 使用 MultiValueMap 而非 LinkedHashMap：Spring WebFlux 的 FormHttpMessageWriter
        // 只能序列化 MultiValueMap，bodyValue(LinkedHashMap) + form-urlencoded 会抛 UnsupportedMediaTypeException
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("appId", platformConfig.getAppId());
        params.add("token", token);
        if (clientIp != null && !clientIp.isBlank()) {
            params.add("clientIp", clientIp);
        }
        params.add("encryptType", effectiveEncryptType);
        if (outId != null && !outId.isBlank()) {
            params.add("outId", outId);
        }
        // 计算签名（sign 不参与自身计算，先单独收集参数再签）
        Map<String, String> signParams = new LinkedHashMap<>();
        params.forEach((k, v) -> signParams.put(k, v.get(0)));
        String sign = requestSigner.signFlash(signParams, platformConfig.getAppKey());
        params.add("sign", sign);

        return webClient
                .post()
                .uri(baseUrl(config) + config.getTokenValidateUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(params)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .retryWhen(ChuanglanWebClientConfig.createRetry())
                .map(response ->
                        ChuanglanApiResponse.from(response).requireSuccess(SUCCESS_CODE, "闪验token置换手机号", "message"))
                .map(response -> decryptPhone(response, platformConfig.getAppKey()))
                .doOnError(error -> {
                    if (!(error instanceof ChuanglanApiException)) {
                        log.error("闪验token置换手机号请求异常", error);
                    }
                });
    }

    /**
     * 校验当前流量卡手机号。
     *
     * @param token SDK token
     * @param mobile 待校验手机号
     * @param outId 调用方流水号
     * @return 本机号校验兼容响应
     */
    public Mono<Map<String, Object>> validateMobile(String token, String mobile, String outId) {
        ChuanglanProperties.Flash config = properties.getFlash();
        ChuanglanProperties.FlashPlatform platformConfig = config.getPlatformConfig(null);
        // 使用 MultiValueMap 而非 LinkedHashMap，原因同上 exchangeTokenForPhone
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("appId", platformConfig.getAppId());
        params.add("mobile", mobile);
        params.add("token", token);
        if (outId != null && !outId.isBlank()) {
            params.add("outId", outId);
        }
        Map<String, String> signParams = new LinkedHashMap<>();
        params.forEach((k, v) -> signParams.put(k, v.get(0)));
        String sign = requestSigner.signFlash(signParams, platformConfig.getAppKey());
        params.add("sign", sign);

        return webClient
                .post()
                .uri(baseUrl(config) + config.getMobileValidateUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(params)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .retryWhen(ChuanglanWebClientConfig.createRetry())
                .map(response -> ChuanglanApiResponse.from(response).requireSuccess(SUCCESS_CODE, "本机号校验", "message"))
                .doOnSuccess(response -> log.info("本机号校验成功, code: {}", response.get("code")))
                .doOnError(error -> {
                    if (!(error instanceof ChuanglanApiException)) {
                        log.error("本机号校验请求异常", error);
                    }
                });
    }

    /**
     * 解密响应 data.mobileName 并写入兼容 phone 字段。
     *
     * @param response 已通过成功码校验的响应
     * @param appKey 当前平台密钥
     * @return 原响应
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> decryptPhone(Map<String, Object> response, String appKey) {
        Object dataValue = response.get("data");
        if (!(dataValue instanceof Map<?, ?>)) {
            return response;
        }
        Map<String, Object> data = (Map<String, Object>) dataValue;
        Object encrypted = data.get("mobileName");
        if (encrypted != null) {
            String value = String.valueOf(encrypted);
            if (!"null".equals(value) && !value.isBlank()) {
                data.put("phone", decryptStrategy.decrypt(value, appKey));
            }
        }
        return response;
    }

    /**
     * 构建闪验 API 基础地址。
     *
     * @param config 闪验配置
     * @return 包含端口的基础地址
     */
    private String baseUrl(ChuanglanProperties.Flash config) {
        String protocol = config.isSslEnabled() ? "https" : "http";
        return protocol + "://" + config.getHost() + ":" + config.getPort();
    }
}
