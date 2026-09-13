package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.application.dto.admin.JobDTO;
import com.apex.scaffold.application.dto.admin.JobLogDTO;
import com.apex.scaffold.application.dto.admin.JobRequest;
import com.apex.scaffold.application.dto.admin.StatusRequest;
import com.apex.scaffold.application.service.JobService;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.interfaces.util.MessageUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
 * 定时任务处理器（函数式端点）。
 *
 * <p>提供定时任务的查询、创建、更新、删除、启停、手动触发与执行日志
 * 回溯 API，所有端点要求 {@code system:job:*} 权限。任务通过动态调度器
 * 在应用内执行，支持 Cron 表达式驱动的周期调度。</p>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /admin-api/v1/jobs - 查询任务列表</li>
 *   <li>GET /admin-api/v1/jobs/{id} - 查询任务详情</li>
 *   <li>POST /admin-api/v1/jobs - 创建任务</li>
 *   <li>PUT /admin-api/v1/jobs/{id} - 更新任务</li>
 *   <li>DELETE /admin-api/v1/jobs/{id} - 删除任务</li>
 *   <li>PUT /admin-api/v1/jobs/{id}/status - 启用/停用任务</li>
 *   <li>POST /admin-api/v1/jobs/{id}/run - 立即执行一次任务</li>
 *   <li>GET /admin-api/v1/jobs/{id}/logs - 查询任务执行日志</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see JobService
 */
@Component
@Tag(name = "后台-定时任务", description = "定时任务管理与执行日志")
public class JobHandler extends BaseHandler {

    /**
     * 定时任务应用服务。
     */
    private final JobService jobService;

