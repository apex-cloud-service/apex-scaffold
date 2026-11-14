package com.apex.scaffold.knowledgeservice.interfaces.util;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.common.util.TraceIdUtil;
import com.apex.scaffold.knowledgeservice.interfaces.security.AuthenticatedUser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 请求处理工具类：请求体校验、身份提取与统一响应/错误构建。
 *
 * <p>身份只取自已验证的 Spring Security 上下文（{@link AuthenticatedUser}），
 * 不读取 X-User-Id / X-Tenant-Id 等中继头；业务错误统一转换为
 * {@code Result{code,msg,traceId}} JSON 响应（HTTP 400 承载业务错误码）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRequestHandlerUtil {

    private final Validator validator;

    /**
     * 处理请求体并进行参数验证。
     *
     * @param request ServerRequest 当前 HTTP 请求
     * @param clazz 请求体类型
     * @param <T> 请求体泛型类型
     * @return 验证通过的请求体对象 Mono
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
     * 从安全上下文提取当前已认证用户（缺失即拒绝）。
     *
     * @return 已认证用户
     */
    public Mono<AuthenticatedUser> currentUser() {
        return ReactiveSecurityContextHolder.getContext()
                .flatMap(context -> Mono.justOrEmpty(context.getAuthentication()))
                .filter(authentication -> authentication.isAuthenticated())
                .map(authentication -> authentication.getPrincipal())
                .ofType(AuthenticatedUser.class)
                .filter(user -> user.userId() != null && user.userId() > 0)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "身份缺失，拒绝访问")));
    }

    /**
     * 构建成功响应。
     *
     * @param data 响应数据
     * @param <T> 响应数据泛型
     * @return HTTP 200 JSON 响应
     */
    public <T> Mono<ServerResponse> buildSuccessResponse(T data) {
        String traceId = TraceIdUtil.getTraceId();
        Result<T> result = Result.success(data, traceId);
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(result);
    }

    /**
     * 统一错误处理：业务异常保留错误码与消息，其余收敛为系统错误且不外泄内部细节。
     *
     * @param throwable 异常
     * @return 错误响应 Mono
     */
    public Mono<ServerResponse> handleError(Throwable throwable) {
        if (throwable instanceof BusinessException businessException) {
            String traceId = TraceIdUtil.getTraceId();
            Result<Void> result = Result.error(businessException.getCode(), businessException.getMessage(), traceId);
            return ServerResponse.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(result);
        }
        log.error("知识库接口处理失败", throwable);
        String traceId = TraceIdUtil.getTraceId();
        Result<Void> result = Result.error(ResultCode.ERROR, traceId);
        return ServerResponse.status(500)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(result);
    }

    /**
     * 安全解析 Long 类型请求参数。
     *
     * @param value 待解析字符串
     * @return 解析成功返回 Long，否则 null
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
     * @param value 待解析字符串
     * @param defaultValue 解析失败时的默认值
     * @return 解析成功返回对应值，否则返回默认值
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
}
