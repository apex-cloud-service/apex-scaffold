package com.apex.scaffold.officeservice.interfaces.handler;

import com.apex.scaffold.common.annotation.IdempotentApi;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.office.application.OfficeAdminService;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.officeservice.interfaces.assembler.OfficeAssembler;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeAppRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeAppResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeBindingResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.OfficeBindingReviewRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 办公平台管理端接口处理器
 *
 * <p>提供办公平台应用配置管理和身份绑定审批的管理接口，所有操作均需要管理员权限。
 *
 * <p>功能模块：
 * <ul>
 *   <li>应用配置管理：查询、创建、更新、删除租户的平台应用配置</li>
 *   <li>绑定审批：查询、审批、拒绝、解绑用户与外部平台的身份绑定</li>
 * </ul>
 *
 * <p>安全保障：
 * <ul>
 *   <li>权限校验：所有接口配置 {@code @RequirePermission} 注解</li>
 *   <li>操作审计：写操作配置 {@code @OperationLog} 注解，不保存敏感信息</li>
 *   <li>幂等保护：写操作配置 {@code @IdempotentApi} 注解</li>
 *   <li>乐观锁：更新和删除操作使用版本号防止并发冲突</li>
 *   <li>租户隔离：所有操作限定在当前租户范围内</li>
 * </ul>
 *
 * <p>接口列表：
 * <ol>
 *   <li>GET /admin-api/v1/office/apps - 查询应用列表</li>
 *   <li>GET /admin-api/v1/office/apps/{platform} - 查询应用详情</li>
 *   <li>POST /admin-api/v1/office/apps/{platform} - 创建应用配置</li>
 *   <li>PUT /admin-api/v1/office/apps/{platform} - 更新应用配置</li>
 *   <li>DELETE /admin-api/v1/office/apps/{platform} - 删除应用配置</li>
 *   <li>GET /admin-api/v1/office/bindings - 查询绑定列表</li>
 *   <li>POST /admin-api/v1/office/bindings/{id}/review - 审批绑定</li>
 *   <li>POST /admin-api/v1/office/bindings/{id}/unbind - 解绑身份</li>
 * </ol>
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Component
@RequiredArgsConstructor
@Tag(name = "办公平台管理", description = "办公平台配置、绑定审批和解绑管理接口")
public class OfficeAdminHandler {
    private final OfficeAdminService service;
    private final OfficeAssembler assembler;

    /** 应用配置列表响应。 */
    @Schema(name = "OfficeAppListResult")
    public static class AppListResult extends Result<java.util.List<OfficeAppResponse>> {}

    /** 应用配置详情响应。 */
    @Schema(name = "OfficeAppResult")
    public static class AppResult extends Result<OfficeAppResponse> {}

    /** 管理操作布尔响应。 */
    @Schema(name = "OfficeAdminBooleanResult")
    public static class AdminBooleanResult extends Result<Boolean> {}

    /** 身份绑定列表响应。 */
    @Schema(name = "OfficeBindingListResult")
    public static class BindingListResult extends Result<java.util.List<OfficeBindingResponse>> {}

    /** 身份绑定分页响应。 */
    @Schema(name = "OfficeBindingPageResult")
    public static class BindingPageResult extends Result<PageResponse<OfficeBindingResponse>> {}

