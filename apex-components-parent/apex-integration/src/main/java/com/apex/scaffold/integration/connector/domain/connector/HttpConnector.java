package com.apex.scaffold.integration.connector.domain.connector;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.domain.IntegrationConnector;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

/**
 * 通用 REST 连接器（方案 M5 / 批次 E2）。
 *
 * <p>经 WebClient（原生异步，无阻塞点）调用任意 REST 端点。认证配置由出站门面
 * 解析 {@code int_connector_config.config_value} JSON 并将凭据引用替换为明文后传入：
 * <pre>{@code
 * {
 *   "baseUrl": "https://api.example.com",
 *   "method": "POST",            // operation 非 HTTP 方法语义时的默认方法
 *   "path": "/v1/notify",        // operation 非 HTTP 方法语义时的默认路径
 *   "headers": {"X-Custom": "v"},
 *   "timeoutMs": 5000,
 *   "healthPath": "/health",
 *   "auth": {                    // type: none/basic/bearer/header
 *     "type": "bearer",
 *     "credentialRef": "gh-token"   // 门面解密后注入 "secret"（basic 另含 username）
 *   }
 * }
 * }</pre>
 *
 * <p>operation 支持 {@code "POST /orders/1"} 形式（HTTP 方法 + 路径），否则回退配置的
 * method/path。GET/DELETE 将一维 payload 拍平为查询参数，POST/PUT/PATCH 以 JSON 体发送。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
public class HttpConnector implements IntegrationConnector {

    /** 连接器类型标识。 */
    public static final String TYPE = "HTTP";

    private static final String AUTH_NONE = "none";
    private static final String AUTH_BASIC = "basic";
    private static final String AUTH_BEARER = "bearer";
    private static final String AUTH_HEADER = "header";
    private static final int HTTP_ERROR_STATUS = 400;
    private static final String JSON_OBJECT_PREFIX = "{";

    private static final List<String> HTTP_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD");

    private final WebClient webClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** SPI 无参构造（自持默认 WebClient）。 */
    public HttpConnector() {
        this(WebClient.create());
    }

    /**
     * 测试/装配构造。
     *
     * @param webClient 底层 HTTP 客户端
     */
    public HttpConnector(WebClient webClient) {
        this.webClient = webClient;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Mono<Void> healthCheck(Map<String, Object> config) {
        String baseUrl = string(config, "baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "HTTP 连接器缺少 baseUrl"));
        }
        return request(config, "GET", string(config, "healthPath"), Map.of()).then();
    }

    @Override
    public Mono<Map<String, Object>> execute(
            Map<String, Object> config, String operation, Map<String, Object> payload, String idempotencyKey) {
        String method;
        String path;
        String normalized = operation == null ? "" : operation.trim().toUpperCase(java.util.Locale.ROOT);
        int spaceIndex = normalized.indexOf(' ');
        if (spaceIndex > 0 && HTTP_METHODS.contains(normalized.substring(0, spaceIndex))) {
            method = normalized.substring(0, spaceIndex);
            path = operation.trim().substring(spaceIndex).trim();
        } else {
            method = string(config, "method");
            path = string(config, "path");
        }
        if (method == null || method.isBlank()) {
            method = "GET";
        }
        return request(config, method.toUpperCase(java.util.Locale.ROOT), path, payload);
    }

    private Mono<Map<String, Object>> request(
            Map<String, Object> config, String method, String path, Map<String, Object> payload) {
        String baseUrl = string(config, "baseUrl");
        if (baseUrl == null || baseUrl.isBlank()) {
            return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "HTTP 连接器缺少 baseUrl"));
        }
        HttpMethod httpMethod = HttpMethod.valueOf(method);
        boolean queryPayload = (payload != null && !payload.isEmpty()) && HttpMethod.GET.equals(httpMethod);
        String url = buildUrl(baseUrl, path, queryPayload ? payload : Map.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> auth = config.get("auth") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        @SuppressWarnings("unchecked")
        Map<String, Object> headers =
                config.get("headers") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();

        WebClient.RequestBodySpec spec = webClient
                .method(httpMethod)
                .uri(url)
                .headers(h -> headers.forEach((name, value) -> h.add(name, String.valueOf(value))))
                .headers(h -> applyAuth(h, auth));

        Mono<ClientBody> exchange;
        if (queryPayload) {
            exchange = spec.exchangeToMono(response -> response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(body -> new ClientBody(response.statusCode().value(), body)));
        } else {
            exchange = spec.contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(payload == null ? Map.of() : payload)
                    .exchangeToMono(response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .map(body -> new ClientBody(response.statusCode().value(), body)));
        }

        Mono<ClientBody> call = exchange.timeout(timeout(config))
                .onErrorMap(
                        WebClientResponseException.class,
                        e -> new BusinessException(
                                ResultCode.DEGRADE,
                                "HTTP 端点响应异常: " + e.getStatusCode().value()))
                .onErrorMap(
                        java.util.concurrent.TimeoutException.class,
                        e -> new BusinessException(ResultCode.REQUEST_TIMEOUT, "HTTP 端点响应超时"))
                .doOnError(e -> log.debug("HTTP 连接器调用失败: url={}, method={}", url, method));
        return call.flatMap(this::toPayload);
    }

    private String buildUrl(String baseUrl, String path, Map<String, Object> query) {
        StringBuilder url = new StringBuilder(joinUrl(baseUrl, path));
        if (!query.isEmpty()) {
            java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
            StringJoiner joiner = new StringJoiner("&");
            query.forEach((name, value) ->
                    joiner.add(URLEncoder.encode(name, utf8) + "=" + URLEncoder.encode(String.valueOf(value), utf8)));
            url.append(url.indexOf("?") >= 0 ? "&" : "?").append(joiner);
        }
        return url.toString();
    }

    private void applyAuth(org.springframework.http.HttpHeaders headers, Map<String, Object> auth) {
        String type = string(auth, "type");
        if (type == null || AUTH_NONE.equalsIgnoreCase(type)) {
            return;
        }
        String secret = string(auth, "secret");
        String normalizedType = type.toLowerCase(java.util.Locale.ROOT);
        if (AUTH_BASIC.equals(normalizedType)) {
            headers.setBasicAuth(
                    string(auth, "username") == null ? "" : string(auth, "username"), secret == null ? "" : secret);
        } else if (AUTH_BEARER.equals(normalizedType)) {
            headers.setBearerAuth(secret == null ? "" : secret);
        } else if (AUTH_HEADER.equals(normalizedType)) {
            headers.add(
                    string(auth, "headerName") == null ? "X-API-Key" : string(auth, "headerName"),
                    secret == null ? "" : secret);
        } else {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的认证类型: " + type);
        }
    }

    private Mono<Map<String, Object>> toPayload(ClientBody body) {
        if (body.status() >= HTTP_ERROR_STATUS) {
            return Mono.error(new BusinessException(ResultCode.DEGRADE, "HTTP 端点返回错误状态: " + body.status()));
        }
        String raw = body.body();
        if (raw != null && !raw.isBlank() && raw.trim().startsWith(JSON_OBJECT_PREFIX)) {
            try {
                return Mono.just(objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {}));
            } catch (Exception ignored) {
                // 非 JSON 对象落兜底包络
            }
        }
        Map<String, Object> envelope = new HashMap<>(2);
        envelope.put("status", body.status());
        envelope.put("body", raw == null ? "" : raw);
        return Mono.just(envelope);
    }

    private Duration timeout(Map<String, Object> config) {
        Object timeoutMs = config.get("timeoutMs");
        return timeoutMs instanceof Number number && number.longValue() > 0
                ? Duration.ofMillis(number.longValue())
                : Duration.ofSeconds(10);
    }

    private String joinUrl(String baseUrl, String path) {
        if (path == null || path.isBlank()) {
            return baseUrl;
        }
        return baseUrl.endsWith("/") && path.startsWith("/")
                ? baseUrl + path.substring(1)
                : baseUrl + (path.startsWith("/") || baseUrl.endsWith("/") ? path : "/" + path);
    }

    private String string(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private record ClientBody(int status, String body) {}
}
