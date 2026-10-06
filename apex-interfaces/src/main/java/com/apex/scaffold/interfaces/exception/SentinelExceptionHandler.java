package com.apex.scaffold.interfaces.exception;

import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.LogSafetyUtil;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.interfaces.monitor.SecurityMetricsRecorder;
import com.apex.scaffold.interfaces.util.MessageUtils;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Sentinel 流量控制异常处理器
 *
 * <p>统一处理 Sentinel 限流、熔断、降级、系统保护和授权规则触发的各类异常，
 * 向客户端返回符合 HTTP 语义标准的响应状态码和国际化友好错误提示。
 * 错误消息会根据客户端 Accept-Language 请求头自动切换为对应语言。
 *
 * <h2>异常处理映射</h2>
 * <table border="1">
 *   <caption>Sentinel 异常与 HTTP 状态码映射表</caption>
 *   <tr><th>Sentinel 异常类型</th><th>场景</th><th>HTTP 状态码</th><th>降级策略</th></tr>
 *   <tr><td>{@link FlowException}</td><td>QPS/线程数限流</td><td>429 Too Many Requests</td><td>标准错误码</td></tr>
 *   <tr><td>{@link ParamFlowException}</td><td>热点参数限流</td><td>429 Too Many Requests</td><td>标准错误码</td></tr>
 *   <tr><td>{@link DegradeException}</td><td>熔断降级</td><td>503 Service Unavailable</td><td>读接口返回缓存兜底值，写接口返回排队中状态</td></tr>
 *   <tr><td>{@link SystemBlockException}</td><td>系统负载保护</td><td>503 Service Unavailable</td><td>标准错误码</td></tr>
 *   <tr><td>{@link AuthorityException}</td><td>黑白名单授权</td><td>403 Forbidden</td><td>标准错误码</td></tr>
 *   <tr><td>{@link BlockException}</td><td>其他Sentinel阻塞</td><td>503 Service Unavailable</td><td>标准错误码</td></tr>
 * </table>
 *
 * <h2>业务级降级</h2>
 * <p>对于 {@link DegradeException}（熔断降级），不再仅返回统一错误码：
 * <ul>
 *   <li><strong>读接口</strong>（GET 请求）：通过 {@link DegradeFallbackService} 尝试返回缓存快照</li>
 *   <li><strong>写接口</strong>（POST/PUT/DELETE 请求）：返回"排队中"状态，提示客户端稍后查询</li>
 * </ul>
 *
 * <h2>国际化支持</h2>
 * <p>
 * 通过 {@link MessageUtils} 从 Reactor Context 中获取由
 * {@link com.apex.scaffold.interfaces.filter.LocaleWebFilter} 写入的 Locale，
 * 将流量控制相关的错误消息翻译为客户端偏好的语言。
 * </p>
 *
 * @author Apex Team
 * @version 3.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.interfaces.config.SentinelConfig
 * @see com.apex.scaffold.interfaces.util.MessageUtils
 * @see DegradeFallbackService
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class SentinelExceptionHandler {

    /** 国际化消息工具，用于根据客户端语言解析错误消息 */
    private final MessageUtils messageUtils;

    /** 安全指标记录器 */
    private final SecurityMetricsRecorder securityMetricsRecorder;

    /** 降级兜底服务，提供缓存快照和排队中状态 */
    private final DegradeFallbackService degradeFallbackService;

    /** 读接口 HTTP 方法集合，用于区分降级策略 */
    private static final Set<String> READ_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    /**
     * 处理 QPS/线程数限流异常
     *
     * <p>当请求量超过 Sentinel 配置的流控规则阈值时触发此异常。
     * 返回 HTTP 429 状态码，提示客户端进行限速。
     * 错误消息会根据客户端语言自动切换。
     *
     * @param ex       限流异常，包含触发的规则信息
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @return 包含国际化限流错误信息的响应实体 Mono
     */
    @ExceptionHandler(FlowException.class)
    public Mono<ResponseEntity<Result<Void>>> handleFlowException(FlowException ex, ServerWebExchange exchange) {
        String traceId = resolveTraceId(exchange);
        securityMetricsRecorder.recordRateLimitRejected();
        String resource = ex.getRule() != null ? ex.getRule().getResource() : "unknown";
        log.warn("接口限流, resource: {}, traceId: {}", LogSafetyUtil.uri(resource, 256), traceId);
        return buildI18nError(ResultCode.RATE_LIMIT, traceId, HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * 处理热点参数限流异常
     *
     * <p>当特定参数的请求频次超过热点规则阈值时触发此异常。
     * 返回 HTTP 429 状态码，提示客户端降低对该热点参数的请求频率。
     *
     * @param ex       热点参数限流异常，包含触发的规则信息
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @return 包含国际化限流错误信息的响应实体 Mono
     */
    @ExceptionHandler(ParamFlowException.class)
    public Mono<ResponseEntity<Result<Void>>> handleParamFlowException(
            ParamFlowException ex, ServerWebExchange exchange) {
        String traceId = resolveTraceId(exchange);
        securityMetricsRecorder.recordRateLimitRejected();
        String resource = ex.getRule() != null ? ex.getRule().getResource() : "unknown";
        log.warn("热点参数限流, resource: {}, traceId: {}", LogSafetyUtil.uri(resource, 256), traceId);
        return buildI18nError(ResultCode.RATE_LIMIT, traceId, HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * 处理熔断降级异常
     *
     * <p>当资源进入熔断状态（异常比例/慢调用比例/异常数阈值触发）时触发此异常。
     * 返回 HTTP 503 状态码，表示服务暂时不可用。
     *
     * <p>对于熔断降级，启用业务级降级策略：
     * <ul>
     *   <li>读接口（GET/HEAD/OPTIONS）：尝试返回缓存兜底值</li>
     *   <li>写接口（POST/PUT/DELETE）：返回"排队中"状态</li>
     * </ul>
     *
     * @param ex       熔断降级异常，包含触发的熔断规则信息
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @return 包含降级响应的响应实体 Mono
     */
    @ExceptionHandler(DegradeException.class)
    public Mono<ResponseEntity<Result<Object>>> handleDegradeException(
            DegradeException ex, ServerWebExchange exchange) {
        String traceId = resolveTraceId(exchange);
        String resource = ex.getRule() != null ? ex.getRule().getResource() : "unknown";
        securityMetricsRecorder.recordCircuitBreakerOpened();
        String method = exchange.getRequest().getMethod().name();
        log.warn("接口熔断, resource: {}, method: {}, traceId: {}", LogSafetyUtil.uri(resource, 256), method, traceId);

        if (READ_METHODS.contains(method)) {
            return degradeFallbackService
                    .buildDegradedReadResponse(resource, traceId)
                    .map(result -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(result));
        } else {
            return degradeFallbackService
                    .buildDegradedWriteResponse(resource, traceId)
                    .map(result -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(result));
        }
    }

    /**
     * 处理系统负载保护异常
     *
     * <p>当系统整体负载（Load、CPU使用率、平均RT、入口QPS、并发线程数）超过
     * 系统保护规则阈值时触发此异常。返回 HTTP 503 状态码。
     *
     * @param ex       系统保护异常，包含触发的系统规则信息
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @return 包含国际化降级错误信息的响应实体 Mono
     */
    @ExceptionHandler(SystemBlockException.class)
    public Mono<ResponseEntity<Result<Void>>> handleSystemBlockException(
            SystemBlockException ex, ServerWebExchange exchange) {
        String traceId = resolveTraceId(exchange);
        log.warn("系统保护触发, traceId: {}", traceId);
        return buildI18nError(ResultCode.DEGRADE, traceId, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * 处理授权规则异常
     *
     * <p>当请求来源不在 Sentinel 授权规则的白名单中或在黑名单中时触发此异常。
     * 返回 HTTP 403 状态码，表示请求被授权规则拒绝。
     *
     * @param ex       授权异常，包含触发的授权规则信息
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @return 包含国际化授权拒绝错误信息的响应实体 Mono
     */
    @ExceptionHandler(AuthorityException.class)
    public Mono<ResponseEntity<Result<Void>>> handleAuthorityException(
            AuthorityException ex, ServerWebExchange exchange) {
        String traceId = resolveTraceId(exchange);
        log.warn("授权规则限制, traceId: {}", traceId);
        return buildI18nError(ResultCode.FORBIDDEN, traceId, HttpStatus.FORBIDDEN);
    }

    /**
     * 处理其他 Sentinel 阻塞异常
     *
     * <p>兜底处理方法，捕获未被上述具体异常处理器匹配的其他 {@link BlockException}
     * 子类异常。返回 HTTP 503 状态码。
     *
     * @param ex       Sentinel 阻塞异常
     * @param exchange 当前 HTTP 请求-响应交换对象
     * @return 包含国际化降级错误信息的响应实体 Mono
     */
    @ExceptionHandler(BlockException.class)
    public Mono<ResponseEntity<Result<Void>>> handleBlockException(BlockException ex, ServerWebExchange exchange) {
        String traceId = resolveTraceId(exchange);
        log.warn("Sentinel阻塞异常, type: {}, traceId: {}", ex.getClass().getSimpleName(), traceId);
        return buildI18nError(ResultCode.CIRCUIT_BREAKER, traceId, HttpStatus.SERVICE_UNAVAILABLE);
    }

    /**
     * 构建国际化错误响应
     *
     * <p>从 Reactor Context 中获取 Locale，通过 {@link MessageUtils}
     * 解析对应语言的错误消息，构建统一格式的 HTTP 错误响应。
     *
     * @param resultCode 错误码枚举，包含 code 和 messageKey
     * @param traceId    链路追踪 ID
     * @param httpStatus HTTP 状态码
     * @return 包含国际化错误消息的 ResponseEntity Mono
     */
    private Mono<ResponseEntity<Result<Void>>> buildI18nError(
            ResultCode resultCode, String traceId, HttpStatus httpStatus) {
        return Mono.deferContextual(ctx -> {
            Locale locale = ctx.getOrDefault(Locale.class, Locale.SIMPLIFIED_CHINESE);
            String msg = messageUtils.get(locale, resultCode.getMessageKey());
            Result<Void> result = Result.error(resultCode.getCode(), msg, traceId);
            return Mono.just(ResponseEntity.status(httpStatus).body(result));
        });
    }

    private String resolveTraceId(ServerWebExchange exchange) {
        String traceId = exchange.getAttribute(TraceIdUtil.TRACE_ID_KEY);
        return LogSafetyUtil.text(traceId == null || traceId.isBlank() ? TraceIdUtil.getTraceId() : traceId, 64);
    }
}
