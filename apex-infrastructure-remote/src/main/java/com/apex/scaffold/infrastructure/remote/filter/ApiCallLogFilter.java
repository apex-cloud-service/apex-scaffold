package com.apex.scaffold.infrastructure.remote.filter;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallLogService;
import com.apex.scaffold.infrastructure.remote.logging.ApiCallObservation;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * API 调用日志 ExchangeFilterFunction。
 *
 * <p>注册为 Spring WebClient 的 {@link ExchangeFilterFunction}，在每次对外部 HTTP 服务
 * 发起调用时自动收集请求元数据（URL、方法、请求头）和响应元数据（状态码、耗时），
 * 通过 {@link ApiCallLogService} 异步写入数据库。</p>
 *
 * <h2>设计说明</h2>
 * <ol>
 *   <li><b>请求阶段</b> — 在发出请求前记录请求方法、URL、请求头</li>
 *   <li><b>响应成功</b> — 记录 HTTP 状态码、响应头、执行耗时</li>
 *   <li><b>响应失败</b> — 记录异常类型、错误消息、执行耗时</li>
 * </ol>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li><b>按需启用</b> — 仅当调用方通过 {@link #ATTRIBUTE_SERVICE_NAME} 属性传入
 *       serviceName 时才记录，不传则不记录</li>
 *   <li><b>敏感脱敏</b> — 请求头中的 Authorization / ApiKey / Secret 等字段由
 *       {@link ApiCallLogService} 在写入前脱敏</li>
 *   <li><b>异步不阻塞</b> — 数据库写入为异步 fire-and-forget，不影响请求链路时延</li>
 *   <li><b>不读取响应体</b> — 避免消耗响应流导致业务方获取不到数据</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code * webClient.get()
 *     .uri("https://api.example.com/users")
 *     .attribute(ApiCallLogFilter.ATTRIBUTE_SERVICE_NAME, "wechat")
 *     .retrieve()
 *     .bodyToMono(...);
 * }</pre>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see ApiCallLogService
 * @see ApiCallObservation
 */
