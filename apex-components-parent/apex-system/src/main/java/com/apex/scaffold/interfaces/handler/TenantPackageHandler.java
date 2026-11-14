package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.TenantPackageRequest;
import com.apex.scaffold.application.service.TenantPackageService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.model.entity.TenantPackageEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 租户套餐管理 Handler（方案批次 B6）。
 *
 * <p>承载 {@code /api/v2/tenant-packages} 套餐 CRUD：列表、详情、创建、更新
 * （菜单/权限/能力裁剪集合变更即时生效——既有用户权限随统一授权快照口径收敛）。
 * 平台管理接口：{@code default} 租户 ADMIN 专属权限码。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantPackageHandler {

    private final TenantPackageService tenantPackageService;

    /**
     * 查询套餐列表。
     *
     * @param request 当前请求
     * @return {@link Result} 封装的全部套餐（含菜单/权限/能力裁剪集合）
     */
    @Operation(
            operationId = "listTenantPackages",
            summary = "查询套餐列表",
            description = "返回全部租户套餐（含菜单/权限/能力裁剪集合）；需 tenant:package:read 权限")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的套餐列表",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TenantPackageEntity.class))),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 tenant:package:read 权限")
    })
    @RequirePermission("tenant:package:read")
    public Mono<ServerResponse> listPackages(ServerRequest request) {
        return tenantPackageService
                .listPackages()
                .collectList()
                .flatMap(packages -> ServerResponse.ok().bodyValue(Result.success(packages)))
                .onErrorResume(this::asError);
    }

    /**
     * 查询套餐详情。
     *
     * @param request 当前请求，套餐编码取自路径变量 {@code packageCode}
     * @return {@link Result} 封装的套餐详情；编码不存在时经异常边界返回 404
     */
    @Operation(operationId = "getTenantPackage", summary = "查询套餐详情", description = "按套餐编码查询单个套餐；未知编码返回 404")
    @Parameter(
            name = "packageCode",
            in = ParameterIn.PATH,
            required = true,
            description = "套餐编码",
            schema = @Schema(type = "string"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的套餐详情",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TenantPackageEntity.class))),
        @ApiResponse(responseCode = "404", description = "套餐不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 tenant:package:read 权限")
    })
    @RequirePermission("tenant:package:read")
    public Mono<ServerResponse> getPackage(ServerRequest request) {
        return tenantPackageService
                .getByCode(request.pathVariable("packageCode"))
                .flatMap(pkg -> ServerResponse.ok().bodyValue(Result.success(pkg)))
                .onErrorResume(this::asError);
    }

    /**
     * 创建套餐。
     *
     * @param request 当前请求，请求体为 {@link TenantPackageRequest}
     * @return {@link Result} 封装的已创建套餐；编码冲突或参数非法返回 400
     */
    @Operation(
            operationId = "createTenantPackage",
            summary = "创建套餐",
            description = "按请求体创建租户套餐；编码冲突或参数非法返回 400。变更即时生效，既有用户权限随统一授权快照口径收敛")
    @RequestBody(
            description = "套餐创建请求",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantPackageRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的已创建套餐",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TenantPackageEntity.class))),
        @ApiResponse(responseCode = "400", description = "编码冲突或参数非法"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 tenant:package:create 权限")
    })
    @OperationLog(module = "租户套餐", type = OperationLog.OperationType.INSERT, description = "创建租户套餐")
    @RequirePermission("tenant:package:create")
    public Mono<ServerResponse> createPackage(ServerRequest request) {
        return request.bodyToMono(TenantPackageRequest.class)
                .flatMap(body -> tenantPackageService.createPackage(toEntity(body)))
                .flatMap(pkg -> ServerResponse.ok().bodyValue(Result.success(pkg)))
                .onErrorResume(this::asError);
    }

    /**
     * 更新套餐。
     *
     * @param request 当前请求，请求体为 {@link TenantPackageRequest}
     * @return {@link Result} 封装的已更新套餐；编码不存在或参数非法返回 400
     */
    @Operation(
            operationId = "updateTenantPackage",
            summary = "更新套餐",
            description = "按请求体更新租户套餐（菜单/权限/能力裁剪集合变更即时生效）；编码不存在或参数非法返回 400")
    @RequestBody(
            description = "套餐更新请求",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantPackageRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Result 封装的已更新套餐",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TenantPackageEntity.class))),
        @ApiResponse(responseCode = "400", description = "套餐不存在或参数非法"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 tenant:package:update 权限")
    })
    @OperationLog(module = "租户套餐", type = OperationLog.OperationType.UPDATE, description = "更新租户套餐")
    @RequirePermission("tenant:package:update")
    public Mono<ServerResponse> updatePackage(ServerRequest request) {
        return request.bodyToMono(TenantPackageRequest.class)
                .flatMap(body -> tenantPackageService.updatePackage(toEntity(body)))
                .flatMap(pkg -> ServerResponse.ok().bodyValue(Result.success(pkg)))
                .onErrorResume(this::asError);
    }

    private TenantPackageEntity toEntity(TenantPackageRequest body) {
        return TenantPackageEntity.builder()
                .packageCode(body.getPackageCode())
                .packageName(body.getPackageName())
                .menuIds(body.getMenuIds())
                .permissionCodes(body.getPermissionCodes())
                .features(body.getFeatures())
                .status(body.getStatus() == null ? 1 : body.getStatus())
                .remark(body.getRemark())
                .build();
    }

    private Mono<ServerResponse> asError(Throwable error) {
        if (error instanceof BusinessException businessException) {
            int status = businessException.getCode() == ResultCode.NOT_FOUND.getCode() ? 404 : 400;
            return ServerResponse.status(status).bodyValue(Result.error(businessException.getMessage()));
        }
        log.error("套餐管理处理异常", error);
        return ServerResponse.status(500).bodyValue(Result.error(ResultCode.ERROR.getMsg()));
    }
}
