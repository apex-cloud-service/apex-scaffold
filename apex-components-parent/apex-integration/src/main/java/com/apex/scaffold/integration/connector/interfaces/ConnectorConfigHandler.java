package com.apex.scaffold.integration.connector.interfaces;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.integration.connector.application.ConnectorConfigManageService;
import com.apex.scaffold.integration.connector.interfaces.dto.ConnectorConfigCreateRequest;
import com.apex.scaffold.integration.connector.interfaces.dto.ConnectorConfigResponse;
import com.apex.scaffold.integration.connector.interfaces.dto.ConnectorConfigUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
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
 * 连接器配置管理 Handler（方案 M5 / 批次 E2，C7 标准管理接口）。
 *
 * <p>租户始终取可信上下文；{@code @RequirePermission} 为元数据声明，
 * 由宿主安全切面执行。响应不包含任何明文凭据（配置值中的敏感项本就以凭据引用表达）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "集成中心-连接器配置", description = "连接器配置管理（响应不含明文凭据）")
public class ConnectorConfigHandler {

    private static final String PAGE_NUM_DEFAULT = "1";

    private static final String PAGE_SIZE_DEFAULT = "20";

    private final ConnectorConfigManageService manageService;

    private final Validator validator;

    /**
     * 分页查询连接器配置。
     *
     * @param request 当前请求（query: connectorType/pageNum/pageSize）
     * @return 分页结果
     */
    @RequirePermission("integration:connector:read")
    @Operation(
            operationId = "integrationListConnectorConfigs",
            summary = "分页查询连接器配置",
            description = "按可信租户上下文分页查询，可按连接器类型过滤；响应不含任何明文凭据")
    @Parameters({
        @Parameter(
                name = "connectorType",
                in = ParameterIn.QUERY,
                required = false,
                description = "连接器类型过滤",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "pageNum",
                in = ParameterIn.QUERY,
                required = false,
                description = "页码，从 1 开始",
                schema = @Schema(type = "integer")),
        @Parameter(
                name = "pageSize",
                in = ParameterIn.QUERY,
                required = false,
                description = "每页条数（服务端上限 100）",
                schema = @Schema(type = "integer"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为连接器配置分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "缺少可信租户上下文"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 integration:connector:read 权限")
    })
    public Mono<ServerResponse> page(ServerRequest request) {
        String connectorType = request.queryParam("connectorType").orElse(null);
        int pageNum = parseIntOrDefault(request.queryParam("pageNum").orElse(PAGE_NUM_DEFAULT), 1);
        int pageSize = parseIntOrDefault(request.queryParam("pageSize").orElse(PAGE_SIZE_DEFAULT), 20);
        return IntegrationResponses.currentTenant(request)
                .flatMap(tenantId -> manageService.page(tenantId, connectorType, pageNum, pageSize))
                .flatMap(page -> ServerResponse.ok().bodyValue(Result.success(page)))
                .onErrorResume(IntegrationResponses::error);
    }

    /**
     * 按 ID 查询配置。
     *
     * @param request 当前请求（path: id）
     * @return 配置详情
     */
    @RequirePermission("integration:connector:read")
    @Operation(
            operationId = "integrationGetConnectorConfig",
            summary = "按 ID 查询连接器配置",
            description = "按配置主键查询详情（租户归属校验），响应不含明文凭据")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "配置主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为配置详情",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ConnectorConfigResponse.class))),
        @ApiResponse(responseCode = "404", description = "配置不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 integration:connector:read 权限")
    })
    public Mono<ServerResponse> get(ServerRequest request) {
        return IntegrationResponses.currentTenant(request)
                .flatMap(tenantId -> manageService
                        .get(tenantId, Long.valueOf(request.pathVariable("id")))
                        .map(ConnectorConfigResponse::from))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IntegrationResponses::error);
    }

    /**
     * 新增连接器配置。
     *
     * @param request 当前请求（JSON body）
     * @return 落库后的配置
     */
    @RequirePermission("integration:connector:create")
    @Operation(
            operationId = "integrationCreateConnectorConfig",
            summary = "新增连接器配置",
            description = "新增连接器配置；配置值中的敏感项以凭据引用表达，落库不存明文")
    @RequestBody(
            description = "连接器配置创建请求",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConnectorConfigCreateRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功，data 为落库后的配置",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ConnectorConfigResponse.class))),
        @ApiResponse(responseCode = "400", description = "请求体为空或参数校验失败"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 integration:connector:create 权限")
    })
    @OperationLog(module = "integration", type = OperationLog.OperationType.INSERT, description = "新增连接器配置")
    public Mono<ServerResponse> create(ServerRequest request) {
        return request.bodyToMono(ConnectorConfigCreateRequest.class)
                .switchIfEmpty(Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                        com.apex.scaffold.common.result.ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(body -> IntegrationResponses.validate(body, validator))
                .flatMap(body -> IntegrationResponses.currentTenant(request).flatMap(tenantId -> manageService
                        .create(
                                tenantId,
                                body.getConnectorType(),
                                body.getConfigKey(),
                                body.getConfigValue(),
                                body.getRemark())
                        .map(ConnectorConfigResponse::from)))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IntegrationResponses::error);
    }

    /**
     * 更新连接器配置。
     *
     * @param request 当前请求（path: id，JSON body）
     * @return 更新后的配置
     */
    @RequirePermission("integration:connector:update")
    @Operation(
            operationId = "integrationUpdateConnectorConfig",
            summary = "更新连接器配置",
            description = "按主键更新配置值、备注与乐观锁版本；版本不匹配返回冲突")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "配置主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "连接器配置更新请求（配置值、备注、version 乐观锁）",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConnectorConfigUpdateRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功，data 为更新后的配置",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ConnectorConfigResponse.class))),
        @ApiResponse(responseCode = "400", description = "请求体为空或参数校验失败"),
        @ApiResponse(responseCode = "404", description = "配置不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 integration:connector:update 权限")
    })
    @OperationLog(module = "integration", type = OperationLog.OperationType.UPDATE, description = "更新连接器配置")
    public Mono<ServerResponse> update(ServerRequest request) {
        return request.bodyToMono(ConnectorConfigUpdateRequest.class)
                .switchIfEmpty(Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                        com.apex.scaffold.common.result.ResultCode.PARAM_ERROR, "请求体不能为空")))
                .flatMap(body -> IntegrationResponses.validate(body, validator))
                .flatMap(body -> IntegrationResponses.currentTenant(request).flatMap(tenantId -> manageService
                        .update(
                                tenantId,
                                Long.valueOf(request.pathVariable("id")),
                                body.getConfigValue(),
                                body.getRemark(),
                                body.getVersion())
                        .map(ConnectorConfigResponse::from)))
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .onErrorResume(IntegrationResponses::error);
    }

    /**
     * 删除连接器配置（逻辑删除）。
     *
     * @param request 当前请求（path: id）
     * @return 完成信号
     */
    @RequirePermission("integration:connector:delete")
    @Operation(operationId = "integrationDeleteConnectorConfig", summary = "删除连接器配置", description = "按主键逻辑删除（租户归属校验）")
    @Parameter(
            name = "id",
            in = ParameterIn.PATH,
            required = true,
            description = "配置主键 ID",
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功，data 为空"),
        @ApiResponse(responseCode = "404", description = "配置不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 integration:connector:delete 权限")
    })
    @OperationLog(module = "integration", type = OperationLog.OperationType.DELETE, description = "删除连接器配置")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return IntegrationResponses.currentTenant(request)
                .flatMap(tenantId -> manageService.delete(tenantId, Long.valueOf(request.pathVariable("id"))))
                .then(ServerResponse.ok().bodyValue(Result.success()))
                .onErrorResume(IntegrationResponses::error);
    }

    private int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
