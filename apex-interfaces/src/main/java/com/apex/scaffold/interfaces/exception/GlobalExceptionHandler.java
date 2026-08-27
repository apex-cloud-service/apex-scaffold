package com.apex.scaffold.interfaces.exception;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.HttpStatusUtil;
import com.apex.scaffold.common.util.JsonUtil;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.infrastructure.remote.exception.RemoteApiException;
import com.apex.scaffold.interfaces.monitor.ExceptionMonitor;
import com.apex.scaffold.interfaces.util.MessageUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * 全局异常处理器
 *
 * <p>
 * 统一处理应用程序中抛出的各类异常，将其转换为标准响应格式 {@link Result}。
 * 基于 Spring WebFlux 的 {@link RestControllerAdvice} 实现，支持响应式异常处理。
 * 集成了国际化支持，异常消息会根据客户端的 Accept-Language 自动切换为对应语言。
 *
 * <h2>处理的异常类型</h2>
 * <ul>
 *   <li><b>业务异常</b>：{@link BusinessException}，根据错误码映射 HTTP 状态码，
 *       使用 {@link BusinessException#getMessageKey()} 获取关联的消息键</li>
 *   <li><b>微信 API 异常</b>：{@code WeChatApiException}</li>
 *   <li><b>参数校验异常</b>：{@link ConstraintViolationException}、{@link WebExchangeBindException}</li>
 *   <li><b>参数异常</b>：{@link IllegalArgumentException}、{@link IllegalStateException}</li>
 *   <li><b>系统异常</b>：{@link NullPointerException} 及其他未捕获异常</li>
 * </ul>
 *
 * <h2>设计模式</h2>
 * <ul>
 *   <li>集中式异常处理模式：通过 {@link RestControllerAdvice} 统一异常处理入口</li>
 *   <li>策略映射：根据异常类型和错误码映射对应的 HTTP 状态码</li>
 *   <li>观察者模式：集成 {@link ExceptionMonitor} 监控所有异常事件</li>
 *   <li>国际化适配：通过 {@link MessageUtils} 解析多语言消息</li>
 *   <li>链路一致性：优先复用请求交换对象中的 Trace ID，避免响应式线程切换后生成新 ID</li>
 * </ul>
 *
 * @author Apex Team
 * @version 4.1.0
 * @since 1.0.0
 * @see ExceptionMonitor
 * @see BusinessException
 * @see MessageUtils
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    /** 默认错误码，表示未分类的系统错误 */
    private static final int DEFAULT_ERROR_CODE = -1;

    /** 异常监控器，用于收集和记录系统异常 */
    private final ExceptionMonitor exceptionMonitor;

    /** 国际化消息工具，用于根据客户端语言解析错误消息 */
    private final MessageUtils messageUtils;

    /**
     * 处理业务异常
     *
     * <p>
     * 捕获应用程序中抛出的 {@link BusinessException}，根据错误码映射对应的 HTTP 状态码。
     * 使用 {@link BusinessException#getMessageKey()} 获取关联的国际化消息键，
     * 而非使用 {@link ResultCode#getMessageKey()} 的默认映射，确保异常消息准确反映业务含义。
     *
     * @param ex       {@link BusinessException} 业务异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 包含错误码和错误信息的错误响应
     */
    @ExceptionHandler(BusinessException.class)
    public Mono<ResponseEntity<Result<Void>>> handleBusinessException(
            BusinessException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);
        log.warn("业务异常: {}, traceId: {}", ex.getMessage(), traceId);

        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg;
            if (ex.getMessageKey() != null && ex.getMsg() != null) {
                // 将 msg 与 messageKey 在默认 locale(zh_CN) 下的解析结果对比：
                // 若一致，说明是 ResultCode 的标准消息，使用客户端 locale 做 i18n；
                // 若不一致，说明开发者传入了自定义消息，直接返回自定义消息
                String defaultMsg = messageUtils.get(Locale.SIMPLIFIED_CHINESE, ex.getMessageKey());
                if (ex.getMsg().equals(defaultMsg)) {
                    msg = messageUtils.get(locale, ex.getMessageKey());
                } else {
                    msg = ex.getMsg();
                }
            } else {
                msg = ex.getMsg() != null ? ex.getMsg() : messageUtils.get(locale, ResultCode.ERROR.getMessageKey());
            }
            Result<Void> result = Result.error(ex.getCode(), msg, traceId);
            HttpStatus status = HttpStatusUtil.fromBusinessCode(ex.getCode());
            return Mono.just(ResponseEntity.status(status).body(result));
        });
    }

    /**
     * 处理微信 API 异常
     *
     * <p>
     * 捕获微信接口调用过程中抛出的异常，返回 502 Bad Gateway 状态码。
     *
     * @param ex       {@code WeChatApiException} 微信 API 异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 错误响应（502 Bad Gateway）
     */
    @ExceptionHandler(com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException.class)
    public Mono<ResponseEntity<Result<Void>>> handleWeChatApiException(
            com.apex.scaffold.infrastructure.wechat.exception.WeChatApiException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);
        log.warn("微信API异常: {}, traceId: {}", ex.getMessage(), traceId);

        Result<Void> result = Result.error(ResultCode.WX_API_ERROR.getCode(), ex.getMessage(), traceId);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result));
    }

    /**
     * 将 Python 等远程 HTTP 服务异常映射为稳定的网关响应，且不向客户端泄露上游响应体。
     */
    @ExceptionHandler(RemoteApiException.class)
    public Mono<ResponseEntity<Result<Void>>> handleRemoteApiException(
            RemoteApiException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);
        String traceId = resolveTraceId(exchange);
        log.warn(
                "远程API异常: service={}, path={}, status={}, circuitBreakerOpen={}, traceId={}",
                ex.getServiceName(),
                ex.getRequestPath(),
                ex.getHttpStatus(),
                ex.isCircuitBreakerOpen(),
                traceId);

        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            if (ex.isClientError()) {
                Result<Void> result = Result.error(
                        ResultCode.PARAM_ERROR.getCode(), messageUtils.get(locale, "sys.param_error"), traceId);
                return Mono.just(ResponseEntity.badRequest().body(result));
            }
            // 熔断器打开 → 503（服务降级，语义正确）
            if (ex.isCircuitBreakerOpen()) {
                Result<Void> result = Result.error(
                        ResultCode.CIRCUIT_BREAKER.getCode(),
                        messageUtils.get(locale, "flow.circuit_breaker"),
                        traceId);
                return Mono.just(
                        ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result));
            }
            // 网络超时/连接异常（httpStatus == -1）→ 504
            if (ex.getHttpStatus() == -1) {
                Result<Void> result = Result.error(
                        ResultCode.CIRCUIT_BREAKER.getCode(),
                        messageUtils.get(locale, "flow.network_timeout"),
                        traceId);
                return Mono.just(
                        ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(result));
            }
            // 远程服务返回 5xx → 502
            Result<Void> result = Result.error(
                    ResultCode.CIRCUIT_BREAKER.getCode(), messageUtils.get(locale, "flow.circuit_breaker"), traceId);
            return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(result));
        });
    }

    /**
     * 处理参数校验异常（方法参数校验）
     *
     * <p>
     * 处理 JSR-380 Bean Validation 注解（如 {@code @NotBlank}、{@code @Size} 等）触发的参数校验异常。
     * 将所有校验失败信息拼接后返回。
     *
     * @param ex       {@link ConstraintViolationException} 约束违反异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 错误响应（400 Bad Request），包含所有校验失败信息
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Mono<ResponseEntity<Result<Void>>> handleConstraintViolationException(
            ConstraintViolationException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);
        String message = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));

        log.warn("参数校验失败: {}, traceId: {}", message, traceId);

        Result<Void> result = Result.error(ResultCode.PARAM_ERROR.getCode(), message, traceId);
        return Mono.just(ResponseEntity.badRequest().body(result));
    }

    /**
     * 处理非法参数异常
     *
     * @param ex       {@link IllegalArgumentException} 非法参数异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 错误响应（400 Bad Request）
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ResponseEntity<Result<Void>>> handleIllegalArgumentException(
            IllegalArgumentException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);
        log.warn("非法参数: {}, traceId: {}", ex.getMessage(), traceId, ex);

        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg = messageUtils.get(locale, "sys.param_error");
            Result<Void> result = Result.error(ResultCode.PARAM_ERROR.getCode(), msg, traceId);
            return Mono.just(ResponseEntity.badRequest().body(result));
        });
    }

    /**
     * 处理请求体解码异常
     *
     * <p>
     * 当客户端提交的请求体格式不合法时，例如 multipart/form-data 缺失 boundary、
     * 上传内容被代理截断或请求体与声明的 Content-Type 不匹配，WebFlux 底层会抛出
     * {@link DecodingException}。这类异常属于客户端输入错误，应稳定返回 400，
     * 而不是被误判为系统内部错误。
     *
     * @param ex       {@link DecodingException} 请求体解码异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 错误响应（400 Bad Request）
     * @since 4.0.0
     */
    @ExceptionHandler(DecodingException.class)
    public Mono<ResponseEntity<Result<Void>>> handleDecodingException(
            DecodingException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);
        String contentType = exchange.getRequest().getHeaders().getFirst("Content-Type");
        log.warn("请求体解码失败: {}, contentType: {}, traceId: {}", ex.getMessage(), contentType, traceId);

        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg = messageUtils.get(locale, "sys.param_error");
            Result<Void> result = Result.error(ResultCode.PARAM_ERROR.getCode(), msg, traceId);
            return Mono.just(ResponseEntity.badRequest().body(result));
        });
    }

    /**
     * 处理非法状态异常
     *
     * @param ex       {@link IllegalStateException} 非法状态异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 错误响应（409 Conflict）
     */
    @ExceptionHandler(IllegalStateException.class)
    public Mono<ResponseEntity<Result<Void>>> handleIllegalStateException(
            IllegalStateException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);
        log.warn("非法状态: {}, traceId: {}", ex.getMessage(), traceId);

        Result<Void> result = Result.error(ResultCode.ERROR.getCode(), ex.getMessage(), traceId);
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(result));
    }

    /**
     * 处理数据库数据完整性违反异常
     *
     * <p>
     * 捕获数据库层面的约束违反异常（如 NOT NULL、UNIQUE、外键约束等），
     * 返回 409 Conflict 状态码。该异常通常由数据校验不足或不正确的业务逻辑引起，
     * 需要记录详细错误日志以便排查。
     * 错误消息通过 {@link MessageUtils} 获取客户端语言的文本。
     *
     * <h3>常见触发场景</h3>
     * <ul>
     *   <li>插入或更新时违反非空约束（NOT NULL）</li>
     *   <li>插入或更新时违反唯一约束（UNIQUE）</li>
     *   <li>插入或更新时违反外键约束（FOREIGN KEY）</li>
     *   <li>数据长度超出列定义限制</li>
     * </ul>
     *
     * @param ex       {@link DataIntegrityViolationException} 数据完整性违反异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 错误响应（409 Conflict），包含国际化的错误提示
     * @since 2.1.0
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Mono<ResponseEntity<Result<Void>>> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);
        log.error("数据完整性违反: {}, traceId: {}", ex.getMessage(), traceId, ex);

        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg = messageUtils.get(locale, "sys.data_integrity_error");
            Result<Void> result = Result.error(ResultCode.ERROR.getCode(), msg, traceId);
            return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(result));
        });
    }

    /**
     * 处理空指针异常
     *
     * <p>
     * 系统级异常，记录详细错误日志并返回通用错误信息，避免泄露敏感信息。
     * 错误消息通过 {@link MessageUtils} 获取客户端语言的文本。
     *
     * @param ex       {@link NullPointerException} 空指针异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 错误响应（500 Internal Server Error），包含国际化的错误提示
     */
    @ExceptionHandler(NullPointerException.class)
    public Mono<ResponseEntity<Result<Void>>> handleNullPointerException(
            NullPointerException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);
        log.error("空指针异常: {}, traceId: {}", sanitizeMessage(ex.getMessage()), traceId, ex);

        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg = messageUtils.get(locale, "sys.error");
            Result<Void> result = Result.error(ResultCode.ERROR.getCode(), msg, traceId);
            return Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result));
        });
    }

    /**
     * 处理所有其他未捕获的异常
     *
     * <p>
     * 兜底异常处理器，确保所有未明确处理的异常都能返回统一格式的响应，
     * 避免将异常堆栈信息暴露给客户端。错误消息通过 {@link MessageUtils} 国际化。
     *
     * @param ex       {@link Exception} 异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 错误响应（500 Internal Server Error），包含国际化的通用错误提示
     */
    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Result<Void>>> handleException(Exception ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);
        log.error("系统异常: {}, traceId: {}", sanitizeMessage(ex.getMessage()), traceId, ex);

        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg = messageUtils.get(locale, "sys.internal_error");
            Result<Void> result = Result.error(ResultCode.ERROR.getCode(), msg, traceId);
            return Mono.just(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result));
        });
    }

    /**
     * 处理 WebFlux 请求体绑定异常（参数校验失败）
     *
     * <p>
     * 在 WebFlux 响应式环境中，当 {@code @RequestBody} 注解的参数绑定失败
     * 或请求体反序列化失败时，Spring 会抛出 {@link WebExchangeBindException}，
     * 而非 Spring MVC 的 {@code MethodArgumentNotValidException}（WebFlux 下不会抛出）。
     * 本方法收集所有字段级别的校验错误信息，提取 {@link FieldError} 的默认消息并拼接，
     * 返回 400 Bad Request 状态码及国际化的错误提示。
     *
     * <h3>异常来源</h3>
     * <ul>
     *   <li>请求体 JSON 反序列化失败（类型不匹配、格式错误）</li>
     *   <li>{@code @Valid} 或 {@code @Validated} 触发的 JSR-380 校验失败</li>
     *   <li>WebFlux 编码器/解码器内部的绑定错误</li>
     * </ul>
     *
     * @param ex       {@link WebExchangeBindException} WebFlux 绑定异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 错误响应（400 Bad Request），包含字段校验失败的详细信息
     * @since 1.0.0
     */
    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Result<Void>>> handleWebExchangeBindException(
            WebExchangeBindException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);

        String message =
                ex.getFieldErrors().stream().map(FieldError::getDefaultMessage).collect(Collectors.joining(", "));

        log.warn("WebFlux请求体绑定失败: {}, traceId: {}", message, traceId);

        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg = messageUtils.get(locale, "sys.param_error");
            Result<Void> result = Result.error(ResultCode.PARAM_ERROR.getCode(), msg, traceId);
            return Mono.just(ResponseEntity.badRequest().body(result));
        });
    }

    /**
     * 处理 Spring 响应式状态异常
     *
     * <p>
     * {@link ResponseStatusException} 是 Spring WebFlux 框架内置的异常类，
     * 允许在应用代码中直接抛出带有 HTTP 状态码和原因描述的异常。
     * 本方法动态读取异常中携带的 {@link HttpStatus} 作为响应状态码，
     * 并使用 {@link ResponseStatusException#getReason()} 作为错误消息，
     * 将框架级状态异常统一转换为标准 {@link Result} 格式。
     *
     * <h3>典型使用场景</h3>
     * <ul>
     *   <li>资源未找到：{@code throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在")}</li>
     *   <li>权限不足：{@code throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问")}</li>
     *   <li>服务不可用：{@code throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "服务暂不可用")}</li>
     * </ul>
     *
     * @param ex       {@link ResponseStatusException} 响应式状态异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 错误响应，HTTP 状态码由异常内部决定
     * @since 1.0.0
     */
    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<Result<Void>>> handleResponseStatusException(
            ResponseStatusException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);
        String reason = ex.getReason() != null ? ex.getReason() : "";
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        log.warn("响应状态异常: [{}] {}, traceId: {}", status.value(), reason, traceId);

        Result<Void> result = Result.error(status.value(), reason, traceId);
        return Mono.just(ResponseEntity.status(status).body(result));
    }

    /**
     * 处理请求体超出内存缓冲区大小限制异常
     *
     * <p>
     * 当客户端发送的请求体（如文件上传、大型 JSON 请求）超过 Spring WebFlux
     * {@code spring.codec.max-in-memory-size} 配置的阈值时，底层编解码器会抛出
     * {@link DataBufferLimitException}。本方法捕获该异常并返回 413 Payload Too Large，
     * 提示客户端缩小请求体或分片传输。
     *
     * <h3>配置示例</h3>
     * <pre>{@code * spring:
     *   codec:
     *     max-in-memory-size: 10MB
     * }</pre>
     *
     * @param ex       {@link DataBufferLimitException} 数据缓冲区超限异常
     * @param exchange {@link ServerWebExchange} 交换对象
     * @return 错误响应（413 Payload Too Large），包含国际化的错误提示
     * @since 1.0.0
     */
    @ExceptionHandler(DataBufferLimitException.class)
    public Mono<ResponseEntity<Result<Void>>> handleDataBufferLimitException(
            DataBufferLimitException ex, ServerWebExchange exchange) {
        exceptionMonitor.monitorException(ex);

        String traceId = resolveTraceId(exchange);
        log.warn("请求体超出缓冲区大小限制: {}, traceId: {}", ex.getMessage(), traceId);

        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg = messageUtils.get(locale, "sys.param_error");
            Result<Void> result = Result.error(ResultCode.PARAM_ERROR.getCode(), msg, traceId);
            return Mono.just(ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(result));
        });
    }

    /**
     * 解析当前请求应写入日志和错误响应的 Trace ID。
     *
     * <p>{@code TraceFilter} 在进入业务链时将 Trace ID 同时写入 {@link ServerWebExchange}
     * attributes 和 Reactor Context。异常可能在 JDBC {@code boundedElastic} 等其他线程上产生，
     * 因此不能优先依赖基于 ThreadLocal 的 MDC。这里首先读取不受线程切换影响的交换对象属性；
     * 仅当过滤器未执行或属性为空时，才回退到 {@link TraceIdUtil#getTraceId()}。</p>
     *
     * @param exchange 当前请求交换对象
     * @return 与请求访问日志和 {@code X-Trace-Id} 响应头一致的 Trace ID；缺失时生成兜底 ID
     */
    private String resolveTraceId(ServerWebExchange exchange) {
        String exchangeTraceId = exchange.getAttribute(TraceIdUtil.TRACE_ID_KEY);
        if (exchangeTraceId != null && !exchangeTraceId.isBlank()) {
            return exchangeTraceId;
        }
        return TraceIdUtil.getTraceId();
    }

    /**
     * WebFlux 函数式端点的兜底异常处理器
     *
     * <p>
     * 作为 {@link WebExceptionHandler} 的 Bean 注册，
     * 捕获 {@link RestControllerAdvice} 无法覆盖的 WebFlux 底层异常。
     * 根据异常类型判断客户端错误或服务器错误，返回对应语言的 JSON 格式错误响应。
     *
     * <h3>异常分类</h3>
     * <ul>
     *   <li>{@link BusinessException}：根据错误码映射状态码，使用消息键获取国际化消息</li>
     *   <li>{@link IllegalArgumentException} 或 {@code ServerWebInputException}：400 Bad Request</li>
     *   <li>其他异常：500 Internal Server Error</li>
     * </ul>
     *
     * @return {@link WebExceptionHandler} 实例
     */
    @org.springframework.context.annotation.Bean
    public WebExceptionHandler webExceptionHandler() {
        return (exchange, ex) -> {
            if (exchange.getResponse().isCommitted()) {
                return Mono.error(ex);
            }
            String traceId = resolveTraceId(exchange);
            log.error("WebFlux未捕获异常: {}, traceId: {}", ex.getMessage(), traceId, ex);

            return Mono.deferContextual(ctx -> {
                Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
                if (locale == null) {
                    locale = Locale.SIMPLIFIED_CHINESE;
                }

                HttpStatus status;
                String msg;
                Integer code;

                if (ex instanceof BusinessException be) {
                    status = HttpStatusUtil.fromBusinessCode(be.getCode());
                    code = be.getCode();
                    if (be.getMessageKey() != null && be.getMsg() != null) {
                        String defaultMsg = messageUtils.get(Locale.SIMPLIFIED_CHINESE, be.getMessageKey());
                        if (be.getMsg().equals(defaultMsg)) {
                            msg = messageUtils.get(locale, be.getMessageKey());
                        } else {
                            msg = be.getMsg();
                        }
                    } else {
                        msg = be.getMsg() != null
                                ? be.getMsg()
                                : messageUtils.get(locale, ResultCode.ERROR.getMessageKey());
                    }
                } else if (ex instanceof RemoteApiException remoteApiException) {
                    if (remoteApiException.isClientError()) {
                        status = HttpStatus.BAD_REQUEST;
                        msg = messageUtils.get(locale, "sys.param_error");
                        code = ResultCode.PARAM_ERROR.getCode();
                    } else {
                        status = HttpStatus.BAD_GATEWAY;
                        msg = messageUtils.get(locale, "sys.internal_error");
                        code = ResultCode.ERROR.getCode();
                    }
                } else if (ex instanceof IllegalArgumentException
                        || ex instanceof org.springframework.web.server.ServerWebInputException
                        || ex instanceof DecodingException) {
                    status = HttpStatus.BAD_REQUEST;
                    msg = messageUtils.get(locale, "sys.param_error");
                    code = 400;
                } else {
                    status = HttpStatus.INTERNAL_SERVER_ERROR;
                    msg = messageUtils.get(locale, "sys.internal_error");
                    code = DEFAULT_ERROR_CODE;
                }

                exchange.getResponse().setStatusCode(status);
                exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                Result<?> result = Result.error(code, msg, traceId);
                DataBufferFactory bufferFactory = exchange.getResponse().bufferFactory();
                byte[] bytes = JsonUtil.toJson(result).getBytes(StandardCharsets.UTF_8);
                return exchange.getResponse().writeWith(Mono.just(bufferFactory.wrap(bytes)));
            });
        };
    }

    /**
     * 对异常消息进行脱敏处理,防止敏感信息泄露到日志中。
     *
     * <p>支持的脱敏类型:
     * <ul>
     *   <li>手机号: 138****8000</li>
     *   <li>身份证号: 110***********1234</li>
     *   <li>邮箱: t***@example.com</li>
     *   <li>银行卡号: 6222 **** **** 1234</li>
     * </ul>
     *
     * @param message 原始异常消息
     * @return 脱敏后的消息
     */
    private String sanitizeMessage(String message) {
        if (message == null || message.isEmpty()) {
            return message;
        }

        // 手机号脱敏 (11位数字,以1开头)
        String sanitized = message.replaceAll("(?<!\\d)(1[3-9]\\d{1})(\\d{4})(\\d{4})(?!\\d)", "$1****$3");

        // 身份证号脱敏 (15或18位,保留前3后4)
        sanitized = sanitized.replaceAll("(?<!\\d)(\\d{3})(\\d{8,11})(\\d{4})(?!\\d)", "$1*********$3");

        // 邮箱脱敏
        sanitized = sanitized.replaceAll(
                "([a-zA-Z0-9._%+-]{1})[a-zA-Z0-9._%+-]*(@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})", "$1***$2");

        // 银行卡号脱敏 (16-19位数字,保留前4后4)
        sanitized = sanitized.replaceAll("(?<!\\d)(\\d{4})(\\d{8,11})(\\d{4})(?!\\d)", "$1 **** **** $3");

        return sanitized;
    }
}
