package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.TenantActionRequest;
import com.apex.scaffold.application.dto.admin.TenantCreateRequest;
import com.apex.scaffold.application.dto.admin.TenantDTO;
import com.apex.scaffold.application.dto.admin.TenantPageQuery;
import com.apex.scaffold.application.dto.admin.TenantQuotaUpdateRequest;
import com.apex.scaffold.application.service.TenantApplicationService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 平台租户管理处理器（函数式端点）。
 *
 * <p>为平台管理员提供租户目录的分页查询、开通、详情、生命周期动作与配额管理
 * API。所有端点要求平台级 {@code tenant:*} 权限，租户管理员不具备这些权限，
 * 不得通过租户作用域访问平台目录。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>GET /api/v2/platform/tenants - 分页查询租户目录</li>
 *   <li>POST /api/v2/platform/tenants - 开通租户</li>
 *   <li>GET /api/v2/platform/tenants/{id} - 查询租户详情</li>
 *   <li>POST /api/v2/platform/tenants/{id}/actions - 执行状态动作</li>
 *   <li>PUT /api/v2/platform/tenants/{id}/quota - 更新租户配额</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 * @see TenantApplicationService
 */
@Component
@Tag(name = "后台-租户管理", description = "平台级租户目录、生命周期与配额管理")
public class PlatformTenantHandler extends BaseHandler {

    private static final int DEFAULT_PAGE_SIZE_LIMIT = 100;

    /** 租户应用服务。 */
    private final TenantApplicationService tenantService;

    /** 平台租户守卫（B8：平台专属入口的第二道边界） */
    private final com.apex.scaffold.application.service.TenantGuard tenantGuard;

