package com.apex.scaffold.infrastructure.remote.logging;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.ApiCallLogEntity;
import com.apex.scaffold.domain.repository.ApiCallLogRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

/**
 * API 调用日志异步写入服务。
 *
 * <p>负责对外部 HTTP 调用日志进行脱敏处理和异步持久化。核心职责：
 * <ul>
 *   <li>请求/响应头中的敏感字段脱敏（Authorization、Api-Key、Secret 等）</li>
 *   <li>超长请求/响应体截断（受 {@link ApiCallLogProperties#getMaxBodyLength()} 控制）</li>
 *   <li>异步写入数据库（通过 {@link ApiCallLogRepository#save(ApiCallLogEntity)}）</li>
 * </ul>
 *
 * <p>该服务采用"发后即忘"（fire-and-forget）模式：调用方通过 {@link #log(ApiCallLogEntity)}
 * 提交日志实体后立即返回，数据库写入在 boundedElastic 调度器上异步执行。
 * 写入失败不会阻塞或中断主业务流程。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ApiCallLogProperties
 * @see ApiCallLogRepository
 */
@Service
public class ApiCallLogService {
    private static final Logger log = LoggerFactory.getLogger(ApiCallLogService.class);

    /**
     * 常见的请求/响应头敏感字段名称（小写）。
     * 这些字段的值在写入日志前会被替换为 {@code "***"}。
     */
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

    /**
     * URL 查询参数中需要脱敏的敏感参数名。
     * 这些参数的值在写入日志前会被替换为 {@code "***"}。
     */
    private static final Set<String> SENSITIVE_QUERY_PARAMS =
            new LinkedHashSet<>(Arrays.asList("secret", "access_token", "appid"));

    /** 敏感字段值脱敏后的占位符。 */
    private static final String MASKED_VALUE = "***";

    /** 脱敏键值对匹配模式：敏感字段名后跟冒号空格值。 */
    private static final Pattern SENSITIVE_KEY_PATTERN = buildSensitivePattern();

    /** 配置属性。 */
    private final ApiCallLogProperties properties;

    /** API 调用日志仓储。 */
    private final ApiCallLogRepository repository;

    /** 指标注册表。 */
    private final MeterRegistry meterRegistry;

    /**
     * 构造日志服务。
     *
     * @param properties 配置属性
     * @param repository 日志仓储
     * @param meterRegistry Micrometer 指标注册表
     */
    public ApiCallLogService(
            ApiCallLogProperties properties, ApiCallLogRepository repository, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.repository = repository;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 异步记录 API 调用日志。
     *
     * <p>在写入数据库前，对日志实体执行以下预处理：
     * <ol>
     *   <li>请求头 / 响应头中的敏感字段脱敏（{@link #sanitizeHeaders(String)}）</li>
     *   <li>请求体截断（{@link #truncate(String)}）</li>
     *   <li>响应体截断（受 {@link ApiCallLogProperties#isLogResponseBody()} 控制）</li>
     * </ol>
     * 预处理后的实体通过 {@link ApiCallLogRepository#save(ApiCallLogEntity)} 异步写入。</p>
     *
     * @param entity 待记录的 API 调用日志实体（将在方法内被修改和写入）
     * @return 提交写入操作后的空 Mono（不等待写入完成）
     */
    public Mono<Void> log(ApiCallLogEntity entity) {
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
        // C3 修复：将落库提交到专用阻塞池，避免在 WebClient NIO / doFinally 线程里阻塞，
        // 同时返回的 Mono 延迟到订阅者线程执行 subscribeOn，测试侧 Mockito 可同步 verify。
        return repository.save(entity).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 记录第三方调用 Micrometer 指标（耗时与结果分布）。
     *
     * <p>指标不依赖日志写入结果，即使落库失败也能反映真实调用情况：</p>
     * <ul>
     *   <li>{@code apex.api.call}：调用耗时 Timer，按目标服务 {@code service} 标签区分</li>
     *   <li>{@code apex.api.call.result}：调用结果 Counter，按 {@code service} 与 {@code result}
     *       （success/client_error/server_error/exception）标签区分</li>
     * </ul>
     *
     * @param entity 已预处理的 API 调用日志实体
     */
    private void recordApiCallMetrics(ApiCallLogEntity entity) {
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
     * 将调用状态码映射为结果标签。
     *
     * @param status 调用状态（0=2xx 成功，1=4xx 客户端错误，2=5xx 服务端错误，3=异常）
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
     * 对请求头 / 响应头的 JSON 字符串进行敏感字段脱敏。
     *
     * <h3>脱敏规则</h3>
     * <p>使用正则匹配 {@code "敏感字段名": "原始值"} 模式，将原始值替换为 {@code "***"}。
     * 字段名匹配不区分大小写。</p>
     *
     * <h3>脱敏的敏感字段</h3>
     * <ul>
     *   <li>{@code Authorization}</li>
     *   <li>{@code Api-Key} / {@code ApiKey}</li>
     *   <li>{@code Secret}</li>
     *   <li>{@code Token}</li>
     *   <li>{@code Signature}</li>
     *   <li>{@code Credential}</li>
     * </ul>
     *
     * @param headers 原始请求/响应头（JSON 格式），可为 null
     * @return 脱敏后的头信息，null 输入返回 null
     */
    private String sanitizeHeaders(String headers) {
        if (headers == null || headers.isBlank()) {
            return headers;
        }
        return SENSITIVE_KEY_PATTERN.matcher(headers).replaceAll("\"$1\":\"" + MASKED_VALUE + "\"");
    }

    /**
     * 对 URL 中的敏感查询参数（如 secret、access_token、appid）进行脱敏。
     *
     * <p>微信 API 调用时，{@code secret} 和 {@code access_token} 常以查询参数形式
     * 出现在 URL 中。若不对 URL 脱敏，这些敏感值会被原样写入数据库日志。</p>
     *
     * @param url 原始完整 URL，可为 null
     * @return 脱敏后的 URL；null 输入返回 null，非 URI 格式输入返回原值
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
     * 判断查询参数名是否为敏感字段。
     *
     * @param paramName 参数名
     * @return true 表示需脱敏
     */
    private boolean isSensitiveQueryParam(String paramName) {
        if (paramName == null) {
            return false;
        }
        return SENSITIVE_QUERY_PARAMS.stream().anyMatch(paramName::equalsIgnoreCase);
    }

    /**
     * 按配置的最大长度截断字符串。
     *
     * @param value 原始字符串，可为 null
     * @return 截断后的字符串，null 输入返回 null
     */
    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        int maxLen = properties.getMaxBodyLength();
        return value.length() <= maxLen ? value : value.substring(0, maxLen) + "...[truncated]";
    }

    /**
     * 构建敏感字段脱敏正则模式。
     *
     * <h3>正则模式</h3>
     * <pre>"(field1|field2|..."):".*?"</pre>
     *
     * <p>注意：不适用 {@link Pattern#quote(String)}（其产生的 {@code \Q...\E}
     * 在 JDK 17+ 的 {@code CASE_INSENSITIVE} 模式下行为不一致），
     * 改为仅转义真正的正则元字符。</p>
     *
     * @return 编译后的 Pattern
     */
    private static Pattern buildSensitivePattern() {
        String fieldNames = SENSITIVE_HEADER_NAMES.stream()
                .map(name -> name.replaceAll("([.$|{}()\\[\\]^*+?\\\\])", "\\\\$1"))
                .collect(Collectors.joining("|"));
        return Pattern.compile("\"(" + fieldNames + ")\":\"(?:[^\"]*)\"", Pattern.CASE_INSENSITIVE);
    }
}
