package com.apex.scaffold.integration.connector.domain.connector;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.domain.IntegrationConnector;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * webhook 出站连接器（方案 M5 / 批次 E2）。
 *
 * <p>向目标 URL POST JSON 载荷，携带 HMAC-SHA256 签名头（{@code X-Signature}，
 * 算法 {@code HMAC-SHA256(secret, timestamp + "." + body)} 十六进制）与时间戳头
 * （{@code X-Timestamp}，epoch 秒）。签名密钥经出站门面由凭据引用解密后以
 * {@code secret} 注入配置。
 *
 * <p>配置：{@code url}（必填）、{@code secret}（必填）、{@code headers}（附加头）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
public class WebhookOutboundConnector implements IntegrationConnector {

    /** 连接器类型标识。 */
    public static final String TYPE = "WEBHOOK";

    private static final String URL_KEY = "url";
    private static final String SECRET_KEY = "secret";
    private static final String HEADERS_KEY = "headers";

    /** 签名头。 */
    public static final String SIGNATURE_HEADER = "X-Signature";

    /** 时间戳头（epoch 秒）。 */
    public static final String TIMESTAMP_HEADER = "X-Timestamp";

    private final WebClient webClient;

    /** SPI 无参构造（自持默认 WebClient）。 */
    public WebhookOutboundConnector() {
        this(WebClient.create());
    }

    /**
     * 测试/装配构造。
     *
     * @param webClient 底层 HTTP 客户端
     */
    public WebhookOutboundConnector(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Mono<Void> healthCheck(Map<String, Object> config) {
        if (string(config, URL_KEY) == null || string(config, SECRET_KEY) == null) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "WEBHOOK 连接器缺少 url 或 secret"));
        }
        return Mono.empty();
    }

    @Override
    public Mono<Map<String, Object>> execute(
            Map<String, Object> config, String operation, Map<String, Object> payload, String idempotencyKey) {
        String url = string(config, URL_KEY);
        String secret = string(config, SECRET_KEY);
        if (url == null || url.isBlank() || secret == null || secret.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "WEBHOOK 连接器缺少 url 或 secret"));
        }
        String body = toJson(payload == null ? Map.of() : payload);
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = WebhookSignatures.hmacSha256Hex(secret, timestamp + "." + body);
        return webClient
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header(TIMESTAMP_HEADER, timestamp)
                .header(SIGNATURE_HEADER, signature)
                .headers(h -> {
                    if (config.get(HEADERS_KEY) instanceof Map<?, ?> extra) {
                        extra.forEach((name, value) -> h.add(String.valueOf(name), String.valueOf(value)));
                    }
                })
                .exchangeToMono(response -> response.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(responseBody -> {
                            Map<String, Object> envelope = new HashMap<>(2);
                            envelope.put("status", response.statusCode().value());
                            envelope.put("response", responseBody);
                            return envelope;
                        }))
                .onErrorMap(
                        WebClientResponseException.class,
                        e -> new BusinessException(
                                ResultCode.DEGRADE,
                                "webhook 目标端响应异常: " + e.getStatusCode().value()))
                .doOnNext(envelope -> log.debug("webhook 出站完成: url={}, status={}", url, envelope.get("status")));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "webhook 载荷序列化失败");
        }
    }

    private String string(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
