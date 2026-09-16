package com.apex.scaffold.interfaces.util;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 请求处理工具类
 *
 * <p>提供统一的请求处理、参数验证和响应构建功能。
 * 集成了国际化支持，错误响应消息会根据客户端的 Accept-Language 自动切换。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>{@link #handleRequestBody(ServerRequest, Class)} — 处理请求体并进行 JSR-380 Bean Validation</li>
 *   <li>{@link #buildSuccessResponse(Object)} — 构建成功的 JSON 响应</li>
 *   <li>{@link #buildErrorResponse(ResultCode, String)} — 构建带国际化的错误响应</li>
 *   <li>{@link #buildUnauthorizedResponse(String)} — 构建 401 未授权响应</li>
 *   <li>{@link #extractToken(ServerRequest)} — 提取 Authorization 头中的 Bearer Token</li>
 *   <li>{@link #extractClientIp(ServerRequest)} — 提取客户端真实IP地址</li>
 * </ul>
 *
 * <h2>国际化说明</h2>
 * <p>
 * 错误响应方法（{@code buildErrorResponse}、{@code buildUnauthorizedResponse}）
 * 通过 {@link MessageUtils} 从 Reactor Context 中获取 Locale 并解析对应语言的消息。
 * </p>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 * @see MessageUtils
 * @see com.apex.scaffold.interfaces.filter.LocaleWebFilter
 */
@Component
@RequiredArgsConstructor
public class RequestHandlerUtil {

    /** JSR-380 Bean Validation 校验器 */
    private final Validator validator;

    /** 国际化消息工具，用于根据客户端语言返回对应文本 */
    private final MessageUtils messageUtils;

    /** 仅在可信代理链下解析转发 IP，防止客户端伪造限流与审计来源。 */
    private final ClientIpResolver clientIpResolver;

    /**
     * 处理请求体并进行参数验证
     *
     * <p>从请求体中反序列化对象，然后执行 JSR-380 Bean Validation 校验。
     * 校验失败时抛出 {@link BusinessException}，校验失败的具体消息由
     * {@link ConstraintViolation#getMessage()} 提供。
     *
     * @param request ServerRequest 当前 HTTP 请求
     * @param clazz   请求体类型
     * @param <T>     请求体泛型类型
     * @return 验证通过的请求体对象 Mono
     * @throws BusinessException 当参数校验不通过时抛出
     */
    public <T> Mono<T> handleRequestBody(ServerRequest request, Class<T> clazz) {
        return request.bodyToMono(clazz).flatMap(body -> {
            Set<ConstraintViolation<T>> violations = validator.validate(body);
            if (!violations.isEmpty()) {
                String message =
                        violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, message));
            }
            return Mono.just(body);
        });
    }

    /**
     * 构建成功响应
     *
     * <p>将业务数据包装为统一格式的成功响应，返回 HTTP 200。
     *
     * @param data 响应数据
     * @param <T>  响应数据类型
     * @return HTTP 200 JSON 响应的 Mono
     */
    public <T> Mono<ServerResponse> buildSuccessResponse(T data) {
        String traceId = TraceIdUtil.getTraceId();
        Result<T> result = Result.success(data, traceId);
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(result);
    }

    /**
     * 构建错误响应（按错误码和自定义消息）
     *
     * <p>返回 400 Bad Request，消息为传入的 message 值（不做国际化处理）。
     *
     * @param code    错误代码
     * @param message 错误消息
     * @return HTTP 400 JSON 错误响应的 Mono
     */
    public Mono<ServerResponse> buildErrorResponse(int code, String message) {
        String traceId = TraceIdUtil.getTraceId();
        Result<Void> result = Result.error(code, message, traceId);
        return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result);
    }

    /**
     * 构建错误响应（按 ResultCode + 自定义消息）
     *
     * <p>返回 400 Bad Request。
     *
     * @param resultCode 结果代码
     * @param message    错误消息
     * @return HTTP 400 JSON 错误响应的 Mono
     */
    public Mono<ServerResponse> buildErrorResponse(ResultCode resultCode, String message) {
        return buildErrorResponse(resultCode.getCode(), message);
    }

    /**
     * 构建401错误响应
     *
     * <p>返回 HTTP 401 Unauthorized，消息使用传入的 message 值。
     *
     * @param message 错误消息
     * @return HTTP 401 JSON 错误响应的 Mono
     */
    public Mono<ServerResponse> buildUnauthorizedResponse(String message) {
        String traceId = TraceIdUtil.getTraceId();
        Result<Void> result = Result.error(ResultCode.UNAUTHORIZED.getCode(), message, traceId);
        return ServerResponse.status(401)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result);
    }

    /**
     * 提取请求头中的 JWT Token
     *
     * <p>从 {@code Authorization} 头中提取 Bearer Token。
     *
     * @param request ServerRequest 当前 HTTP 请求
     * @return Token 字符串的 Optional，未找到时为空
     */
    public Optional<String> extractToken(ServerRequest request) {
        return Optional.ofNullable(request.headers().firstHeader("Authorization"))
                .map(auth -> auth.replace("Bearer ", ""));
    }

    /**
     * 提取查询参数
     *
     * @param request ServerRequest 当前 HTTP 请求
     * @param name    参数名
     * @return 参数值的 Optional，未找到时为空
     */
    public Optional<String> extractQueryParam(ServerRequest request, String name) {
        return request.queryParam(name);
    }

    /**
     * 提取路径参数
     *
     * @param request ServerRequest 当前 HTTP 请求
     * @param name    参数名
     * @return 参数值字符串
     */
    public String extractPathParam(ServerRequest request, String name) {
        return request.pathVariable(name);
    }

    /**
     * 提取客户端真实 IP 地址
     *
     * <p>优先从 {@code X-Forwarded-For} 请求头获取，支持多级代理分隔。
     * 若不存在则回退到 {@code X-Real-IP}，最后使用远程连接地址。
     *
     * @param request ServerRequest 当前 HTTP 请求
     * @return 客户端真实 IP 地址，无法获取时返回 {@code "unknown"}
     */
    public String extractClientIp(ServerRequest request) {
        String realIp = request.headers().firstHeader("X-Real-IP");
        return Optional.ofNullable(clientIpResolver.resolve(
                        request.remoteAddress().orElse(null), request.headers().header("X-Forwarded-For"), realIp))
                .orElse("unknown");
    }

    /**
     * 验证并映射请求体
     *
     * <p>从请求体中反序列化对象并执行验证，支持自定义验证逻辑。
     *
     * @param request ServerRequest 当前 HTTP 请求
     * @param clazz   请求体类型
     * @param <T>     请求体泛型类型
     * @return 验证通过的请求体对象 Mono
     */
    public <T> Mono<T> validateAndMap(ServerRequest request, Class<T> clazz) {
        return handleRequestBody(request, clazz);
    }

    /**
     * 验证并映射请求体（带自定义验证器）
     *
     * @param request         ServerRequest 当前 HTTP 请求
     * @param clazz           请求体类型
     * @param customValidator 自定义验证函数
     * @param <T>             请求体泛型类型
     * @return 验证通过的请求体对象 Mono
     */
    public <T> Mono<T> validateAndMap(
            ServerRequest request, Class<T> clazz, java.util.function.Consumer<T> customValidator) {
        return handleRequestBody(request, clazz).doOnNext(customValidator);
    }

    /**
     * 构建成功响应（Result包装）
     *
     * @param result Result对象
     * @param <T>    响应数据类型
     * @return HTTP 200 JSON 响应的 Mono
     */
    public <T> Mono<ServerResponse> ok(Result<T> result) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(result);
    }

    /**
     * 获取租户ID
     *
     * @param request ServerRequest 当前 HTTP 请求
     * @return 租户ID
     */
    public String getTenantId(ServerRequest request) {
        return request.headers().firstHeader("X-Tenant-Id");
    }

    /**
     * 统一错误处理
     *
     * @param throwable 异常
     * @return 错误响应 Mono
     */
    public Mono<ServerResponse> handleError(Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            return buildErrorResponse(businessException.getCode(), businessException.getMessage());
        }
        return buildErrorResponse(ResultCode.ERROR, throwable.getMessage());
    }
}
