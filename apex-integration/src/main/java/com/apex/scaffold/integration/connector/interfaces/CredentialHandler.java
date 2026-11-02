package com.apex.scaffold.integration.connector.interfaces;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.integration.connector.application.CredentialManageService;
import com.apex.scaffold.integration.connector.interfaces.dto.CredentialRegisterRequest;
import com.apex.scaffold.integration.connector.interfaces.dto.CredentialResponse;
import com.apex.scaffold.integration.connector.interfaces.dto.CredentialRotateRequest;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 凭据管理 Handler（方案 M5 / 批次 E2，C7 标准管理接口）。
 *
 * <p>安全口径：写操作只收明文入参，落库前由应用服务加密；响应经
 * {@link CredentialResponse#from} 裁剪密文/IV，<b>永不回显明文</b>；
 * 日志只记录凭据标识与版本等元数据。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CredentialHandler {

    private final CredentialManageService manageService;

    private final Validator validator;

    /**
     * 查询租户凭据列表。
     *
     * @param request 当前请求（query: credentialType）
     * @return 凭据元数据列表（不含密文/明文）
     */
    @RequirePermission("integration:credential:read")
    public Mono<ServerResponse> list(ServerRequest request) {
        String credentialType = request.queryParam("credentialType").orElse(null);
        return IntegrationResponses.currentTenant(request)
                .flatMap(tenantId -> manageService.list(tenantId, credentialType))
                .flatMap(credentials -> ServerResponse.ok()
                        .bodyValue(Result.success(credentials.stream()
                                .map(CredentialResponse::from)
                                .toList())))
                .onErrorResume(IntegrationResponses::error);
    }

    /**
     * 按 ID 查询凭据元数据。
     *
     * @param request 当前请求（path: id）
     * @return 凭据元数据（不含密文/明文）
     */
    @RequirePermission("integration:credential:read")
    public Mono<ServerResponse> get(ServerRequest request) {
        return IntegrationResponses.currentTenant(request)
                .flatMap(tenantId -> manageService
                        .get(tenantId, Long.valueOf(request.pathVariable("id")))
                        .map(CredentialResponse::from))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IntegrationResponses::error);
    }

    /**
     * 登记新凭据。
     *
     * @param request 当前请求（JSON body，含明文 secret）
     * @return 凭据元数据（不含密文/明文）
     */
    @RequirePermission("integration:credential:manage")
    @OperationLog(
            module = "integration",
            type = OperationLog.OperationType.INSERT,
            description = "登记集成凭据",
            saveRequest = false)
    public Mono<ServerResponse> register(ServerRequest request) {
        return request.bodyToMono(CredentialRegisterRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(body -> IntegrationResponses.validate(body, validator))
                .flatMap(body -> IntegrationResponses.currentTenant(request).flatMap(tenantId -> manageService
                        .register(tenantId, body.getCredentialKey(), body.getCredentialType(), body.getSecret())
                        .map(CredentialResponse::from)))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IntegrationResponses::error);
    }

    /**
     * 轮换凭据。
     *
     * @param request 当前请求（path: id，JSON body，含新明文 secret）
     * @return 轮换后的凭据元数据
     */
    @RequirePermission("integration:credential:manage")
    @OperationLog(
            module = "integration",
            type = OperationLog.OperationType.UPDATE,
            description = "轮换集成凭据",
            saveRequest = false)
    public Mono<ServerResponse> rotate(ServerRequest request) {
        return request.bodyToMono(CredentialRotateRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(body -> IntegrationResponses.validate(body, validator))
                .flatMap(body -> IntegrationResponses.currentTenant(request).flatMap(tenantId -> manageService
                        .rotate(tenantId, Long.valueOf(request.pathVariable("id")), body.getSecret())
                        .map(CredentialResponse::from)))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IntegrationResponses::error);
    }

    /**
     * 删除凭据（逻辑删除）。
     *
     * @param request 当前请求（path: id）
     * @return 完成信号
     */
    @RequirePermission("integration:credential:manage")
    @OperationLog(module = "integration", type = OperationLog.OperationType.DELETE, description = "删除集成凭据")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return IntegrationResponses.currentTenant(request)
                .flatMap(tenantId -> manageService.delete(tenantId, Long.valueOf(request.pathVariable("id"))))
                .then(ServerResponse.ok().bodyValue(Result.success()))
                .onErrorResume(IntegrationResponses::error);
    }
}
