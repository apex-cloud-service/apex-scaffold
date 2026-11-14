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
 * 微信连接器（方案 M5 / 批次 E2）。
 *
 * <p>直接以 WebClient 实现微信开放平台 HTTP 调用，不依赖
 * {@code apex-infrastructure-integration} 的 SDK 客户端；
 * {@code ChuanglanGateway} / {@code WeChatGateway} 契约保持不动。
 *
 * <p>配置：{@code baseUrl}（默认 https://api.weixin.qq.com）、{@code token}
 * （access_token，出站门面经凭据引用解密注入）、{@code timeoutMs}。
 * 操作：{@code sendMessage}（客服消息）、{@code sendTemplate}（模板消息）；
 * payload 即微信接口 JSON 体（touser / msgtype / content 等）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
public class WeChatConnector implements IntegrationConnector {

    /** 连接器类型标识。 */
    public static final String TYPE = "WECHAT";

    private static final String DEFAULT_BASE_URL = "https://api.weixin.qq.com";
    private static final String SEND_URI = "/cgi-bin/message/custom/send";
    private static final String TEMPLATE_SEND_URI = "/cgi-bin/message/template/send";
    private static final String HEALTH_URI = "/cgi-bin/getcallbackip";

    private final WebClient webClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** SPI 无参构造（自持默认 WebClient）。 */
    public WeChatConnector() {
        this(WebClient.create());
    }

    /**
     * 测试/装配构造。
     *
     * @param webClient 底层 HTTP 客户端
     */
    public WeChatConnector(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Mono<Void> healthCheck(Map<String, Object> config) {
        String token = string(config, "token");
        if (token == null || token.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "WECHAT 连接器缺少 access_token"));
        }
        return call(config, "GET", HEALTH_URI, token, Map.of())
                .flatMap(body -> !"0".equals(trimCode(body.get("errcode")))
                        ? Mono.error(new BusinessException(
                                ResultCode.WX_API_ERROR, "微信连通性检查失败: errcode=" + body.get("errcode")))
                        : Mono.<Void>empty());
    }

    @Override
    public Mono<Map<String, Object>> execute(
            Map<String, Object> config, String operation, Map<String, Object> payload, String idempotencyKey) {
        String token = string(config, "token");
        if (token == null || token.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "WECHAT 连接器缺少 access_token"));
        }
        String uri =
                switch (operation == null ? "" : operation) {
                    case "sendMessage" -> SEND_URI;
                    case "sendTemplate" -> TEMPLATE_SEND_URI;
                    default -> throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的微信操作: " + operation);
                };
        return call(config, "POST", uri, token, payload == null ? Map.of() : payload)
                .flatMap(body -> {
                    if (body.containsKey("errcode") && !"0".equals(trimCode(body.get("errcode")))) {
                        return Mono.error(
                                new BusinessException(ResultCode.WX_API_ERROR, "微信接口调用失败: " + body.get("errmsg")));
                    }
                    return Mono.just(body);
                });
    }

    private Mono<Map<String, Object>> call(
            Map<String, Object> config, String method, String uri, String token, Map<String, Object> payload) {
        String baseUrl = string(config, "baseUrl");
        String url = (baseUrl == null || baseUrl.isBlank() ? DEFAULT_BASE_URL : baseUrl) + uri;
        WebClient.RequestBodySpec spec = webClient
                .method(org.springframework.http.HttpMethod.valueOf(method))
                .uri(url + (url.contains("?") ? "&" : "?") + "access_token=" + token);
        Mono<String> bodyMono = "GET".equals(method)
                ? spec.exchangeToMono(
                        response -> response.bodyToMono(String.class).defaultIfEmpty(""))
                : spec.contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(payload)
                        .exchangeToMono(
                                response -> response.bodyToMono(String.class).defaultIfEmpty(""));
        return bodyMono.timeout(timeout(config))
                .onErrorMap(
                        WebClientResponseException.class,
                        e -> new BusinessException(
                                ResultCode.WX_API_ERROR,
                                "微信端响应异常: " + e.getStatusCode().value()))
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
            Map<String, Object> envelope = new HashMap<>();
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
