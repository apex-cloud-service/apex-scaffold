package com.apex.scaffold.officeservice.interfaces.handler.office;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.office.application.OfficeOrganizationSyncService;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.officeservice.interfaces.dto.office.organization.OrganizationMappingResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.organization.SyncOrganizationRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.organization.SyncOrganizationResponse;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 办公平台组织同步处理器
 *
 * <p>提供组织架构同步相关的 HTTP 接口处理，包括：
 * <ul>
 *   <li>同步部门：从外部平台同步部门信息</li>
 *   <li>同步成员：从外部平台同步成员信息</li>
 *   <li>查询映射：查询组织映射关系</li>
 *   <li>同步历史：查询同步任务历史</li>
 * </ul>
 *
 * <p>接口路径：/api/v1/office/organization
 *
 * <p>身份校验由办公服务安全过滤链执行。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "办公平台组织同步", description = "办公平台组织架构同步接口")
public class OfficeOrganizationHandler {

    private static final String SYNC_TYPE_DEPARTMENT = "DEPARTMENT";
    private static final String SYNC_TYPE_MEMBER = "MEMBER";

    private final OfficeOrganizationSyncService organizationSyncService;
    private final RequestHandlerUtil requestHandlerUtil;

    /** 组织同步执行结果的统一响应。 */
    @Schema(name = "OfficeOrganizationSyncResult")
    public static class OrganizationSyncResult extends Result<SyncOrganizationResponse> {}

    /** 组织映射详情的统一响应，不存在时data为空。 */
    @Schema(name = "OfficeOrganizationMappingResult")
    public static class OrganizationMappingResult extends Result<OrganizationMappingResponse> {}

    /** 组织映射列表的统一响应。 */
    @Schema(name = "OfficeOrganizationMappingListResult")
    public static class OrganizationMappingListResult extends Result<java.util.List<OrganizationMappingResponse>> {}

    /** 同步任务记录列表的统一响应，不包含分页总数。 */
    @Schema(name = "OfficeOrganizationTaskListResult")
    public static class OrganizationTaskListResult
            extends Result<java.util.List<com.apex.scaffold.office.domain.OfficeSyncTask>> {}

