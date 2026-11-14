package com.apex.scaffold.officeservice.interfaces.util;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 请求处理工具类。
 *
 * <p>为办公服务接口层提供统一的请求处理、参数验证和响应构建能力，
 * 与单应用时代 {@code apex-interfaces} 的请求处理逻辑保持一致，避免继承
 * 依赖于旧应用专有类型（如 {@code BaseHandler}）的基类。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class RequestHandlerUtil {

    private final Validator validator;

    /**
     * 处理请求体并进行参数验证。
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
     * 执行 Jakarta Bean Validation 校验（响应式链操作符）。
     *
     * @param body 待校验对象
     * @param <T>  待校验类型
     * @return 校验通过时返回含原 body 的 Mono，失败时返回携带 BusinessException 的 error Mono
     */
    public <T> Mono<T> validateAndThrow(T body) {
        Set<ConstraintViolation<T>> violations = validator.validate(body);
        if (violations.isEmpty()) {
            return Mono.just(body);
        }
        String message =
                violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.joining(", "));
        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, message));
    }

    /**
     * 验证并映射请求体。
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
     * 验证并映射请求体（带自定义验证器）。
     *
     * @param request         ServerRequest 当前 HTTP 请求
     * @param clazz           请求体类型
     * @param customValidator 自定义验证函数
     * @param <T>             请求体泛型类型
     * @return 验证通过的请求体对象 Mono
     */
    public <T> Mono<T> validateAndMap(ServerRequest request, Class<T> clazz, Consumer<T> customValidator) {
        return handleRequestBody(request, clazz).doOnNext(customValidator);
    }

    /**
     * 构建成功响应。
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
     * 构建成功响应（Result 包装）。
     *
     * @param result Result 对象
     * @param <T>    响应数据类型
     * @return HTTP 200 JSON 响应的 Mono
     */
    public <T> Mono<ServerResponse> ok(Result<T> result) {
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(result);
    }

    /**
     * 构建错误响应。
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
     * 构建错误响应。
     *
     * @param resultCode 结果代码
     * @param message    错误消息
     * @return HTTP 400 JSON 错误响应的 Mono
     */
    public Mono<ServerResponse> buildErrorResponse(ResultCode resultCode, String message) {
        return buildErrorResponse(resultCode.getCode(), message);
    }

    /**
     * 构建 401 未授权错误响应。
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
     * 获取租户 ID。
     *
     * @param request ServerRequest 当前 HTTP 请求
     * @return 租户 ID
     */
    public String getTenantId(ServerRequest request) {
        return request.headers().firstHeader("X-Tenant-Id");
    }

    /**
     * 安全解析 Long 类型请求参数。
     *
     * <p>参数为空、空白或非数字格式时返回 {@code null}，避免直接使用
     * {@link Long#parseLong(String)} 导致 {@link NumberFormatException}。
     *
     * @param value 待解析的字符串
     * @return 解析成功返回对应 Long 值，否则返回 null
     */
    public static Long safeParseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 安全解析 Integer 类型请求参数。
     *
     * <p>参数为空、空白、非数字或负数时返回指定默认值。
     *
     * @param value        待解析的字符串
     * @param defaultValue 解析失败时返回的默认值
     * @return 解析成功返回对应 Integer 值，否则返回默认值
     */
    public static int safeParseInt(String value, int defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            int result = Integer.parseInt(value.trim());
            return result >= 0 ? result : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 统一错误处理。
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
