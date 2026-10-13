package com.apex.scaffold.authservice.interfaces.handler;

import com.apex.scaffold.auth.application.service.UnifiedAuthorizationQueryService;
import com.apex.scaffold.authservice.interfaces.dto.UnifiedAuthorizationRequest;
import com.apex.scaffold.authservice.interfaces.dto.UnifiedAuthorizationResponse;
import com.apex.scaffold.authservice.interfaces.util.RequestHandlerUtil;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
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
public class UnifiedAuthorizationHandler {
    private final UnifiedAuthorizationQueryService service;
    private final Environment environment;
    private final RequestHandlerUtil responses;

    /**
     * 处理授权快照查询请求，仅允许专用 reader 账号访问
     *
     * @param request 请求对象
     * @return 授权快照响应
     */
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

    private Mono<ServerResponse> error(int status, ResultCode code) {
        return ServerResponse.status(status).bodyValue(Result.error(code));
    }
}