    /**
     * 同步组织（部门或成员）
     *
     * <p>根据请求参数从外部办公平台同步部门或成员信息到 Apex 系统。
     * 支持全量同步和增量同步。
     *
     * @param request HTTP 请求
     * @return 同步结果，包含任务ID、同步数量和状态消息
     */
    @RequirePermission("office:organization:sync")
    @Operation(
            operationId = "syncOrganization",
            summary = "同步组织",
            description = "从外部办公平台同步部门或成员信息，支持全量和增量同步",
            tags = {"办公平台组织同步"})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "组织同步请求参数",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SyncOrganizationRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "同步成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OrganizationSyncResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误或不支持的同步类型",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "同步失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeOrganization",
            type = OperationLog.OperationType.UPDATE,
            description = "syncOrganization",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> syncOrganization(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, SyncOrganizationRequest.class)
                .flatMap(req -> {
                    String tenantId = requestHandlerUtil.getTenantId(request);
                    OfficePlatform platform = OfficePlatform.valueOf(req.getPlatform());

                    Mono<SyncOrganizationResponse> syncMono;
                    if (SYNC_TYPE_DEPARTMENT.equals(req.getSyncType())) {
                        syncMono = organizationSyncService
                                .syncDepartments(tenantId, platform, req.getFullSync())
                                .map(this::toSyncResponse);
                    } else if (SYNC_TYPE_MEMBER.equals(req.getSyncType())) {
                        syncMono = organizationSyncService
                                .syncMembers(tenantId, platform, req.getFullSync())
                                .map(this::toSyncResponse);
                    } else {
                        return ServerResponse.badRequest().bodyValue(Result.error("不支持的同步类型：" + req.getSyncType()));
                    }

                    return syncMono.flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)));
                })
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询部门映射
     *
     * <p>根据外部部门ID查询对应的 Apex 部门映射关系。
     *
     * @param request HTTP 请求
     * @return 组织映射信息
     */
    @RequirePermission("office:organization:query")
    @Operation(
            operationId = "getDepartmentMapping",
            summary = "查询部门映射",
            description = "根据外部部门ID查询对应的Apex部门映射关系",
            tags = {"办公平台组织同步"})
    @Parameters({
        @Parameter(
                name = "platform",
                in = ParameterIn.PATH,
                required = true,
                description = "办公平台类型",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "externalOrgId",
                in = ParameterIn.PATH,
                required = true,
                description = "外部部门ID",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OrganizationMappingResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "查询失败；映射不存在时返回200且data为空",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeOrganization",
            type = OperationLog.OperationType.SELECT,
            description = "getDepartmentMapping",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getDepartmentMapping(ServerRequest request) {
        String tenantId = requestHandlerUtil.getTenantId(request);
        String platform = request.pathVariable("platform");
        String externalOrgId = request.pathVariable("externalOrgId");

        return organizationSyncService
                .getDepartmentMapping(tenantId, OfficePlatform.valueOf(platform), externalOrgId)
                .map(this::toMappingResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .switchIfEmpty(ServerResponse.ok().bodyValue(Result.success(null)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询成员映射
     *
     * <p>根据外部用户ID查询对应的 Apex 用户映射关系。
     *
     * @param request HTTP 请求
     * @return 组织映射信息
     */
    @RequirePermission("office:organization:query")
    @Operation(
            operationId = "getMemberMapping",
            summary = "查询成员映射",
            description = "根据外部用户ID查询对应的Apex用户映射关系",
            tags = {"办公平台组织同步"})
    @Parameters({
        @Parameter(
                name = "platform",
                in = ParameterIn.PATH,
                required = true,
                description = "办公平台类型",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "externalUserId",
                in = ParameterIn.PATH,
                required = true,
                description = "外部用户ID",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OrganizationMappingResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "查询失败；映射不存在时返回200且data为空",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @OperationLog(
            module = "OfficeOrganization",
            type = OperationLog.OperationType.SELECT,
            description = "getMemberMapping",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getMemberMapping(ServerRequest request) {
        String tenantId = requestHandlerUtil.getTenantId(request);
        String platform = request.pathVariable("platform");
        String externalUserId = request.pathVariable("externalUserId");

        return organizationSyncService
                .getMemberMapping(tenantId, OfficePlatform.valueOf(platform), externalUserId)
                .map(this::toMappingResponse)
                .flatMap(response -> ServerResponse.ok().bodyValue(Result.success(response)))
                .switchIfEmpty(ServerResponse.ok().bodyValue(Result.success(null)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询组织映射列表
     *
     * <p>查询指定平台的所有组织映射关系，可用于核对和调试。
     *
     * @param request HTTP 请求
     * @return 组织映射列表
     */
    @RequirePermission("office:organization:list")
    @Operation(
            operationId = "listOrganizationMappings",
            summary = "查询组织映射列表",
            description = "查询指定平台的所有组织映射关系",
            tags = {"办公平台组织同步"})
    @Parameters({
        @Parameter(
                name = "platform",
                in = ParameterIn.QUERY,
                required = false,
                description = "办公平台类型（可选，不传则查询所有平台）",
                schema = @Schema(type = "string"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OrganizationMappingListResult.class)))
    })
    @OperationLog(
            module = "OfficeOrganization",
            type = OperationLog.OperationType.SELECT,
            description = "listOrganizationMappings",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listOrganizationMappings(ServerRequest request) {
        String tenantId = requestHandlerUtil.getTenantId(request);
        String platformParam = request.queryParam("platform").orElse(null);

        OfficePlatform platform = platformParam != null ? OfficePlatform.valueOf(platformParam) : null;

        return organizationSyncService
                .listOrganizationMappings(tenantId, platform)
                .map(list -> list.stream().map(this::toMappingResponse).collect(Collectors.toList()))
                .flatMap(responses -> ServerResponse.ok().bodyValue(Result.success(responses)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询同步任务列表
     *
     * <p>查询历史同步任务记录，包含任务状态、同步时间、游标等信息。
     * 支持分页查询。
     *
     * @param request HTTP 请求
     * @return 同步任务列表
     */
    @RequirePermission("office:sync:read")
    @Operation(
            operationId = "listSyncTasks",
            summary = "查询同步任务列表",
            description = "查询历史同步任务记录，支持分页",
            tags = {"办公平台组织同步"})
    @Parameters({
        @Parameter(
                name = "platform",
                in = ParameterIn.QUERY,
                required = false,
                description = "办公平台类型（可选，不传则查询所有平台）",
                schema = @Schema(type = "string")),
        @Parameter(
                name = "page",
                in = ParameterIn.QUERY,
                required = false,
                description = "页码（默认为1）",
                schema = @Schema(type = "integer", defaultValue = "1")),
        @Parameter(
                name = "size",
                in = ParameterIn.QUERY,
                required = false,
                description = "每页数量（默认为20）",
                schema = @Schema(type = "integer", defaultValue = "20"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OrganizationTaskListResult.class)))
    })
    @OperationLog(
            module = "OfficeOrganization",
            type = OperationLog.OperationType.SELECT,
            description = "listSyncTasks",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listSyncTasks(ServerRequest request) {
        String tenantId = requestHandlerUtil.getTenantId(request);
        String platformParam = request.queryParam("platform").orElse(null);
        int page = Integer.parseInt(request.queryParam("page").orElse("1"));
        int size = Integer.parseInt(request.queryParam("size").orElse("20"));

        OfficePlatform platform = platformParam != null ? OfficePlatform.valueOf(platformParam) : null;

        return organizationSyncService
                .listSyncTasks(tenantId, platform, page, size)
                .flatMap(tasks -> ServerResponse.ok().bodyValue(Result.success(tasks)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    // ========== 私有方法：对象转换 ==========

    private SyncOrganizationResponse toSyncResponse(OfficeOrganizationSyncService.SyncTaskResult result) {
        return SyncOrganizationResponse.builder()
                .taskId(result.taskId())
                .syncedCount(result.syncedCount())
                .message(result.message())
                .success(result.success())
                .build();
    }

    private OrganizationMappingResponse toMappingResponse(
            com.apex.scaffold.office.domain.OfficeOrganizationMapping mapping) {
        return OrganizationMappingResponse.builder()
                .id(mapping.getId())
                .platform(mapping.getPlatform().name())
                .externalOrgId(mapping.getExternalOrgId())
                .externalUserId(mapping.getExternalUserId())
                .apexDeptId(mapping.getApexDeptId())
                .apexUserId(mapping.getApexUserId())
                .createdAt(mapping.getCreatedAt())
                .updatedAt(mapping.getUpdatedAt())
                .build();
    }
}
