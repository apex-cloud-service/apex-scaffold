package com.apex.scaffold.infrastructure.chuanglan.service;

import com.apex.scaffold.common.util.SensitiveDataUtil;
import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanProperties;
import com.apex.scaffold.infrastructure.chuanglan.config.ChuanglanWebClientConfig;
import com.apex.scaffold.infrastructure.chuanglan.exception.ChuanglanApiException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 创蓝短信子系统 HTTP Client。
 *
 * <p>本类只负责短信发送和余额查询，封装短信 host、JSON 协议、短信专用成功码与重试。
 * 对外兼容 Facade 由 {@link ChuanglanApiService} 提供，调用者无需直接依赖该 Client。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 3.3.0
 */
@Slf4j
public final class ChuanglanSmsClient {

    /** 短信 v2 模板发送成功码。 */
    private static final String SEND_SUCCESS_CODE = "000000";

    /** 短信余额查询成功码。 */
    private static final String BALANCE_SUCCESS_CODE = "0";

    /** Map 泛型响应类型。 */
    private static final ParameterizedTypeReference<Map<String, Object>> MAP_TYPE =
            new ParameterizedTypeReference<Map<String, Object>>() {};

    /** 创蓝整体配置。 */
    private final ChuanglanProperties properties;

    /** 共享连接池 WebClient。 */
    private final WebClient webClient;

    /** 短信参数与签名构建器。 */
    private final ChuanglanRequestSigner requestSigner;

    /**
     * 创建短信子系统 Client。
     *
     * @param properties 创蓝配置
     * @param webClient 连接池化 WebClient
     * @param requestSigner 请求签名与 nonce 构建器
     */
    public ChuanglanSmsClient(
            ChuanglanProperties properties, WebClient webClient, ChuanglanRequestSigner requestSigner) {
        this.properties = properties;
        this.webClient = webClient;
        this.requestSigner = requestSigner;
    }

    /**
     * 使用短信 v2 模板接口发送验证码。
     *
     * @param phone 接收手机号
     * @param code 模板验证码
     * @return 原始兼容响应
     */
    public Mono<Map<String, Object>> sendSms(String phone, String code) {
        ChuanglanProperties.Sms config = properties.getSms();
        Map<String, String> body = new LinkedHashMap<>();
        body.put("account", config.getAccount());
        body.put("password", config.getPassword());
        body.put("nonce", requestSigner.nonce());
        body.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));
        body.put("phoneNumbers", phone);
        body.put("templateId", config.getTemplateId());
        body.put("templateParamJson", requestSigner.buildTemplateParams(code));
        body.put("signature", config.getSignature());
        body.put("report", "true");
        log.debug(
                "短信v2请求已构建, accountFingerprint: {}, phoneCount: 1, templateId: {}",
                SensitiveDataUtil.fingerprint(config.getAccount()),
                config.getTemplateId());

        return webClient
                .post()
                .uri(baseUrl(config) + config.getSendUri())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .retryWhen(ChuanglanWebClientConfig.createRetry())
                .map(response ->
                        ChuanglanApiResponse.from(response).requireSuccess(SEND_SUCCESS_CODE, "短信发送", "errorMsg"))
                .doOnSuccess(response -> log.debug("发送短信成功, msgId: {}", response.get("msgId")))
                .doOnError(error -> {
                    if (!(error instanceof ChuanglanApiException)) {
                        log.error("发送短信请求异常", error);
                    }
                });
    }

    /**
     * 查询短信账户余额。
     *
     * @return 原始兼容余额响应
     */
    public Mono<Map<String, Object>> queryBalance() {
        ChuanglanProperties.Sms config = properties.getSms();
        return webClient
                .post()
                .uri(baseUrl(config) + config.getBalanceUri())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("account", config.getAccount(), "password", config.getPassword()))
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .retryWhen(ChuanglanWebClientConfig.createRetry())
                .map(response ->
                        ChuanglanApiResponse.from(response).requireSuccess(BALANCE_SUCCESS_CODE, "查询余额", "errorMsg"))
                .doOnSuccess(response -> log.debug("查询余额成功, balance: {}", response.get("balance")))
                .doOnError(error -> {
                    if (!(error instanceof ChuanglanApiException)) {
                        log.error("查询余额请求异常", error);
                    }
                });
    }

    /**
     * 构建短信 API 基础地址。
     *
     * @param config 短信配置
     * @return HTTP 或 HTTPS 基础地址
     */
    private String baseUrl(ChuanglanProperties.Sms config) {
        return (config.isSslEnabled() ? "https" : "http") + "://" + config.getHost();
    }
}