    /**
     * 创建定时任务处理器。
     *
     * @param jobService 定时任务应用服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public JobHandler(JobService jobService, MessageUtils messageUtils, Validator validator) {
        super(messageUtils, validator);
        this.jobService = jobService;
    }

    /**
     * 查询任务列表。
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为任务 DTO 列表
     */
    @RequirePermission("system:job:list")
    @Operation(operationId = "adminListJobs", summary = "查询定时任务列表", description = "查询系统中所有定时任务的列表，包含任务名称、调度表达式、执行状态等信息")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                array = @ArraySchema(schema = @Schema(implementation = JobDTO.class)))),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:job:list权限")
    })
    public Mono<ServerResponse> list(ServerRequest request) {
        return jobService.listJobs().flatMap(this::buildSuccess);
    }

    /**
     * 分页查询任务列表（支持条件筛选）。
     *
     * <p>支持按任务名称模糊匹配、任务分组和状态精确筛选，并提供分页功能。
     * 查询参数通过 Query String 传递，分页参数为必填项。</p>
     *
     * @param request HTTP 请求，查询参数包含 {@code pageNum}、{@code pageSize}、
     *                {@code jobName}、{@code jobGroup}、{@code status}
     * @return 带 traceId 的统一成功响应，data 为分页响应对象
     * @throws com.apex.scaffold.common.exception.BusinessException 当分页参数缺失或非法时抛出
     */
    @RequirePermission("system:job:list")
    @Operation(
            operationId = "adminPageJobs",
            summary = "分页查询定时任务列表",
            description = "分页查询定时任务列表，支持按任务名称模糊匹配、任务分组和状态精确筛选。" + "查询结果按创建时间降序排列，返回分页信息和数据列表")
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
            name = "jobName",
            description = "任务名称（支持模糊查询）",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", example = "数据同步"))
    @Parameter(
            name = "jobGroup",
            description = "任务分组（精确匹配）",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "string", example = "DEFAULT"))
    @Parameter(
            name = "status",
            description = "任务状态（0-停用，1-正常）",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", example = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = com.apex.scaffold.common.dto.PageResponse.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，分页参数必填且必须为正整数"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:job:list权限")
    })
    public Mono<ServerResponse> page(ServerRequest request) {
        return Mono.defer(() -> {
                    String pageNumStr = request.queryParam("pageNum").orElse(null);
                    String pageSizeStr = request.queryParam("pageSize").orElse(null);

                    if (pageNumStr == null || pageNumStr.isBlank()) {
                        return Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                                com.apex.scaffold.common.result.ResultCode.PARAM_ERROR, "pageNum 必须为正整数"));
                    }
                    if (pageSizeStr == null || pageSizeStr.isBlank()) {
                        return Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                                com.apex.scaffold.common.result.ResultCode.PARAM_ERROR, "pageSize 必须为 1-100 之间的正整数"));
                    }

                    int pageNum = safeParseInt(pageNumStr, -1);
                    int pageSize = safeParseInt(pageSizeStr, -1);
                    String jobName = request.queryParam("jobName").orElse(null);
                    String jobGroup = request.queryParam("jobGroup").orElse(null);
                    Integer status = request.queryParam("status")
                            .filter(value -> !value.isBlank())
                            .map(value -> safeParseInt(value, -1))
                            .orElse(null);

                    if (pageNum < 1) {
                        return Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                                com.apex.scaffold.common.result.ResultCode.PARAM_ERROR, "pageNum 必须为正整数"));
                    }
                    if (pageSize < 1 || pageSize > 100) {
                        return Mono.error(new com.apex.scaffold.common.exception.BusinessException(
                                com.apex.scaffold.common.result.ResultCode.PARAM_ERROR, "pageSize 必须为 1-100 之间的正整数"));
                    }

                    com.apex.scaffold.application.dto.admin.JobQueryRequest queryRequest =
                            com.apex.scaffold.application.dto.admin.JobQueryRequest.builder()
                                    .pageNum(pageNum)
                                    .pageSize(pageSize)
                                    .jobName(jobName)
                                    .jobGroup(jobGroup)
                                    .status(status)
                                    .build();

                    return jobService.pageJobs(queryRequest);
                })
                .flatMap(this::buildSuccess);
    }

    /**
     * 查询任务详情。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为任务实体
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:job:query")
    @Operation(operationId = "adminGetJob", summary = "查询任务详情", description = "根据任务ID查询定时任务的详细信息，包含完整的调度配置和执行策略")
    @Parameter(
            name = "id",
            description = "任务ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数错误，任务ID必须为正整数"),
        @ApiResponse(responseCode = "404", description = "任务不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:job:query权限")
    })
    public Mono<ServerResponse> detail(ServerRequest request) {
        return jobService.getJob(requirePositiveId(request)).flatMap(this::buildSuccess);
    }

    /**
     * 创建任务。
     *
     * <p>请求体必填并经 Jakarta Bean Validation 校验，Cron 表达式
     * 合法性由服务端解析器校验。</p>
     *
     * @param request HTTP 请求
     * @return 带 traceId 的统一成功响应，data 为已创建的任务实体
     * @throws BusinessException 当请求体为空时抛出
     */
    @RequirePermission("system:job:add")
    @Operation(
            operationId = "adminCreateJob",
            summary = "创建定时任务",
            description = "创建一个新的定时任务，需指定任务名称、调用目标、Cron表达式等配置。Cron表达式支持6/7段标准格式")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "任务创建请求信息",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败或Cron表达式不合法"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:job:add权限")
    })
    @OperationLog(module = "定时任务", type = OperationLog.OperationType.INSERT, description = "创建定时任务")
    public Mono<ServerResponse> create(ServerRequest request) {
        return parseBody(request, JobRequest.class)
                .flatMap(jobService::createJob)
                .flatMap(this::buildSuccess);
    }

    /**
     * 更新任务。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为已更新的任务实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:job:edit")
    @Operation(
            operationId = "adminUpdateJob",
            summary = "更新定时任务",
            description = "更新指定任务的配置信息，支持修改任务名称、调用目标、Cron表达式、执行策略等。若任务正在运行，会重新注册调度")
    @Parameter(
            name = "id",
            description = "任务ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "任务更新请求信息",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数校验失败或Cron表达式不合法"),
        @ApiResponse(responseCode = "404", description = "任务不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:job:edit权限")
    })
    @OperationLog(module = "定时任务", type = OperationLog.OperationType.UPDATE, description = "更新定时任务")
    public Mono<ServerResponse> update(ServerRequest request) {
        Long id = requirePositiveId(request);
        return parseBody(request, JobRequest.class)
                .flatMap(body -> jobService.updateJob(id, body))
                .flatMap(this::buildSuccess);
    }

    /**
     * 删除任务。
     *
     * <p>删除后同步取消调度链。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"deleted": 是否删除成功}}
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:job:remove")
    @Operation(operationId = "adminDeleteJob", summary = "删除定时任务", description = "删除指定的定时任务，删除后会自动取消该任务的调度。历史执行日志会保留")
    @Parameter(
            name = "id",
            description = "任务ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "删除成功"),
        @ApiResponse(responseCode = "400", description = "参数错误，任务ID必须为正整数"),
        @ApiResponse(responseCode = "404", description = "任务不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:job:remove权限")
    })
    @OperationLog(module = "定时任务", type = OperationLog.OperationType.DELETE, description = "删除定时任务")
    public Mono<ServerResponse> delete(ServerRequest request) {
        return jobService
                .deleteJob(requirePositiveId(request))
                .flatMap(deleted -> buildSuccess(java.util.Map.of("deleted", deleted)));
    }

    /**
     * 启用或停用任务。
     *
     * <p>启用后注册调度，停用后取消调度。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为更新后的任务实体
     * @throws BusinessException 当 {@code id} 非法或请求体为空时抛出
     */
    @RequirePermission("system:job:edit")
    @Operation(
            operationId = "adminUpdateJobStatus",
            summary = "启用或停用定时任务",
            description = "更新任务的启用状态。启用后任务会根据Cron表达式自动调度执行，停用后会取消调度但不删除任务配置")
    @Parameter(
            name = "id",
            description = "任务ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "状态更新请求信息",
            required = true,
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = StatusRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = JobDTO.class))),
        @ApiResponse(responseCode = "400", description = "参数错误或请求体为空"),
        @ApiResponse(responseCode = "404", description = "任务不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:job:edit权限")
    })
    @OperationLog(module = "定时任务", type = OperationLog.OperationType.UPDATE, description = "更新任务状态")
    public Mono<ServerResponse> updateStatus(ServerRequest request) {
        Long id = requirePositiveId(request);
        return parseBody(request, StatusRequest.class)
                .flatMap(body -> jobService.updateStatus(id, body.isEnabled()))
                .flatMap(this::buildSuccess);
    }

    /**
     * 立即执行一次任务。
     *
     * <p>不改变调度状态，仅触发一次执行并记录日志。</p>
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数
     * @return 带 traceId 的统一成功响应，data 为 {@code {"triggered": true}}
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:job:run")
    @Operation(
            operationId = "adminRunJob",
            summary = "立即执行一次任务",
            description = "手动触发任务立即执行一次，不影响任务的调度状态和计划。执行结果会记录到执行日志中")
    @Parameter(
            name = "id",
            description = "任务ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "触发成功"),
        @ApiResponse(responseCode = "400", description = "参数错误，任务ID必须为正整数"),
        @ApiResponse(responseCode = "404", description = "任务不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:job:run权限")
    })
    @OperationLog(module = "定时任务", type = OperationLog.OperationType.UPDATE, description = "手动触发任务")
    public Mono<ServerResponse> runOnce(ServerRequest request) {
        return jobService.runOnce(requirePositiveId(request)).then(buildSuccess(java.util.Map.of("triggered", true)));
    }

    /**
     * 查询任务执行日志。
     *
     * @param request HTTP 请求，路径参数 {@code id} 必须为正整数，
     *                查询参数 {@code limit} 缺省 20
     * @return 带 traceId 的统一成功响应，data 为执行日志 DTO 列表
     * @throws BusinessException 当 {@code id} 非法时抛出
     */
    @RequirePermission("system:job:query")
    @Operation(
            operationId = "adminListJobLogs",
            summary = "查询任务执行日志",
            description = "查询指定任务的最近执行日志，包含执行状态、耗时、异常信息等。日志按执行时间倒序排列")
    @Parameter(
            name = "id",
            description = "任务ID",
            required = true,
            in = ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64", example = "1"))
    @Parameter(
            name = "limit",
            description = "返回条数上限，默认20，最大100",
            required = false,
            in = ParameterIn.QUERY,
            schema = @Schema(type = "integer", defaultValue = "20", minimum = "1", maximum = "100"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                array = @ArraySchema(schema = @Schema(implementation = JobLogDTO.class)))),
        @ApiResponse(responseCode = "400", description = "参数错误，任务ID必须为正整数"),
        @ApiResponse(responseCode = "404", description = "任务不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，需要system:job:query权限")
    })
    public Mono<ServerResponse> logs(ServerRequest request) {
        int limit = safeParseInt(request.queryParam("limit").orElse(null), 20);
        return jobService.listLogs(requirePositiveId(request), limit).flatMap(this::buildSuccess);
    }
}
