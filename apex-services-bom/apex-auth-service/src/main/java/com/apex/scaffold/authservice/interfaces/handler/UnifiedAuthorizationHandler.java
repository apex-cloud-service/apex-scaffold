package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.service.UnifiedAuthorizationQueryService;
import com.apex.scaffold.authservice.interfaces.dto.UnifiedAuthorizationRequest;
import com.apex.scaffold.authservice.interfaces.dto.UnifiedAuthorizationResponse;
import com.apex.scaffold.authservice.interfaces.util.RequestHandlerUtil;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 仅向有效专用 reader 提供当前 Token 租户的授权快照。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Component
@RequiredArgsConstructor
@Tag(name = "统一授权-内部快照", description = "仅供有效专用 reader 读取当前 Token 租户的授权快照")
public class UnifiedAuthorizationHandler {
    private final UnifiedAuthorizationQueryService service;
    private final Environment environment;
    private final RequestHandlerUtil responses;

    /**
     * 按路径 userId 查询授权快照，仅允许配置的专用 reader 账号访问。
     *
     * <p>租户从已认证主体的 details 获取，查询参数不能覆盖该作用域。
     * userId 必须为正整数；请求参数错误返回 400，reader 身份或访问权限不满足返回 403。
     *
     * @param request 包含目标 userId 路径参数的请求
     * @return Result 包装的授权快照或对应状态码的业务错误响应
     */
    @OperationLog(
            module = "UnifiedAuthorization",
            type = OperationLog.OperationType.SELECT,
            description = "query",
            saveRequest = false,
            saveResponse = false)
    @Operation(
            operationId = "queryUnifiedAuthorization",
            summary = "查询授权快照（reader 专用）",
            description = "对应 GET /auth/v1/authorization/users/{userId} 端点。仅允许配置的专用 reader 账号访问；"
                    + "租户从已认证主体的 details 获取，查询参数不能覆盖该作用域。"
                    + "userId 必须为正整数；请求参数错误返回 400，reader 身份或访问权限不满足返回 403")
    public Mono<ServerResponse> query(ServerRequest request) {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(auth -> auth.isAuthenticated() && auth.getDetails() instanceof String)
                .filter(auth ->
                        auth.getName().equals(environment.getProperty("auth-center.authorization-reader-user-id")))
                .flatMap(auth -> {
                    Long userId;
                    try {
                        userId = Long.valueOf(request.pathVariable("userId"));
                        if (userId <= 0) {
                            return error(400, ResultCode.PARAM_ERROR);
                        }
                    } catch (NumberFormatException error) {
                        return error(400, ResultCode.PARAM_ERROR);
                    }
                    Long readerId;
                    try {
                        readerId = Long.valueOf(auth.getName());
                    } catch (NumberFormatException invalidReader) {
                        return error(403, ResultCode.FORBIDDEN);
                    }
                    return responses
                            .validateAndMap(new UnifiedAuthorizationRequest(userId))
                            .flatMap(input ->
                                    service.queryForReader((String) auth.getDetails(), readerId, input.userId()))
                            .map(data -> new UnifiedAuthorizationResponse(
                                    data.tenantId(),
                                    data.userId(),
                                    data.permissions(),
                                    data.roles().stream()
                                            .map(role -> new UnifiedAuthorizationResponse.Role(
                                                    role.code(), role.dataScope()))
                                            .toList()))
                            .flatMap(responses::buildSuccessResponse);
                })
                .switchIfEmpty(Mono.defer(() -> error(403, ResultCode.FORBIDDEN)))
                .onErrorResume(
                        com.apex.scaffold.common.exception.BusinessException.class,
                        failure -> error(400, ResultCode.PARAM_ERROR))
                .onErrorResume(
                        org.springframework.security.access.AccessDeniedException.class,
                        failure -> error(403, ResultCode.FORBIDDEN));
    }

    /**
     * 使用指定 HTTP 状态和业务码构造统一错误响应。
     *
     * @param status HTTP 状态码
     * @param code 业务响应码
     * @return Result 包装的错误响应
     */
    private Mono<ServerResponse> error(int status, ResultCode code) {
        return ServerResponse.status(status).bodyValue(Result.error(code));
    }
}
