package com.apex.scaffold.integration.connector.interfaces;

import com.apex.scaffold.common.context.TenantContext;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 集成接口层公共支撑（方案 M5 / 批次 E2）。
 *
 * <p>统一租户上下文提取（可信 Reactor Context，不接受客户端覆盖）、
 * Jakarta Validation 手动校验（函数式路由无自动校验）与异常到 HTTP
 * 响应的映射：安全类错误（401/403/404）映射对应 HTTP 状态，
 * 其余业务失败保持 HTTP 200 + Result 错误码。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
final class IntegrationResponses {

    private IntegrationResponses() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }

    /**
     * 读取当前可信租户（由宿主认证过滤器写入 Reactor Context）。
     *
     * @param request 当前请求
     * @return 租户编码；缺失时以 401 失败
     */
    static Mono<String> currentTenant(ServerRequest request) {
        return Mono.deferContextual(context -> {
            String tenant = context.getOrDefault(TenantContext.REACTOR_CONTEXT_KEY, null);
            if (tenant == null || tenant.isBlank()) {
                return Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "缺少可信租户上下文"));
            }
            return Mono.just(tenant);
        });
    }

    /**
     * 手动执行 Jakarta Validation 校验（函数式路由无声明式校验）。
     *
     * @param body      请求体
     * @param validator 校验器
     * @param <T>       请求体类型
     * @return 校验通过的请求体
     */
    static <T> Mono<T> validate(T body, Validator validator) {
        Set<ConstraintViolation<T>> violations = validator.validate(body);
        if (violations.isEmpty()) {
            return Mono.just(body);
        }
        String message = violations.iterator().next().getMessage();
        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, message));
    }

    /**
     * 异常映射为响应。
     *
     * @param error 异常
     * @return 错误响应
     */
    static Mono<ServerResponse> error(Throwable error) {
        if (error instanceof BusinessException businessException) {
            int code = businessException.getCode() == null ? ResultCode.ERROR.getCode() : businessException.getCode();
            return ServerResponse.status(httpStatus(code)).bodyValue(Result.error(code, businessException.getMsg()));
        }
        log.error("集成接口处理失败", error);
        return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .bodyValue(Result.error(ResultCode.ERROR.getCode(), ResultCode.ERROR.getMsg()));
    }

    private static HttpStatus httpStatus(int code) {
        return switch (code) {
            case 400, 40001 -> HttpStatus.BAD_REQUEST;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.OK;
        };
    }
}