@Slf4j
@lombok.RequiredArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public final class ApiCallLogFilter {

    /**
     * Exchange 属性键名 — 用于在 WebClient 请求中传入目标服务名称。
     * <p>设置此属性后，该次 HTTP 调用的元数据将被异步写入 {@code sys_api_call_log} 表。</p>
     */
    public static final String ATTRIBUTE_SERVICE_NAME = "api-call-log.service-name";

    /**
     * Exchange 属性键名 — 用于传入请求体内容（字符串）。
     * <p>调用方如需记录请求体，可提前将 body 序列化为字符串并设置到此属性中。</p>
     * <p>便捷方法：{@link #withRequestBody(String)}</p>
     */
    public static final String ATTRIBUTE_REQUEST_BODY = "api-call-log.request-body";

    /**
     * Exchange 属性键名 — 用于在 WebClient 请求中传入当前请求的租户 ID。
     * <p>若传入，API 调用日志落库时将在阻塞写入线程内显式安装此 tenantId，避免
     * WebClient 订阅线程与切面入口线程不同导致 TenantContext ThreadLocal 丢失。</p>
     */
    public static final String ATTRIBUTE_TENANT_ID = "api-call-log.tenant-id";

    /** 错误消息最大长度 */
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    /**
     * 返回一个 Consumer，用于将请求体文本写入日志属性。
     *
     * <p>使用示例：
     * <pre>{@code * .attributes(ApiCallLogFilter.withRequestBody(toJson(body)))
     * .bodyValue(body)
     * }</pre>
     *
     * @param body 序列化后的请求体文本
     * @return 设置请求体属性的 Consumer
     */
    public static java.util.function.Consumer<org.springframework.web.reactive.function.client.ClientRequest.Builder>
            withRequestBody(String body) {
        return builder -> builder.attribute(ATTRIBUTE_REQUEST_BODY, body);
    }

    /** API 调用日志服务。 */
    private final ApiCallLogService logService;

    /**
     * 静态工厂方法 — 创建 API 调用日志过滤器。
     *
     * @param logService API 调用日志服务
     * @return ExchangeFilterFunction 实例
     */
    public static ExchangeFilterFunction create(ApiCallLogService logService) {
        return new ApiCallLogFilter(logService).toFilter();
    }

    /**
     * 构建 ExchangeFilterFunction。
     *
     * <p>实现 Spring WebClient 的 {@link ExchangeFilterFunction#filter(ClientRequest, ExchangeFunction)}
     * 契约。核心流程：
     * <ol>
     *   <li>检查请求中是否存在 {@link #ATTRIBUTE_SERVICE_NAME} 属性</li>
     *   <li>不存在 → 直接放行，不记录日志</li>
     *   <li>存在 → 构建 {@link ApiCallObservation} 请求元数据，
     *       使用 {@code doOnSuccess} / {@code doOnError} / {@code doFinally} 回调记录响应</li>
     * </ol>
     *
     * @return ExchangeFilterFunction 实例
     */
    private ExchangeFilterFunction toFilter() {
        return (request, next) -> {
            String serviceName = request.attribute(ATTRIBUTE_SERVICE_NAME)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .orElse(null);
            if (serviceName == null) {
                return next.exchange(request);
            }
            ApiCallObservation entity = buildRequestMeta(request, serviceName);
            entity.setRequestBody(request.attribute(ATTRIBUTE_REQUEST_BODY)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .orElse(null));
            entity.setRequestId(UUID.randomUUID().toString().replace("-", ""));
            // C3 修复：在请求入口捕获 tenantId 属性，后续 WebClient 切换到 NIO 线程后
            // ThreadLocal 不可靠，写入线程内显式安装即可。
            String tenantId = request.attribute(ATTRIBUTE_TENANT_ID)
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .orElseGet(() -> TenantContext.currentTenantId().orElse(null));
            AtomicLong start = new AtomicLong(System.currentTimeMillis());

            return next.exchange(request)
                    .flatMap(response -> {
                        recordResponseMeta(entity, response, start.get());
                        if (isBinaryResponse(response)) {
                            if (!response.statusCode().is2xxSuccessful()) {
                                entity.setErrorMsg("HTTP "
                                        + response.statusCode().value()
                                        + " (binary response, body not captured)");
                            }
                            return reactor.core.publisher.Mono.just(response);
                        }
                        return response.bodyToMono(byte[].class)
                                .defaultIfEmpty(new byte[0])
                                .map(bytes -> {
                                    if (bytes.length > 0) {
                                        entity.setResponseBody(new String(bytes, StandardCharsets.UTF_8));
                                    }
                                    if (!response.statusCode().is2xxSuccessful() && bytes.length > 0) {
                                        entity.setErrorMsg(new String(bytes, StandardCharsets.UTF_8));
                                    }
                                    return bytes.length == 0
                                            ? response
                                            : response.mutate()
                                                    .body(Flux.just(
                                                            DefaultDataBufferFactory.sharedInstance.wrap(bytes)))
                                                    .build();
                                });
                    })
                    .doOnError(throwable -> {
                        entity.setExecutionTime(System.currentTimeMillis() - start.get());
                        entity.setStatus(3);
                        entity.setErrorMsg(formatError(throwable));
                    })
                    .doFinally(signalType -> writeLog(entity, tenantId));
        };
    }

    /**
     * C3 修复：在统一阻塞池上提交 API 调用日志写入，并在写入线程显式安装/释放
     * 从请求属性捕获的 tenantId；避免 doFinally 内裸 subscribe() 造成调度不确定，
     * 以及切换线程后的 ThreadLocal 丢失。
     *
     * @param entity   API 调用日志实体
     * @param tenantId 入口侧捕获的租户 ID（允许为 null）
     */
    private void writeLog(ApiCallObservation entity, String tenantId) {
        Mono.fromRunnable(() -> {
                    String previous = TenantContext.setTenantId(tenantId);
                    try {
                        logService
                                .log(entity)
                                .subscribe(null, error -> log.warn("API调用日志写入失败: {}", error.getMessage()));
                    } finally {
                        TenantContext.restoreTenantId(previous);
                    }
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .subscribe(null, error -> log.warn("API调用日志提交失败: {}", error.getMessage()));
    }

    /**
     * 判断响应体是否为不应写入文本审计日志的二进制内容。
     *
     * @param response 远程响应
     * @return 图片或通用二进制流返回 {@code true}
     */
    private boolean isBinaryResponse(ClientResponse response) {
        return response.headers()
                .contentType()
                .map(contentType -> contentType.getType().equalsIgnoreCase("image")
                        || MediaType.APPLICATION_OCTET_STREAM.includes(contentType))
                .orElse(false);
    }

    /**
     * 记录无需消费响应体即可取得的响应元数据。
     *
     * @param entity API 调用日志实体
     * @param response 远程响应
     * @param startMillis 请求开始时间
     */
    private void recordResponseMeta(ApiCallObservation entity, ClientResponse response, long startMillis) {
        entity.setExecutionTime(System.currentTimeMillis() - startMillis);
        entity.setResponseStatus(String.valueOf(response.statusCode().value()));
        entity.setResponseHeaders(headersToJson(response));
        entity.setStatus(
                response.statusCode().is2xxSuccessful()
                        ? 0
                        : response.statusCode().is4xxClientError() ? 1 : 2);
    }

    /**
     * 构建请求基础元数据（方法、URL、请求头、TraceId、时间）。
     *
     * @param request     客户端请求
     * @param serviceName 目标服务名称
     * @return 已填充基础字段的日志实体
     */
    private ApiCallObservation buildRequestMeta(ClientRequest request, String serviceName) {
        ApiCallObservation entity = new ApiCallObservation();
        entity.setServiceName(serviceName);
        entity.setMethod(request.method().name());
        entity.setPath(request.url().getPath());
        entity.setUrl(request.url().toString());
        entity.setRequestHeaders(headersToJson(request));
        entity.setTraceId(TraceIdUtil.getTraceId());
        entity.setCallTime(LocalDateTime.now());
        return entity;
    }

    /**
     * 将 ClientRequest 请求头序列化为 JSON 格式字符串。
     *
     * @param request 客户端请求
     * @return JSON 格式的头信息
     */
    private String headersToJson(ClientRequest request) {
        if (request.headers().isEmpty()) {
            return "{}";
        }
        return request.headers().entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":\"" + String.join(",", e.getValue()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    /**
     * 将 ClientResponse 响应头序列化为 JSON 格式字符串。
     *
     * @param response 客户端响应
     * @return JSON 格式的头信息
     */
    private String headersToJson(ClientResponse response) {
        if (response.headers().asHttpHeaders().isEmpty()) {
            return "{}";
        }
        return response.headers().asHttpHeaders().entrySet().stream()
                .map(e -> "\"" + e.getKey() + "\":\"" + String.join(",", e.getValue()) + "\"")
                .collect(Collectors.joining(",", "{", "}"));
    }

    /**
     * 格式化异常信息，递归展开根因链（类名 + 消息 + 每层 1 帧栈）。
     *
     * @param throwable 异常
     * @return 格式化的错误字符串，总长度不超过 2000 字符
     */
    private String formatError(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        Throwable current = throwable;
        while (current != null && sb.length() < MAX_ERROR_MESSAGE_LENGTH) {
            sb.append(current.getClass().getName()).append(": ").append(current.getMessage());
            StackTraceElement[] stack = current.getStackTrace();
            if (stack.length > 0) {
                sb.append("\n\tat ").append(stack[0]);
            }
            current = current.getCause();
            if (current != null) {
                sb.append("\nCaused by: ");
            }
        }
        if (sb.length() >= MAX_ERROR_MESSAGE_LENGTH) {
            sb.setLength(1997);
            sb.append("...");
        }
        return sb.toString();
    }
}
