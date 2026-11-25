package com.apex.scaffold.officeservice.interfaces.handler.office;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.application.OfficeOrganizationSyncService;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.OfficeSyncTask;
import com.apex.scaffold.officeservice.interfaces.dto.office.organization.SyncOrganizationRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.organization.SyncOrganizationResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.organization.SyncPreflightRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.organization.SyncPreviewRequest;
import com.apex.scaffold.officeservice.interfaces.security.AuthenticatedUser;
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
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 办公平台组织同步向导处理器（v2）
 *
 * <p>提供引导式组织同步的 HTTP 接口，包括：
 * <ul>
 *   <li>预检：校验平台应用配置与凭据环境变量是否就绪</li>
 *   <li>预览：只读拉取首页数据，对比已有映射估算差异</li>
 *   <li>任务：创建同步任务、查询任务列表与详情</li>
 *   <li>重试：失败任务按同步类型从检查点增量续传</li>
 * </ul>
 *
 * <p>接口路径：/api/v2/office/sync
 *
 * <p>身份校验由办公服务安全过滤链执行；租户一律取自已验签 JWT 的安全上下文
 * （{@link AuthenticatedUser}），不接受客户端请求头。读接口要求 {@code office:sync:read}，
 * 执行接口要求 {@code office:sync:execute}（权限种子见 system V65 迁移）。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "办公平台组织同步向导", description = "办公平台组织同步引导式接口（v2）")
public class OfficeSyncWizardHandler {

    private final OfficeOrganizationSyncService organizationSyncService;
    private final RequestHandlerUtil requestHandlerUtil;

    /** 同步预检结果的统一响应。 */
    @Schema(name = "OfficeSyncPreflightResult")
    public static class PreflightResultResponse extends Result<OfficeOrganizationSyncService.PreflightResult> {}

    /** 同步预览结果的统一响应。 */
    @Schema(name = "OfficeSyncPreviewResult")
    public static class PreviewResultResponse extends Result<OfficeOrganizationSyncService.SyncPreviewResult> {}

    /** 同步任务执行结果的统一响应。 */
    @Schema(name = "OfficeSyncTaskExecuteResult")
    public static class TaskExecuteResult extends Result<SyncOrganizationResponse> {}

    /** 同步任务详情的统一响应。 */
    @Schema(name = "OfficeSyncTaskDetailResult")
    public static class TaskDetailResult extends Result<OfficeSyncTask> {}

    /** 同步任务列表的统一响应。 */
    @Schema(name = "OfficeSyncTaskListResult")
    public static class TaskListResult extends Result<List<OfficeSyncTask>> {}