    /**
     * 查询当前租户的所有办公平台应用配置列表。
     *
     * <p>不分页，直接返回当前租户下的全部应用配置，经 {@link OfficeAssembler#toAppResponse}
     * 转换为视图后包装为 {@code Result}；无配置时返回空列表而非错误。
     *
     * @param request HTTP 请求，无业务参数（仅承载认证与租户上下文）
     * @return 200 响应，{@code data} 为应用配置列表；无数据时为空列表
     */
    @Operation(
            operationId = "listOfficeApps",
            summary = "查询办公平台应用列表",
            description = "查询当前租户的所有办公平台应用配置",
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "查询成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AppListResult.class)))
            })
    @RequirePermission("office:app:list")
    @OperationLog(
            module = "OfficeAdmin",
            type = OperationLog.OperationType.SELECT,
            description = "listApps",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listApps(ServerRequest request) {
        return service.listApps()
                .map(apps -> apps.stream().map(assembler::toAppResponse).toList())
                .flatMap(responses -> ServerResponse.ok().bodyValue(Result.success(responses)));
    }

    /**
     * 查询指定平台的应用配置详情。
     *
     * @param request HTTP 请求，路径参数包含平台类型
     * @return 应用配置响应
     */
    @Operation(
            operationId = "getOfficeApp",
            summary = "查询指定平台应用配置",
            description = "根据平台类型查询应用配置详情",
            parameters = {@Parameter(name = "platform", description = "平台类型", required = true, example = "FEISHU")},
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "查询成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AppResult.class)))
            })
    @RequirePermission("office:app:query")
    @OperationLog(
            module = "OfficeAdmin",
            type = OperationLog.OperationType.SELECT,
            description = "getApp",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getApp(ServerRequest request) {
        return parsePlatform(request)
                .flatMap(service::getApp)
                .map(assembler::toAppResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)));
    }

    /**
     * 创建新的办公平台应用配置。
     *
     * <p>先按路径参数解析平台类型（非法取值返回参数错误），再将请求体映射为应用配置命令并创建；
     * 写操作受 {@code @IdempotentApi} 幂等与 {@code @OperationLog} 审计保护，
     * 成功后返回新建配置视图。
     *
     * @param request HTTP 请求，路径参数含平台类型，请求体为应用配置
     * @return 200 响应，{@code data} 为创建成功的应用配置
     * @throws com.apex.scaffold.common.exception.BusinessException 平台类型非法或请求体校验失败时抛出
     */
    @Operation(
            operationId = "createOfficeApp",
            summary = "创建办公平台应用配置",
            description = "为当前租户创建新的办公平台应用配置，平台类型通过路径参数指定",
            parameters = {@Parameter(name = "platform", description = "平台类型", required = true, example = "FEISHU")},
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = OfficeAppRequest.class))),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "创建成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AppResult.class)))
            })
    @RequirePermission("office:app:create")
    @OperationLog(
            module = "办公平台",
            type = OperationLog.OperationType.INSERT,
            description = "创建应用配置",
            saveRequest = false,
            saveResponse = false)
    @IdempotentApi
    public Mono<ServerResponse> createApp(ServerRequest request) {
        return parsePlatform(request)
                .flatMap(platform -> request.bodyToMono(OfficeAppRequest.class)
                        .map(assembler::toCommand)
                        .flatMap(command -> service.createApp(platform, command)))
                .map(assembler::toAppResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)));
    }

    /**
     * 更新指定平台的应用配置。
     *
     * <p>平台类型取自路径参数，请求体须携带乐观锁版本号，版本不匹配时由应用服务拒绝；
     * 成功后返回更新后的配置视图，写操作受幂等与审计保护。
     *
     * @param request HTTP 请求，路径参数含平台类型，请求体为更新内容与乐观锁版本号
     * @return 200 响应，{@code data} 为更新后的应用配置
     * @throws com.apex.scaffold.common.exception.BusinessException 平台类型非法、版本冲突或配置不存在时抛出
     */
    @Operation(
            operationId = "updateOfficeApp",
            summary = "更新办公平台应用配置",
            description = "更新指定平台的应用配置信息，需要提供乐观锁版本号",
            parameters = {@Parameter(name = "platform", description = "平台类型", required = true, example = "FEISHU")},
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = OfficeAppRequest.class))),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "更新成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AppResult.class)))
            })
    @RequirePermission("office:app:update")
    @OperationLog(
            module = "办公平台",
            type = OperationLog.OperationType.UPDATE,
            description = "更新应用配置",
            saveRequest = false,
            saveResponse = false)
    @IdempotentApi
    public Mono<ServerResponse> updateApp(ServerRequest request) {
        return parsePlatform(request)
                .flatMap(platform -> request.bodyToMono(OfficeAppRequest.class)
                        .map(assembler::toCommand)
                        .flatMap(command -> service.updateApp(platform, command)))
                .map(assembler::toAppResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)));
    }

    /**
     * 删除指定平台的应用配置。
     *
     * <p>平台类型取自路径参数，乐观锁版本号取自查询参数；版本号缺省时传 {@code null}
     * 交由应用服务处理，删除成功返回布尔结果，写操作受幂等与审计保护。
     *
     * @param request HTTP 请求，路径参数含平台类型，查询参数可选 {@code version}
     * @return 200 响应，{@code data} 为是否删除成功
     * @throws com.apex.scaffold.common.exception.BusinessException 平台类型非法、版本冲突或配置不存在时抛出
     */
    @Operation(
            operationId = "deleteOfficeApp",
            summary = "删除办公平台应用配置",
            description = "删除指定平台的应用配置，需要提供乐观锁版本号",
            parameters = {
                @Parameter(name = "platform", description = "平台类型", required = true, example = "FEISHU"),
                @Parameter(name = "version", description = "乐观锁版本号", required = true, example = "0")
            },
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "删除成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AdminBooleanResult.class)))
            })
    @RequirePermission("office:app:delete")
    @OperationLog(
            module = "办公平台",
            type = OperationLog.OperationType.DELETE,
            description = "删除应用配置",
            saveRequest = false,
            saveResponse = false)
    @IdempotentApi
    public Mono<ServerResponse> deleteApp(ServerRequest request) {
        return parsePlatform(request)
                .flatMap(platform -> {
                    Integer version =
                            request.queryParam("version").map(Integer::parseInt).orElse(null);
                    return service.deleteApp(platform, version);
                })
                .flatMap(success -> ServerResponse.ok().bodyValue(Result.success(success)));
    }

    /**
     * 查询指定平台的身份绑定列表，支持分页。
     *
     * <p>平台类型取自查询参数（缺失或非法返回参数错误）；页码缺省 1、每页缺省 20，
     * 以列表形式返回该页绑定记录，不携带总数。
     *
     * @param request HTTP 请求，查询参数含平台类型与可选分页信息
     * @return 200 响应，{@code data} 为当前页绑定记录列表
     * @throws com.apex.scaffold.common.exception.BusinessException 平台参数缺失或非法时抛出
     */
    @Operation(
            operationId = "listOfficeBindings",
            summary = "查询办公平台绑定列表",
            description = "分页查询指定平台的身份绑定记录",
            parameters = {
                @Parameter(name = "platform", description = "平台类型", required = true, example = "FEISHU"),
                @Parameter(
                        name = "page",
                        description = "页码",
                        required = false,
                        example = "1",
                        schema = @Schema(type = "integer", defaultValue = "1")),
                @Parameter(
                        name = "size",
                        description = "每页数量",
                        required = false,
                        example = "20",
                        schema = @Schema(type = "integer", defaultValue = "20"))
            },
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "查询成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = BindingListResult.class)))
            })
    @RequirePermission("office:binding:list")
    @OperationLog(
            module = "OfficeAdmin",
            type = OperationLog.OperationType.SELECT,
            description = "listBindings",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listBindings(ServerRequest request) {
        return parsePlatformFromQuery(request)
                .flatMap(platform -> {
                    int page = request.queryParam("page").map(Integer::parseInt).orElse(1);
                    int size = request.queryParam("size").map(Integer::parseInt).orElse(20);
                    return service.listBindings(platform, page, size);
                })
                .map(bindings ->
                        bindings.stream().map(assembler::toBindingResponse).toList())
                .flatMap(responses -> ServerResponse.ok().bodyValue(Result.success(responses)));
    }

    /**
     * 分页查询指定平台的身份绑定列表，支持状态过滤并返回真实总数。
     *
     * <p>按状态过滤后把应用层分页结果重建为携带真实总数的 {@code PageResponse}；
     * 平台缺失或非法返回参数错误，页码缺省 1、每页缺省 20，状态缺省不过滤。
     *
     * @param request HTTP 请求，查询参数含平台类型、可选状态与分页信息
     * @return 200 响应，{@code data} 为带总数的绑定分页结果
     * @throws com.apex.scaffold.common.exception.BusinessException 平台参数缺失或非法时抛出
     */
    @Operation(
            operationId = "listOfficeBindingsPage",
            summary = "分页查询办公平台绑定列表",
            description = "分页查询指定平台的身份绑定记录，支持按状态过滤并返回真实总数",
            parameters = {
                @Parameter(name = "platform", description = "平台类型", required = true, example = "FEISHU"),
                @Parameter(
                        name = "status",
                        description = "绑定状态（PENDING/APPROVED/REJECTED/REVOKED）",
                        required = false,
                        example = "PENDING"),
                @Parameter(
                        name = "page",
                        description = "页码",
                        required = false,
                        example = "1",
                        schema = @Schema(type = "integer", defaultValue = "1")),
                @Parameter(
                        name = "size",
                        description = "每页数量",
                        required = false,
                        example = "20",
                        schema = @Schema(type = "integer", defaultValue = "20"))
            },
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "查询成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = BindingPageResult.class)))
            })
    @RequirePermission("office:binding:list")
    @OperationLog(
            module = "OfficeAdmin",
            type = OperationLog.OperationType.SELECT,
            description = "listBindingsPage",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listBindingsPage(ServerRequest request) {
        return parsePlatformFromQuery(request)
                .flatMap(platform -> {
                    String status = request.queryParam("status").orElse(null);
                    int page = request.queryParam("page").map(Integer::parseInt).orElse(1);
                    int size = request.queryParam("size").map(Integer::parseInt).orElse(20);
                    return service.listBindingsPage(platform, status, page, size);
                })
                .map(page -> PageResponse.of(
                        page.getList().stream()
                                .map(assembler::toBindingResponse)
                                .toList(),
                        page.getTotal(),
                        page.getPageNum(),
                        page.getPageSize()))
                .flatMap(responses -> ServerResponse.ok().bodyValue(Result.success(responses)));
    }

    /**
     * 审批身份绑定申请。
     *
     * <p>绑定主键取自路径参数，版本号与审批结论取自请求体；仅具备审批权限的管理员可调用，
     * 版本不匹配或状态不允许时由应用服务拒绝，成功返回布尔结果，写操作受幂等与审计保护。
     *
     * @param request HTTP 请求，路径参数含绑定主键，请求体为审批决定与乐观锁版本号
     * @return 200 响应，{@code data} 为是否审批成功
     * @throws com.apex.scaffold.common.exception.BusinessException 绑定不存在、版本冲突或状态非法时抛出
     * @throws NumberFormatException 路径中的绑定主键不是合法长整型时抛出
     */
    @Operation(
            operationId = "reviewOfficeBinding",
            summary = "审批身份绑定申请",
            description = "管理员审批用户的办公平台身份绑定申请，批准或拒绝",
            parameters = {@Parameter(name = "id", description = "绑定主键", required = true, example = "1001")},
            requestBody =
                    @io.swagger.v3.oas.annotations.parameters.RequestBody(
                            required = true,
                            content =
                                    @Content(
                                            mediaType = "application/json",
                                            schema = @Schema(implementation = OfficeBindingReviewRequest.class))),
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "审批成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AdminBooleanResult.class)))
            })
    @RequirePermission("office:binding:review")
    @OperationLog(
            module = "办公平台",
            type = OperationLog.OperationType.UPDATE,
            description = "审批身份绑定",
            saveRequest = false,
            saveResponse = false)
    @IdempotentApi
    public Mono<ServerResponse> reviewBinding(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        return request.bodyToMono(OfficeBindingReviewRequest.class)
                .flatMap(reviewRequest ->
                        service.reviewBinding(id, reviewRequest.getVersion(), reviewRequest.getApproved()))
                .flatMap(success -> ServerResponse.ok().bodyValue(Result.success(success)));
    }

    /**
     * 解绑办公平台身份。
     *
     * <p>绑定主键取自路径参数，乐观锁版本号取自查询参数；解绑后该身份不再满足办公平台登录
     * 所需的已审批绑定条件，成功返回布尔结果，写操作受幂等与审计保护。
     *
     * @param request HTTP 请求，路径参数含绑定主键，查询参数可选 {@code version}
     * @return 200 响应，{@code data} 为是否解绑成功
     * @throws com.apex.scaffold.common.exception.BusinessException 绑定不存在或版本冲突时抛出
     * @throws NumberFormatException 路径中的绑定主键不是合法长整型时抛出
     */
    @Operation(
            operationId = "unbindOfficeIdentity",
            summary = "解绑办公平台身份",
            description = "撤销用户的办公平台身份绑定，需要提供乐观锁版本号",
            parameters = {
                @Parameter(name = "id", description = "绑定主键", required = true, example = "1001"),
                @Parameter(name = "version", description = "乐观锁版本号", required = true, example = "0")
            },
            responses = {
                @ApiResponse(
                        responseCode = "200",
                        description = "解绑成功",
                        content =
                                @Content(
                                        mediaType = "application/json",
                                        schema = @Schema(implementation = AdminBooleanResult.class)))
            })
    @RequirePermission("office:binding:unbind")
    @OperationLog(
            module = "办公平台",
            type = OperationLog.OperationType.DELETE,
            description = "解绑身份",
            saveRequest = false,
            saveResponse = false)
    @IdempotentApi
    public Mono<ServerResponse> unbind(ServerRequest request) {
        Long id = Long.parseLong(request.pathVariable("id"));
        Integer version = request.queryParam("version").map(Integer::parseInt).orElse(null);
        return service.unbind(id, version)
                .flatMap(success -> ServerResponse.ok().bodyValue(Result.success(success)));
    }

    /**
     * 从请求路径中解析平台类型
     *
     * @param request HTTP 请求
     * @return 平台类型
     * @throws com.apex.scaffold.common.exception.BusinessException 平台参数无效时
     */
    private Mono<OfficePlatform> parsePlatform(ServerRequest request) {
        String value = request.pathVariable("platform");
        try {
            return Mono.just(OfficePlatform.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException error) {
            return Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                    com.apex.scaffold.common.result.ResultCode.PARAM_ERROR));
        }
    }

    /**
     * 从查询参数中解析平台类型
     *
     * @param request HTTP 请求
     * @return 平台类型
     * @throws com.apex.scaffold.common.exception.BusinessException 平台参数无效或缺失时
     */
    private Mono<OfficePlatform> parsePlatformFromQuery(ServerRequest request) {
        String value = request.queryParam("platform").orElse(null);
        if (value == null || value.isBlank()) {
            return Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                    com.apex.scaffold.common.result.ResultCode.PARAM_ERROR));
        }
        try {
            return Mono.just(OfficePlatform.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException error) {
            return Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                    com.apex.scaffold.common.result.ResultCode.PARAM_ERROR));
        }
    }
}
