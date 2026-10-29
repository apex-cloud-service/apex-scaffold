package com.apex.scaffold.infrastructure.remote.logging;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * API 调用观测脱敏、指标记录及异步写入服务。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Service
@Slf4j
public class ApiCallLogService {
    private static final Set<String> SENSITIVE_HEADER_NAMES = Set.of(
            "authorization",
            "api-key",
            "api_key",
            "apikey",
            "secret",
            "x-api-key",
            "x-auth-token",
            "token",
            "password",
            "credential",
            "sign",
            "signature",
            "x-qa-hmac-signature");
    private static final Set<String> SENSITIVE_QUERY_PARAMS =
            new LinkedHashSet<>(Arrays.asList("secret", "access_token", "appid"));
    private static final String MASKED_VALUE = "***";
    private static final Pattern SENSITIVE_KEY_PATTERN = buildSensitivePattern();
    private final ApiCallLogProperties properties;
    private final ApiCallLogSink repository;
    private final MeterRegistry meterRegistry;

    /**
     * 装配可选的服务专属写入适配；无适配时仍执行脱敏和指标统计。
     *
     * @param properties 配置属性
     * @param repository 服务专属调用观测写入适配
     * @param meterRegistry 指标注册表
     */
    public ApiCallLogService(
            ApiCallLogProperties properties, ObjectProvider<ApiCallLogSink> repository, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.repository = repository.getIfAvailable(() -> observation -> Mono.empty());
        this.meterRegistry = meterRegistry;
    }

    /**
     * 脱敏调用数据并异步提交写入。
     *
     * @param entity 待记录的调用观测数据
     * @return 写入完成信号
     */
    public Mono<Void> log(ApiCallObservation entity) {
        if (!properties.isEnabled() || entity == null) {
            return Mono.empty();
        }
        entity.setRequestHeaders(sanitizeHeaders(entity.getRequestHeaders()));
        entity.setResponseHeaders(sanitizeHeaders(entity.getResponseHeaders()));
        entity.setRequestBody(truncate(entity.getRequestBody()));
        entity.setUrl(sanitizeUrl(entity.getUrl()));
        if (!properties.isLogResponseBody()) {
            entity.setResponseBody(null);
        } else {
            entity.setResponseBody(truncate(entity.getResponseBody()));
        }
        recordApiCallMetrics(entity);
        return repository.save(entity).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 记录调用耗时与结果指标。
     *
     * @param entity 调用观测数据
     */
    private void recordApiCallMetrics(ApiCallObservation entity) {
        String service = entity.getServiceName() != null ? entity.getServiceName() : "unknown";
        if (entity.getExecutionTime() != null) {
            meterRegistry
                    .timer("apex.api.call", "service", service)
                    .record(Duration.ofMillis(entity.getExecutionTime()));
        }
        meterRegistry
                .counter("apex.api.call.result", "service", service, "result", resolveResult(entity.getStatus()))
                .increment();
    }

    /**
     * 将调用状态映射为结果标签。
     *
     * @param status 调用状态
     * @return 结果标签
     */
    private String resolveResult(Integer status) {
        if (status == null) {
            return "unknown";
        }
        return switch (status) {
            case 0 -> "success";
            case 1 -> "client_error";
            case 2 -> "server_error";
            case 3 -> "exception";
            default -> "unknown";
        };
    }

    /**
     * 脱敏请求和响应头中的敏感字段。
     *
     * @param headers 原始头信息
     * @return 脱敏后的头信息
     */
    private String sanitizeHeaders(String headers) {
        if (headers == null || headers.isBlank()) {
            return headers;
        }
        return SENSITIVE_KEY_PATTERN.matcher(headers).replaceAll("\"$1\":\"" + MASKED_VALUE + "\"");
    }

    /**
     * 脱敏 URL 查询参数。
     *
     * @param url 原始 URL
     * @return 脱敏后的 URL
     */
    String sanitizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        try {
            URI uri = new URI(url);
            String rawQuery = uri.getRawQuery();
            if (rawQuery == null || rawQuery.isBlank()) {
                return url;
            }
            UriComponentsBuilder builder = UriComponentsBuilder.newInstance();
            String[] params = rawQuery.split("&");
            boolean anyMasked = false;
            for (String param : params) {
                int eqIdx = param.indexOf('=');
                if (eqIdx > 0) {
                    String key = param.substring(0, eqIdx);
                    if (isSensitiveQueryParam(key)) {
                        builder.queryParam(key, MASKED_VALUE);
                        anyMasked = true;
                    } else {
                        String value = param.substring(eqIdx + 1);
                        builder.queryParam(key, value);
                    }
                } else if (!param.isBlank()) {
                    builder.queryParam(param, "");
                }
            }
            if (!anyMasked) {
                return url;
            }
            String sanitizedQuery = builder.build().encode().toUri().getRawQuery();
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), sanitizedQuery, uri.getFragment())
                    .toASCIIString();
        } catch (URISyntaxException e) {
            log.trace("sanitizeUrl: 无法解析 URL, 返回原值: {}", url);
            return url;
        }
    }

    /**
     * 判断查询参数是否需要脱敏。
     *
     * @param paramName 参数名
     * @return 是否需要脱敏
     */
    private boolean isSensitiveQueryParam(String paramName) {
        if (paramName == null) {
            return false;
        }
        return SENSITIVE_QUERY_PARAMS.stream().anyMatch(paramName::equalsIgnoreCase);
    }

    /**
     * 按配置截断字符串。
     *
     * @param value 原始内容
     * @return 截断后的内容
     */
    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        int maxLen = properties.getMaxBodyLength();
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...[truncated]";
    }

    /**
     * 构建敏感头字段匹配模式。
     *
     * @return 匹配模式
     */
    private static Pattern buildSensitivePattern() {
        String fieldNames = SENSITIVE_HEADER_NAMES.stream()
                .map(name -> name.replaceAll("([.$|{}()\\[\\]^*+?\\\\])", "\\\\$1"))
                .collect(Collectors.joining("|"));
        return Pattern.compile("\"(" + fieldNames + ")\":\"(?:[^\"]*)\"", Pattern.CASE_INSENSITIVE);
    }
}