    /**
     * 同步预检
     *
     * <p>租户取自认证上下文，平台经 {@code parsePlatform} 解析（非法值转参数错误）；校验平台应用
     * 配置与凭据环境变量是否就绪，返回是否可执行同步及缺失项。
     *
     * @param request HTTP 请求，请求体为预检参数
     * @return 200 响应，{@code data} 为是否就绪与缺失项清单
     */
    @Operation(
            operationId = "syncPreflight",
            summary = "同步预检",
            description = "校验平台应用配置与凭据环境变量是否就绪",
            tags = {"办公平台组织同步向导"})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "预检请求参数",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SyncPreflightRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "预检完成",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PreflightResultResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:sync:read")
    @OperationLog(
            module = "OfficeSyncWizard",
            type = OperationLog.OperationType.SELECT,
            description = "syncPreflight",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> preflight(ServerRequest request) {
        return trustedTenantId(request)
                .flatMap(tenantId -> requestHandlerUtil
                        .validateAndMap(request, SyncPreflightRequest.class)
                        .flatMap(req -> organizationSyncService
                                .preflight(tenantId, parsePlatform(req.getPlatform()))
                                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)))))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 同步预览
     *
     * <p>租户取自认证上下文，同步类型仅支持部门或成员（其他值直接返回 400 错误响应）；
     * 平台经 {@code parsePlatform} 解析，只读拉取首页数据并与已有映射对比估算差异。
     *
     * @param request HTTP 请求，请求体为预览参数
     * @return 200 响应，{@code data} 为首页样本数与差异估算；不支持的同步类型或参数错误返回 400
     */
    @Operation(
            operationId = "syncPreview",
            summary = "同步预览",
            description = "只读拉取首页数据并与已有映射对比，估算同步差异",
            tags = {"办公平台组织同步向导"})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "预览请求参数",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SyncPreviewRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "预览成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PreviewResultResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误或不支持的同步类型",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:sync:read")
    @OperationLog(
            module = "OfficeSyncWizard",
            type = OperationLog.OperationType.SELECT,
            description = "syncPreview",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> preview(ServerRequest request) {
        return trustedTenantId(request)
                .flatMap(tenantId -> requestHandlerUtil
                        .validateAndMap(request, SyncPreviewRequest.class)
                        .flatMap(req -> {
                            if (!OfficeOrganizationSyncService.SYNC_TYPE_DEPARTMENT.equals(req.getSyncType())
                                    && !OfficeOrganizationSyncService.SYNC_TYPE_MEMBER.equals(req.getSyncType())) {
                                return ServerResponse.badRequest()
                                        .bodyValue(Result.error("不支持的同步类型：" + req.getSyncType()));
                            }
                            return organizationSyncService
                                    .previewSync(tenantId, parsePlatform(req.getPlatform()), req.getSyncType())
                                    .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
                        }))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 创建同步任务
     *
     * <p>租户取自认证上下文，平台经 {@code parsePlatform} 解析；同步类型为部门或成员时分别触发
     * 对应同步并返回执行结果，其他同步类型直接返回 400 错误响应。
     *
     * @param request HTTP 请求，请求体为同步任务参数
     * @return 200 响应，{@code data} 为任务 ID、同步数量与状态消息；不支持的同步类型或失败时返回 400
     */
    @Operation(
            operationId = "createSyncTask",
            summary = "创建同步任务",
            description = "创建并执行部门或成员同步任务，支持全量和增量同步",
            tags = {"办公平台组织同步向导"})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "同步任务请求参数",
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
                                schema = @Schema(implementation = TaskExecuteResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误或不支持的同步类型",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "同步失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:sync:execute")
    @OperationLog(
            module = "OfficeSyncWizard",
            type = OperationLog.OperationType.UPDATE,
            description = "createSyncTask",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> createTask(ServerRequest request) {
        return trustedTenantId(request)
                .flatMap(tenantId -> requestHandlerUtil
                        .validateAndMap(request, SyncOrganizationRequest.class)
                        .flatMap(req -> {
                            OfficePlatform platform = parsePlatform(req.getPlatform());

                            Mono<OfficeOrganizationSyncService.SyncTaskResult> syncMono;
                            if (OfficeOrganizationSyncService.SYNC_TYPE_DEPARTMENT.equals(req.getSyncType())) {
                                syncMono =
                                        organizationSyncService.syncDepartments(tenantId, platform, req.getFullSync());
                            } else if (OfficeOrganizationSyncService.SYNC_TYPE_MEMBER.equals(req.getSyncType())) {
                                syncMono = organizationSyncService.syncMembers(tenantId, platform, req.getFullSync());
                            } else {
                                return ServerResponse.badRequest()
                                        .bodyValue(Result.error("不支持的同步类型：" + req.getSyncType()));
                            }

                            return syncMono.flatMap(result -> ServerResponse.ok()
                                    .bodyValue(Result.success(SyncOrganizationResponse.builder()
                                            .taskId(result.taskId())
                                            .syncedCount(result.syncedCount())
                                            .message(result.message())
                                            .success(result.success())
                                            .build())));
                        }))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询同步任务列表
     *
     * <p>租户取自认证上下文；平台可省略，页码缺省 1、每页缺省 20（非数字或负数回落到默认值），
     * 返回历史同步任务记录列表，不携带总数。
     *
     * @param request HTTP 请求，查询参数含可选 {@code platform}、{@code page}（缺省 1）、{@code size}（缺省 20）
     * @return 200 响应，{@code data} 为同步任务记录列表；无记录时为空列表
     */
    @Operation(
            operationId = "listSyncTasksV2",
            summary = "查询同步任务列表",
            description = "查询历史同步任务记录，支持按平台过滤与分页",
            tags = {"办公平台组织同步向导"})
    @Parameters({
        @Parameter(
                name = "platform",
                in = ParameterIn.QUERY,
                required = false,
                description = "办公平台类型（可选）",
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
                                schema = @Schema(implementation = TaskListResult.class)))
    })
    @RequirePermission("office:sync:read")
    @OperationLog(
            module = "OfficeSyncWizard",
            type = OperationLog.OperationType.SELECT,
            description = "listSyncTasks",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> listTasks(ServerRequest request) {
        return trustedTenantId(request)
                .flatMap(tenantId -> {
                    String platformParam = request.queryParam("platform").orElse(null);
                    int page = RequestHandlerUtil.safeParseInt(
                            request.queryParam("page").orElse(null), 1);
                    int size = RequestHandlerUtil.safeParseInt(
                            request.queryParam("size").orElse(null), 20);
                    OfficePlatform platform = platformParam != null ? parsePlatform(platformParam) : null;

                    return organizationSyncService
                            .listSyncTasks(tenantId, platform, page, size)
                            .flatMap(tasks -> ServerResponse.ok().bodyValue(Result.success(tasks)));
                })
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询同步任务详情
     *
     * @param request HTTP 请求
     * @return 同步任务详情，不存在时 data 为空
     */
    @Operation(
            operationId = "getSyncTask",
            summary = "查询同步任务详情",
            description = "按任务ID查询同步任务状态、游标与失败信息",
            tags = {"办公平台组织同步向导"})
    @Parameters({
        @Parameter(
                name = "taskId",
                in = ParameterIn.PATH,
                required = true,
                description = "同步任务ID",
                schema = @Schema(type = "integer"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功；任务不存在时返回200且data为空",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TaskDetailResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "任务ID格式错误",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:sync:read")
    @OperationLog(
            module = "OfficeSyncWizard",
            type = OperationLog.OperationType.SELECT,
            description = "getSyncTask",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> getTask(ServerRequest request) {
        return trustedTenantId(request)
                .flatMap(tenantId -> {
                    Long taskId = RequestHandlerUtil.safeParseLong(request.pathVariable("taskId"));
                    if (taskId == null) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "任务ID格式错误"));
                    }
                    return organizationSyncService
                            .getSyncTask(tenantId, taskId)
                            .flatMap(task -> ServerResponse.ok().bodyValue(Result.success(task)))
                            .switchIfEmpty(ServerResponse.ok().bodyValue(Result.success(null)));
                })
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 重试同步任务
     *
     * <p>租户取自认证上下文，任务主键取自路径参数并做安全解析（非数字返回参数错误）；
     * 仅失败任务可重试，有游标时从上次检查点续传、无游标时退化为全量重跑，不存在或状态不允许时返回 400。
     *
     * @param request HTTP 请求，路径参数 taskId
     * @return 200 响应，{@code data} 为重试的任务 ID、同步数量与状态消息；任务不存在或状态不允许时返回 400
     */
    @Operation(
            operationId = "retrySyncTask",
            summary = "重试同步任务",
            description = "重试失败的同步任务，从上次检查点增量续传",
            tags = {"办公平台组织同步向导"})
    @Parameters({
        @Parameter(
                name = "taskId",
                in = ParameterIn.PATH,
                required = true,
                description = "同步任务ID",
                schema = @Schema(type = "integer"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "重试成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = TaskExecuteResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "任务不存在或状态不允许重试",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    @RequirePermission("office:sync:execute")
    @OperationLog(
            module = "OfficeSyncWizard",
            type = OperationLog.OperationType.UPDATE,
            description = "retrySyncTask",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> retryTask(ServerRequest request) {
        return trustedTenantId(request)
                .flatMap(tenantId -> {
                    Long taskId = RequestHandlerUtil.safeParseLong(request.pathVariable("taskId"));
                    if (taskId == null) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "任务ID格式错误"));
                    }
                    return organizationSyncService
                            .retrySyncTask(tenantId, taskId)
                            .flatMap(result -> ServerResponse.ok()
                                    .bodyValue(Result.success(SyncOrganizationResponse.builder()
                                            .taskId(result.taskId())
                                            .syncedCount(result.syncedCount())
                                            .message(result.message())
                                            .success(result.success())
                                            .build())));
                })
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /** 从安全上下文获取受信任租户 ID；缺失时返回未授权业务异常，绝不接受客户端请求头。 */
    private Mono<String> trustedTenantId(ServerRequest request) {
        return request.principal()
                .filter(AuthenticatedUser.class::isInstance)
                .cast(AuthenticatedUser.class)
                .map(AuthenticatedUser::tenantId)
                .filter(tenantId -> tenantId != null && !tenantId.isBlank())
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.UNAUTHORIZED, "未认证或缺少租户上下文")));
    }

    /** 解析平台枚举；非法值返回参数错误而不是 500。 */
    private OfficePlatform parsePlatform(String rawPlatform) {
        try {
            return OfficePlatform.valueOf(rawPlatform);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不支持的办公平台：" + rawPlatform);
        }
    }
}
