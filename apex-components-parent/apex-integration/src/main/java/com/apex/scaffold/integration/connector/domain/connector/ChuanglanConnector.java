package com.apex.scaffold.integration.connector.domain.connector;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.domain.IntegrationConnector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * 创蓝 253 短信连接器（方案 M5 / 批次 E2）。
 *
 * <p>直接以 WebClient 实现创蓝云通信平台 HTTP 调用（JSON 版 v2 接口），不依赖
 * {@code apex-infrastructure-integration} 的 SDK 客户端；
 * {@code ChuanglanGateway} 契约保持不动。
 *
 * <p>配置：{@code baseUrl}（默认 https://smssh1.253.com）、{@code sendPath}
 * （默认 /msg/v2/send/json）、{@code balancePath}（默认 /msg/balance/json）、
 * {@code account} 与 {@code password}（出站门面经凭据引用解密注入）、{@code timeoutMs}。
 * 操作：
 * <ul>
 *   <li>{@code sendSms}：payload {@code {phone, msg}}（v2 模板接口亦支持
 *       {@code {phone, templateId, params}}，原样透传）；</li>
 *   <li>{@code queryBalance}：查询账户余额。</li>
 * </ul>
 * 创蓝应答 {@code code == "0"} 视为成功，其余判为业务失败。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
public class ChuanglanConnector implements IntegrationConnector {

    /** 连接器类型标识。 */
    public static final String TYPE = "CHUANGLAN";

    private static final String OPERATION_QUERY_BALANCE = "queryBalance";
    private static final String OPERATION_SEND_SMS = "sendSms";
    private static final String ACCOUNT_KEY = "account";
    private static final String PASSWORD_KEY = "password";
    private static final String PHONE_KEY = "phone";
    private static final String TEMPLATE_ID_KEY = "templateId";
    private static final String PARAMS_KEY = "params";

    private static final String DEFAULT_BASE_URL = "https://smssh1.253.com";
    private static final String DEFAULT_SEND_PATH = "/msg/v2/send/json";
    private static final String DEFAULT_BALANCE_PATH = "/msg/balance/json";
    private static final String SUCCESS_CODE = "0";

    private final WebClient webClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** SPI 无参构造（自持默认 WebClient）。 */
    public ChuanglanConnector() {
        this(WebClient.create());
    }

    /**
     * 测试/装配构造。
     *
     * @param webClient 底层 HTTP 客户端
     */
    public ChuanglanConnector(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Mono<Void> healthCheck(Map<String, Object> config) {
        return queryBalance(config)
                .flatMap(body -> SUCCESS_CODE.equals(trimCode(body.get("code")))
                        ? Mono.<Void>empty()
                        : Mono.error(new BusinessException(
                                ResultCode.SMS_SERVICE_ERROR, "创蓝连通性检查失败: code=" + body.get("code"))));
    }

    @Override
    public Mono<Map<String, Object>> execute(
            Map<String, Object> config, String operation, Map<String, Object> payload, String idempotencyKey) {
        Map<String, Object> body = new HashMap<>(7);
        body.put(ACCOUNT_KEY, string(config, ACCOUNT_KEY));
        body.put(PASSWORD_KEY, string(config, PASSWORD_KEY));
        if (OPERATION_QUERY_BALANCE.equals(operation)) {
            return post(
                    config,
                    string(config, "balancePath") == null ? DEFAULT_BALANCE_PATH : string(config, "balancePath"),
                    body);
        }
        if (OPERATION_SEND_SMS.equals(operation)) {
            if (payload == null || string(payload, PHONE_KEY) == null) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "sendSms 缺少 phone"));
            }
            body.put(PHONE_KEY, string(payload, PHONE_KEY));
            String msg = string(payload, "msg");
            if (msg != null) {
                body.put("msg", msg);
            }
            if (payload.get(TEMPLATE_ID_KEY) != null) {
                body.put(TEMPLATE_ID_KEY, payload.get(TEMPLATE_ID_KEY));
            }
            if (payload.get(PARAMS_KEY) != null) {
                body.put(PARAMS_KEY, payload.get(PARAMS_KEY));
            }
            body.put("report", "true");
            return post(
                            config,
                            string(config, "sendPath") == null ? DEFAULT_SEND_PATH : string(config, "sendPath"),
                            body)
                    .flatMap(response -> SUCCESS_CODE.equals(trimCode(response.get("code")))
                            ? Mono.just(response)
                            : Mono.error(new BusinessException(
                                    ResultCode.SMS_SERVICE_ERROR,
                                    "创蓝短信发送失败: code=" + response.get("code") + ", msg=" + response.get("msg"))));
        }
        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "不支持的创蓝操作: " + operation));
    }

    private Mono<Map<String, Object>> queryBalance(Map<String, Object> config) {
        if (string(config, ACCOUNT_KEY) == null || string(config, PASSWORD_KEY) == null) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "CHUANGLAN 连接器缺少 account/password"));
        }
        Map<String, Object> body = new HashMap<>(2);
        body.put(ACCOUNT_KEY, string(config, ACCOUNT_KEY));
        body.put(PASSWORD_KEY, string(config, PASSWORD_KEY));
        return post(
                config,
                string(config, "balancePath") == null ? DEFAULT_BALANCE_PATH : string(config, "balancePath"),
                body);
    }

    private Mono<Map<String, Object>> post(Map<String, Object> config, String path, Map<String, Object> body) {
        String baseUrl = string(config, "baseUrl");
        String url = (baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl) + path;
        return webClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .exchangeToMono(response -> response.bodyToMono(String.class).defaultIfEmpty(""))
                .timeout(timeout(config))
                .onErrorMap(
                        WebClientResponseException.class,
                        e -> new BusinessException(
                                ResultCode.SMS_SERVICE_ERROR,
                                "创蓝端响应异常: " + e.getStatusCode().value()))
                .flatMap(this::parse);
    }

    private Mono<Map<String, Object>> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Mono.just(Map.of());
        }
        try {
            Map<String, Object> body = objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {});
            return Mono.just(body == null ? Map.of() : body);
        } catch (Exception e) {
            Map<String, Object> envelope = new HashMap<>(1);
            envelope.put("body", raw);
            return Mono.just(envelope);
        }
    }

    private Duration timeout(Map<String, Object> config) {
        Object timeoutMs = config.get("timeoutMs");
        return timeoutMs instanceof Number number && number.longValue() > 0
                ? Duration.ofMillis(number.longValue())
                : Duration.ofSeconds(10);
    }

    private String trimCode(Object code) {
        return code == null ? null : String.valueOf(code).trim();
    }

    private String string(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
