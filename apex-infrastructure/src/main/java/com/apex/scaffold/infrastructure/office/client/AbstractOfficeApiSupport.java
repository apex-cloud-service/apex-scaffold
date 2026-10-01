package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 办公平台 API 调用支持类。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
public abstract class AbstractOfficeApiSupport {
    private static final String SUCCESS_CODE = "0";
    private final Duration timeout;
    protected final WebClient webClient;
    protected final OfficePlatformProperties.Platform properties;

    protected AbstractOfficeApiSupport(
            WebClient.Builder builder,
            OfficePlatformProperties platformProperties,
            com.apex.scaffold.domain.office.OfficePlatform platform,
            String baseUrl) {
        this.timeout = platformProperties.getTimeout();
        this.webClient = builder.baseUrl(baseUrl).build();
        this.properties =
                platformProperties.getPlatforms().getOrDefault(platform, new OfficePlatformProperties.Platform());
    }

    protected Mono<Map<String, Object>> getAccessToken(String path, Map<String, String> params) {
        return webClient
                .get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path(path);
                    params.forEach(builder::queryParam);
                    return builder.build();
                })
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(this::checkedBody);
    }

    protected Mono<Map<String, Object>> postJson(String path, Map<String, Object> body) {
        return postJson(path, body, Map.of());
    }

    protected Mono<Map<String, Object>> postJson(String path, Map<String, Object> body, Map<String, String> headers) {
        return webClient
                .post()
                .uri(path)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(this::checkedBody);
    }

    protected Mono<Map<String, Object>> getJson(String path, Map<String, String> params) {
        return getAccessToken(path, params);
    }

    protected Mono<Map<String, Object>> getJsonWithHeaders(String path, Map<String, String> headers) {
        return webClient
                .get()
                .uri(path)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(this::checkedBody);
    }

    protected Mono<Map<String, Object>> deleteWithHeaders(String path, Map<String, String> headers) {
        return webClient
                .delete()
                .uri(path)
                .headers(httpHeaders -> headers.forEach(httpHeaders::set))
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(timeout)
                .map(this::checkedBody);
    }

    protected <T> Mono<T> call(Function<Map<String, Object>, T> mapper, Mono<Map<String, Object>> request) {
        return request.map(mapper).onErrorMap(this::mapError);
    }

    protected String secret(String name) {
        String reference = required(properties.getSecretReference(), name + " secret reference");
        return required(System.getenv(reference), name + " secret");
    }

    protected String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, name + "未配置");
        }
        return value;
    }

    protected String text(Object value, String name) {
        return required(value == null ? null : value.toString(), name);
    }

    private Map<String, Object> checkedBody(Map<String, Object> body) {
        Object code = body.get("code");
        if (code == null) {
            code = body.get("errcode");
        }
        if (code != null && !SUCCESS_CODE.equals(code.toString())) {
            throw new BusinessException(
                    ResultCode.BUSINESS_ERROR,
                    "办公平台 API 调用失败：" + code + " " + body.getOrDefault("msg", body.get("errmsg")));
        }
        return body;
    }

    private Throwable mapError(Throwable error) {
        if (error instanceof BusinessException) {
            return error;
        }
        return new BusinessException(ResultCode.BUSINESS_ERROR, "办公平台 API 调用失败：" + error.getMessage());
    }
}
