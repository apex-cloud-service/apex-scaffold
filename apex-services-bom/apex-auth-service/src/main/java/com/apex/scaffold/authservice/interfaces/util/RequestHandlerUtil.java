package com.apex.scaffold.authservice.interfaces.util;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
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
 *
 * @author Apex Scaffold Team
 * @since 1.3.1
 */
@Component
@RequiredArgsConstructor
public class RequestHandlerUtil {

    private final Validator validator;

    /**
     * 处理请求体并进行参数验证
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
     * 验证由路径参数构造的请求对象
     *
     * @param value 待验证的请求对象
     * @param <T>     请求体泛型类型
     * @return 验证通过的请求体对象 Mono
     */
    public <T> Mono<T> validateAndMap(T value) {
        return Mono.defer(() -> {
            Set<ConstraintViolation<T>> violations = validator.validate(value);
            if (!violations.isEmpty()) {
                return Mono.error(new BusinessException(ResultCode.PARAM_ERROR));
            }
            return Mono.just(value);
        });
    }

    /**
     * 验证并映射请求体
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
     * 构建成功响应
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
     * 构建错误响应
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
     * 构建错误响应
     *
     * @param resultCode 结果代码
     * @param message    错误消息
     * @return HTTP 400 JSON 错误响应的 Mono
     */
    public Mono<ServerResponse> buildErrorResponse(ResultCode resultCode, String message) {
        return buildErrorResponse(resultCode.getCode(), message);
    }

    /**
     * 统一错误处理。
     *
     * <p>认证和授权类业务码分别映射为 401 / 403，其余保持既有 HTTP 400 契约。
     *
     * @param throwable 异常
     * @return 错误响应 Mono
     */
    public Mono<ServerResponse> handleError(Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            String traceId = TraceIdUtil.getTraceId();
            Result<Void> result = Result.error(businessException.getCode(), businessException.getMessage(), traceId);
            org.springframework.http.HttpStatus status =
                    switch (businessException.getCode()) {
                        case 401 -> org.springframework.http.HttpStatus.UNAUTHORIZED;
                        case 403 -> org.springframework.http.HttpStatus.FORBIDDEN;
                        default -> org.springframework.http.HttpStatus.BAD_REQUEST;
                    };
            return ServerResponse.status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(result);
        }
        return buildErrorResponse(ResultCode.ERROR, throwable.getMessage());
    }
}