    /**
     * 创建平台租户处理器。
     *
     * @param tenantService 租户应用服务
     * @param tenantGuard 平台租户守卫
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public PlatformTenantHandler(
            TenantApplicationService tenantService,
            com.apex.scaffold.application.service.TenantGuard tenantGuard,
            MessageUtils messageUtils,
            Validator validator) {
        super(messageUtils, validator);
        this.tenantService = tenantService;
        this.tenantGuard = tenantGuard;
    }

    /**
     * 分页查询租户目录。
     *
     * <p>支持按租户编码、名称模糊匹配与状态精确筛选。分页参数必填。</p>
     *
     * @param request HTTP 请求，查询参数包含 {@code pageNum}、{@code pageSize}、
     *                {@code tenantCode}、{@code tenantName}、{@code status}
     * @return 带 traceId 的统一成功响应，data 为租户分页结果
     * @throws BusinessException 当分页参数缺失或非法时抛出
     */
    @Operation(
            operationId = "platformPageTenants",
            summary = "分页查询租户目录",
            description = "平台管理员分页查询租户目录，支持按编码与名称模糊匹配、按状态精确筛选")
    @Parameter(
            name = "pageNum",
            description = "页码（从1开始）",
            required = true,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", minimum = "1", example = "1"))
    @Parameter(
            name = "pageSize",
            description = "每页数量（1-100）",
            required = true,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", minimum = "1", maximum = "100", example = "10"))
    @Parameter(
            name = "tenantCode",
            description = "租户编码（支持模糊查询）",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", example = "T001"))
    @Parameter(
            name = "tenantName",
            description = "租户名称（支持模糊查询）",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", example = "Acme"))
    @Parameter(
            name = "status",
            description = "租户状态（ACTIVE/DISABLED/FROZEN/DEACTIVATED/ARCHIVED）",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", example = "ACTIVE"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，分页参数必填且必须在允许范围内"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 tenant:read 权限")
    })
    @RequirePermission("tenant:read")
    @SuppressWarnings("PMD.MethodTooLongRule")
    @OperationLog(
            module = "租户管理",
            type = OperationLog.OperationType.SELECT,
            description = "分页查询租户目录",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> page(ServerRequest request) {
        return Mono.defer(() -> {
                    String pageNumStr = request.queryParam("pageNum").orElse(null);
                    String pageSizeStr = request.queryParam("pageSize").orElse(null);

                    if (pageNumStr == null || pageNumStr.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "pageNum 必须为正整数"));
                    }
                    if (pageSizeStr == null || pageSizeStr.isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "pageSize 必须为 1-100 之间的正整数"));
                    }

                    int pageNum = safeParseInt(pageNumStr, -1);
                    int pageSize = safeParseInt(pageSizeStr, -1);

                    if (pageNum < 1) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "pageNum 必须为正整数"));
                    }
                    if (pageSize < 1 || pageSize > DEFAULT_PAGE_SIZE_LIMIT) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "pageSize 必须为 1-100 之间的正整数"));
                    }

                    TenantPageQuery query = TenantPageQuery.builder()
                            .pageNum(pageNum)
                            .pageSize(pageSize)
                            .tenantCode(request.queryParam("tenantCode").orElse(null))
                            .tenantName(request.queryParam("tenantName").orElse(null))
                            .status(request.queryParam("status").orElse(null))
                            .build();

                    return tenantService.pageTenants(query);
                })
                .flatMap(this::buildSuccess);
    }

    /**
     * 查询租户详情。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为租户 DTO
     * @throws BusinessException 当 {@code id} 非法或租户不存在时抛出
     */
    @Operation(operationId = "platformGetTenant", summary = "查询租户详情", description = "根据租户 ID 查询租户基础信息、状态与配额")
    @Parameter(
            name = "id",
            description = "租户ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TenantDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，租户ID必须为正整数"),
        @ApiResponse(responseCode = "404", description = "租户不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 tenant:read 权限")
    })
    @RequirePermission("tenant:read")
    @OperationLog(
            module = "租户管理",
            type = OperationLog.OperationType.SELECT,
            description = "查询租户详情",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> detail(ServerRequest request) {
        return tenantService.getById(requirePositiveId(request)).flatMap(this::buildSuccess);
    }

    /**
     * 开通租户。
     *
     * <p>租户编码全局唯一；并发重复编码由数据库唯一约束兜底并返回业务错误。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为已开通的租户 DTO
     * @throws BusinessException 当请求体为空、校验失败或编码已存在时抛出
     */
    @Operation(
            operationId = "platformCreateTenant",
            summary = "开通租户",
            description = "创建新租户并设置初始配额；租户编码全局唯一，重复提交返回编码冲突错误")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "租户创建请求信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantCreateRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "开通成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TenantDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "409", description = "租户编码已存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 tenant:create 权限")
    })
    @RequirePermission("tenant:create")
    @OperationLog(
            module = "租户管理",
            type = OperationLog.OperationType.INSERT,
            description = "开通租户",
            saveRequest = true,
            saveResponse = true)
    public Mono<ServerResponse> create(ServerRequest request) {
        return tenantGuard.assertPlatformTenant().then(Mono.defer(() -> doCreate(request)));
    }

    private Mono<ServerResponse> doCreate(ServerRequest request) {
        return parseBody(request, TenantCreateRequest.class)
                .flatMap(tenantService::createTenant)
                .flatMap(this::buildSuccess);
    }

    /**
     * 执行租户状态动作。
     *
     * <p>动作取值 {@code disable/enable/freeze/unfreeze/deactivate/archive}，
     * 受领域状态机约束；{@code expectedVersion} 不匹配时返回版本冲突。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为更新后的租户 DTO
     * @throws BusinessException 当 {@code id} 非法、动作不支持、状态非法或版本冲突时抛出
     */
    @Operation(
            operationId = "platformChangeTenantStatus",
            summary = "执行租户状态动作",
            description = "统一执行停用/启用/冻结/解冻/注销/归档动作，支持乐观锁版本校验")
    @Parameter(
            name = "id",
            description = "租户ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "租户状态动作请求",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantActionRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "动作执行成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TenantDTO.class))),
        @ApiResponse(responseCode = "400", description = "动作不支持或参数错误"),
        @ApiResponse(responseCode = "404", description = "租户不存在"),
        @ApiResponse(responseCode = "409", description = "状态迁移非法或版本冲突"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 tenant:lifecycle 权限")
    })
    @RequirePermission("tenant:lifecycle")
    @OperationLog(
            module = "租户管理",
            type = OperationLog.OperationType.UPDATE,
            description = "执行租户状态动作",
            saveRequest = true,
            saveResponse = true)
    public Mono<ServerResponse> changeStatus(ServerRequest request) {
        return tenantGuard.assertPlatformTenant().then(Mono.defer(() -> doChangeStatus(request)));
    }

    private Mono<ServerResponse> doChangeStatus(ServerRequest request) {
        long tenantId = requirePositiveId(request);
        return parseBody(request, TenantActionRequest.class)
                .flatMap(body -> tenantService.changeStatus(tenantId, body.getAction(), body.getExpectedVersion()))
                .flatMap(this::buildSuccess);
    }

    /**
     * 更新租户配额。
     *
     * <p>{@code maxUsers} 与 {@code maxStorageMb} 传 null 表示不限制；
     * {@code expectedVersion} 不匹配时返回版本冲突。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为更新后的租户 DTO
     * @throws BusinessException 当 {@code id} 非法、参数校验失败或版本冲突时抛出
     */
    @Operation(
            operationId = "platformUpdateTenantQuota",
            summary = "更新租户配额",
            description = "更新租户最大用户数与最大存储容量，支持乐观锁版本校验")
    @Parameter(
            name = "id",
            description = "租户ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "租户配额更新请求",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TenantQuotaUpdateRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = TenantDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败"),
        @ApiResponse(responseCode = "404", description = "租户不存在"),
        @ApiResponse(responseCode = "409", description = "版本冲突"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要 tenant:quota 权限")
    })
    @RequirePermission("tenant:quota")
    @OperationLog(
            module = "租户管理",
            type = OperationLog.OperationType.UPDATE,
            description = "更新租户配额",
            saveRequest = true,
            saveResponse = true)
    public Mono<ServerResponse> updateQuota(ServerRequest request) {
        return tenantGuard.assertPlatformTenant().then(Mono.defer(() -> doUpdateQuota(request)));
    }

    private Mono<ServerResponse> doUpdateQuota(ServerRequest request) {
        long tenantId = requirePositiveId(request);
        return parseBody(request, TenantQuotaUpdateRequest.class)
                .flatMap(body -> tenantService.updateQuota(
                        tenantId, body.getMaxUsers(), body.getMaxStorageMb(), body.getExpectedVersion()))
                .flatMap(this::buildSuccess);
    }
}
