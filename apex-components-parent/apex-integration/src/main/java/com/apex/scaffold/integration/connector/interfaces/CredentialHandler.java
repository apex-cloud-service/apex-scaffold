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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "集成中心-凭据管理", description = "集成凭据登记与轮换（响应永不回显明文/密文）")
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
    @Operation(
            operationId = "integrationListCredentials",
            summary = "查询租户凭据列表",
            description = "返回凭据元数据列表（不含密文/明文），可按凭据类型过滤")
    @Parameter(
            name = "credentialType",
            in = ParameterIn.QUERY,
            required = false,
            description = "凭据类型过滤",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为凭据元数据列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CredentialResponse.class))),
        @ApiResponse(responseCode = "401", description = "缺少可信租户上下文"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 integration:credential:read 权限")
    })
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
    @Operation(
            operationId = "integrationGetCredential",
            summary = "按 ID 查询凭据元数据",
            description = "按凭据主键查询元数据（租户归属校验），不含密文/明文")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "凭据主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为凭据元数据",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CredentialResponse.class))),
        @ApiResponse(responseCode = "404", description = "凭据不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 integration:credential:read 权限")
    })
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
    @Operation(
            operationId = "integrationRegisterCredential",
            summary = "登记新凭据",
            description = "入参含明文 secret，落库前由应用服务加密；响应仅返回元数据，永不回显明文/密文")
    @RequestBody(
            description = "凭据登记请求（凭据标识、类型、明文 secret）",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CredentialRegisterRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "登记成功，data 为凭据元数据",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CredentialResponse.class))),
        @ApiResponse(responseCode = "400", description = "请求体为空或参数校验失败"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 integration:credential:manage 权限")
    })
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
    @Operation(
            operationId = "integrationRotateCredential",
            summary = "轮换凭据",
            description = "以新明文 secret 轮换既有凭据（落库前加密），响应仅返回元数据")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "凭据主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "凭据轮换请求（新明文 secret）",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CredentialRotateRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "轮换成功，data 为轮换后的凭据元数据",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = CredentialResponse.class))),
        @ApiResponse(responseCode = "400", description = "请求体为空或参数校验失败"),
        @ApiResponse(responseCode = "404", description = "凭据不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 integration:credential:manage 权限")
    })
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
    @Operation(operationId = "integrationDeleteCredential", summary = "删除凭据", description = "按主键逻辑删除（租户归属校验）")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "凭据主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功，data 为空"),
        @ApiResponse(responseCode = "404", description = "凭据不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 integration:credential:manage 权限")
    })
    @OperationLog(module = "integration", type = OperationLog.OperationType.DELETE, description = "删除集成凭据")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return IntegrationResponses.currentTenant(request)
                .flatMap(tenantId -> manageService.delete(tenantId, Long.valueOf(request.pathVariable("id"))))
                .then(ServerResponse.ok().bodyValue(Result.success()))
                .onErrorResume(IntegrationResponses::error);
    }
}
