package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.TenantAware;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.interfaces.dto.workflow.*;
import com.apex.scaffold.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.interfaces.util.MessageUtils;
import com.apex.scaffold.workflow.application.query.WorkflowQueryService;
import com.apex.scaffold.workflow.application.query.WorkflowStatisticsService;
import com.apex.scaffold.workflow.application.service.WorkflowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 工作流处理器（函数式端点）。
 *
 * <p>基于 WebFlux 函数式编程模型的工作流接口处理器，
 * 提供流程发起、待办任务查询、任务审批、流程回退、任务转办、任务委派、
 * 流程挂起/恢复以及实例查询与统计等功能。
 *
 * <h2>核心功能</h2>
 * <ul>
 *   <li>流程实例管理：发起流程实例、查询实例列表与详情</li>
 *   <li>任务处理：查询待办任务、完成任务审批</li>
 *   <li>任务流转：流程回退、任务转办、任务委派</li>
 *   <li>流程控制：挂起与恢复流程实例</li>
 *   <li>统计分析：工作流统计数据与趋势查询</li>
 * </ul>
 *
 * <h2>接口列表</h2>
 * <ul>
 *   <li>POST /api/v1/workflows/{workflowCode}/instances — 发起流程实例</li>
 *   <li>GET /api/v1/workflows/tasks/pending — 查询当前用户待办任务</li>
 *   <li>POST /api/v1/workflows/tasks/{taskId}/complete — 完成任务审批</li>
 *   <li>GET /admin-api/v1/workflows/instances — 查询工作流实例列表</li>
 *   <li>GET /admin-api/v1/workflows/instances/{instanceId} — 查询流程实例详情</li>
 *   <li>GET /admin-api/v1/workflows/statistics — 获取工作流统计数据</li>
 *   <li>GET /admin-api/v1/workflows/statistics/trend — 获取工作流趋势数据</li>
 *   <li>POST /api/workflow/tasks/{taskId}/rollback — 流程回退</li>
 *   <li>POST /api/workflow/tasks/{taskId}/transfer — 任务转办</li>
 *   <li>POST /api/workflow/tasks/{taskId}/delegate — 任务委派</li>
 *   <li>POST /api/workflow/instances/{instanceId}/suspend — 挂起流程实例</li>
 *   <li>POST /api/workflow/instances/{instanceId}/resume — 恢复流程实例</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see WorkflowService
 */
@Slf4j
@Component
@Tag(name = "工作流管理", description = "工作流任务和流程实例操作接口")
public class WorkflowHandler extends BaseHandler {

    private final WorkflowService workflowService;
    private final WorkflowQueryService workflowQueryService;
    private final WorkflowStatisticsService workflowStatisticsService;

    /**
     * 创建工作流管理处理器。
     *
     * @param workflowService 工作流服务
     * @param workflowQueryService 工作流查询服务
     * @param workflowStatisticsService 工作流统计服务
     * @param messageUtils 国际化消息工具
     * @param validator Jakarta Bean Validation 校验器
     */
    public WorkflowHandler(
            WorkflowService workflowService,
            WorkflowQueryService workflowQueryService,
            WorkflowStatisticsService workflowStatisticsService,
            MessageUtils messageUtils,
            Validator validator) {
        super(messageUtils, validator);
        this.workflowService = workflowService;
        this.workflowQueryService = workflowQueryService;
        this.workflowStatisticsService = workflowStatisticsService;
    }

    /**
     * 发起流程实例。
     *
     * <p>根据流程编码创建新的流程实例。</p>
     *
     * @param request ServerRequest，路径参数 workflowCode，请求体 StartProcessInstanceRequest
     * @return ServerResponse，包含流程实例
     */
    @OperationLog(module = "工作流管理", type = OperationLog.OperationType.INSERT, description = "发起流程实例")
    @Operation(operationId = "startProcessInstance", summary = "发起流程实例", description = "根据流程编码创建新的流程实例")
    @Parameter(
            name = "workflowCode",
            description = "流程编码",
            required = true,
            in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH,
            schema = @Schema(type = "string"))
    @RequestBody(
            description = "流程实例创建请求",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = StartProcessInstanceRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "创建成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "404", description = "流程定义不存在")
    })
    public Mono<ServerResponse> startProcessInstance(ServerRequest request) {
        String workflowCode = request.pathVariable("workflowCode");

        return request.bodyToMono(StartProcessInstanceRequest.class)
                .flatMap(this::validateAndThrow)
                .flatMap(req -> getCurrentUserId()
                        .flatMap(userId -> workflowService.startProcessInstance(
                                workflowCode, req.getBusinessKey(), userId, req.getContextJson())))
                .flatMap(instance -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(instance)));
    }

    /**
     * 查询当前用户待办任务。
     *
     * <p>分页查询当前用户的待办任务列表。</p>
     *
     * @param request ServerRequest
     * @return ServerResponse，包含待办任务分页列表
     */
    @Operation(operationId = "listPendingTasks", summary = "查询当前用户待办任务", description = "分页查询当前用户的待办任务列表")
    @Parameter(
            name = "pageNum",
            description = "页码",
            required = false,
            in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
            schema = @Schema(type = "integer", defaultValue = "1"))
    @Parameter(
            name = "pageSize",
            description = "每页大小",
            required = false,
            in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
            schema = @Schema(type = "integer", defaultValue = "10"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Mono<ServerResponse> listPendingTasks(ServerRequest request) {
        int pageNum = Integer.parseInt(request.queryParam("pageNum").orElse("1"));
        int pageSize = Integer.parseInt(request.queryParam("pageSize").orElse("10"));

        return getCurrentUserId()
                .flatMap(userId -> workflowQueryService.listMyTasks(userId, pageNum, pageSize))
                .flatMap(data -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(data)));
    }

    /**
     * 完成任务。
     *
     * <p>审批通过任务。</p>
     *
     * @param request ServerRequest，路径参数 taskId，请求体 CompleteTaskRequest
     * @return ServerResponse，包含操作结果
     */
    @OperationLog(module = "工作流管理", type = OperationLog.OperationType.UPDATE, description = "完成任务")
    @Operation(operationId = "completeTask", summary = "完成任务", description = "审批通过任务")
    @Parameter(
            name = "taskId",
            description = "任务ID",
            required = true,
            in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "完成任务请求",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CompleteTaskRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "完成成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "404", description = "任务不存在")
    })
    public Mono<ServerResponse> completeTask(ServerRequest request) {
        Long taskId = Long.valueOf(request.pathVariable("taskId"));

        return request.bodyToMono(CompleteTaskRequest.class)
                .flatMap(this::validateAndThrow)
                .flatMap(req -> getCurrentUserId()
                        .flatMap(userId -> workflowService.completeTask(taskId, userId, req.getComment())))
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 查询流程实例详情。
     *
     * <p>查询流程实例的完整信息，包括定义、任务列表等。</p>
     *
     * @param request ServerRequest，路径参数 instanceId
     * @return ServerResponse，包含流程实例详情
     */
    @Operation(operationId = "getInstanceDetail", summary = "查询流程实例详情", description = "查询流程实例的完整信息")
    @Parameter(
            name = "instanceId",
            description = "流程实例ID",
            required = true,
            in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "404", description = "流程实例不存在")
    })
    public Mono<ServerResponse> getInstanceDetail(ServerRequest request) {
        Long instanceId = Long.valueOf(request.pathVariable("instanceId"));

        return workflowQueryService.getInstanceDetail(instanceId).flatMap(data -> ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Result.success(data)));
    }

    /**
     * 查询工作流实例列表。
     *
     * <p>支持分页查询和按状态过滤。</p>
     *
     * @param request ServerRequest
     * @return 工作流实例分页列表
     */
    @TenantAware
    @Operation(operationId = "listInstances", summary = "查询工作流实例列表", description = "分页查询工作流实例，支持按状态过滤")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "401", description = "未授权"),
        @ApiResponse(responseCode = "403", description = "权限不足")
    })
    public Mono<ServerResponse> listInstances(ServerRequest request) {
        int pageNum = Integer.parseInt(request.queryParam("pageNum").orElse("1"));
        int pageSize = Integer.parseInt(request.queryParam("pageSize").orElse("10"));
        String status = request.queryParam("status").orElse(null);

        // 直接调用，不捕获错误，方便调试
        return workflowQueryService
                .listInstances(pageNum, pageSize, status, null)
                .flatMap(data -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(data)));
    }

    /**
     * 获取工作流统计数据。
     *
     * <p>返回总实例数、运行中实例数、已完成实例数、待办任务数等统计信息。</p>
     *
     * @param request ServerRequest
     * @return 工作流统计数据
     */
    @TenantAware
    @Operation(operationId = "getStatistics", summary = "获取工作流统计数据", description = "获取工作流实例和任务的统计信息")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Mono<ServerResponse> getStatistics(ServerRequest request) {
        return workflowStatisticsService
                .getStatistics()
                .flatMap(data -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(data)))
                .onErrorResume(error -> {
                    log.warn("工作流统计服务暂时不可用: {}", error.getMessage());
                    // 返回空的统计数据
                    java.util.Map<String, Object> emptyStats = new java.util.HashMap<>();
                    emptyStats.put("totalInstances", 0);
                    emptyStats.put("runningInstances", 0);
                    emptyStats.put("completedInstances", 0);
                    emptyStats.put("pendingTasks", 0);
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success(emptyStats));
                });
    }

    /**
     * 获取工作流趋势数据。
     *
     * <p>返回指定天数内的工作流趋势数据，包括每日完成数和拒绝数。</p>
     *
     * @param request ServerRequest
     * @return 工作流趋势数据
     */
    @TenantAware
    @Operation(operationId = "getTrend", summary = "获取工作流趋势数据", description = "查询指定时间段内工作流实例的趋势统计")
    @Parameter(
            name = "days",
            description = "查询天数",
            required = false,
            in = io.swagger.v3.oas.annotations.enums.ParameterIn.QUERY,
            schema = @Schema(type = "integer", defaultValue = "7"))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "查询成功", content = @Content(mediaType = "application/json")),
        @ApiResponse(responseCode = "401", description = "未授权")
    })
    public Mono<ServerResponse> getTrend(ServerRequest request) {
        int days = Integer.parseInt(request.queryParam("days").orElse("7"));

        return workflowStatisticsService
                .getTrend(days)
                .flatMap(data -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(data)))
                .onErrorResume(error -> {
                    log.warn("工作流趋势服务暂时不可用: {}", error.getMessage());
                    // 返回空的趋势数据
                    java.util.Map<String, Object> emptyTrend = new java.util.HashMap<>();
                    emptyTrend.put("dateList", java.util.Collections.emptyList());
                    emptyTrend.put("completedList", java.util.Collections.emptyList());
                    emptyTrend.put("rejectedList", java.util.Collections.emptyList());
                    return ServerResponse.ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(Result.success(emptyTrend));
                });
    }

    /**
     * 流程回退。
     *
     * <p>将当前任务回退到流程中的前置节点，需要指定目标节点和回退原因。</p>
     *
     * @param request ServerRequest，路径参数 taskId，请求体 RollbackTaskRequest
     * @return ServerResponse，包含操作结果
     */
    @OperationLog(module = "工作流管理", type = OperationLog.OperationType.UPDATE, description = "流程回退")
    @Operation(operationId = "rollbackTask", summary = "流程回退", description = "将工作流任务回退到指定的前置节点")
    @Parameter(
            name = "taskId",
            description = "任务ID",
            required = true,
            in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "回退请求信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RollbackTaskRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "回退成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "404", description = "任务不存在")
    })
    public Mono<ServerResponse> rollbackTask(ServerRequest request) {
        Long taskId = Long.valueOf(request.pathVariable("taskId"));

        return request.bodyToMono(RollbackTaskRequest.class)
                .flatMap(this::validateAndThrow)
                .flatMap(req -> getCurrentUserId()
                        .flatMap(userId -> workflowService.rollbackToNode(
                                taskId, req.getTargetNodeCode(), userId, req.getReason())))
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 任务转办。
     *
     * <p>将当前待办任务转交给其他用户处理。
     *
     * @param request ServerRequest，路径参数 taskId，请求体 TransferTaskRequest
     * @return ServerResponse，包含操作结果
     */
    @OperationLog(module = "工作流管理", type = OperationLog.OperationType.UPDATE, description = "任务转办")
    @Operation(operationId = "transferTask", summary = "任务转办", description = "将当前待办任务转交给其他用户处理，任务责任人变更")
    @Parameter(
            name = "taskId",
            description = "任务ID",
            required = true,
            in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "任务转办请求信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = TransferTaskRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "转办成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "404", description = "任务不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，无法转办该任务")
    })
    public Mono<ServerResponse> transferTask(ServerRequest request) {
        Long taskId = Long.valueOf(request.pathVariable("taskId"));

        return request.bodyToMono(TransferTaskRequest.class)
                .flatMap(this::validateAndThrow)
                .flatMap(req -> getCurrentUserId()
                        .flatMap(userId ->
                                workflowService.transferTask(taskId, req.getNewAssigneeId(), userId, req.getReason())))
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 任务委派。
     *
     * <p>将当前待办任务临时委托给其他用户处理。
     *
     * @param request ServerRequest，路径参数 taskId，请求体 DelegateTaskRequest
     * @return ServerResponse，包含操作结果
     */
    @OperationLog(module = "工作流管理", type = OperationLog.OperationType.UPDATE, description = "任务委派")
    @Operation(operationId = "delegateTask", summary = "任务委派", description = "将当前待办任务临时委托给其他用户处理，委托人完成后任务回到原责任人")
    @Parameter(
            name = "taskId",
            description = "任务ID",
            required = true,
            in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "任务委派请求信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = DelegateTaskRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "委派成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "404", description = "任务不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，无法委派该任务")
    })
    public Mono<ServerResponse> delegateTask(ServerRequest request) {
        Long taskId = Long.valueOf(request.pathVariable("taskId"));

        return request.bodyToMono(DelegateTaskRequest.class)
                .flatMap(this::validateAndThrow)
                .flatMap(req -> getCurrentUserId()
                        .flatMap(userId ->
                                workflowService.delegateTask(taskId, req.getDelegateToId(), userId, req.getReason())))
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 挂起流程实例。
     *
     * <p>暂停流程实例的执行，所有待办任务无法审批。
     *
     * @param request ServerRequest，路径参数 instanceId，请求体 SuspendInstanceRequest
     * @return ServerResponse，包含操作结果
     */
    @OperationLog(module = "工作流管理", type = OperationLog.OperationType.UPDATE, description = "挂起流程实例")
    @Operation(operationId = "suspendInstance", summary = "挂起流程实例", description = "暂停流程实例的执行，所有待办任务无法审批，直到恢复流程")
    @Parameter(
            name = "instanceId",
            description = "流程实例ID",
            required = true,
            in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "挂起流程请求信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SuspendInstanceRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "挂起成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误或流程已挂起"),
        @ApiResponse(responseCode = "404", description = "流程实例不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，无法挂起该流程")
    })
    public Mono<ServerResponse> suspendInstance(ServerRequest request) {
        Long instanceId = Long.valueOf(request.pathVariable("instanceId"));

        return request.bodyToMono(SuspendInstanceRequest.class)
                .flatMap(this::validateAndThrow)
                .flatMap(req -> getCurrentUserId()
                        .flatMap(userId -> workflowService.suspendInstance(instanceId, userId, req.getReason())))
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 恢复流程实例。
     *
     * <p>恢复挂起的流程实例，允许待办任务继续审批。
     *
     * @param request ServerRequest，路径参数 instanceId，请求体 ResumeInstanceRequest
     * @return ServerResponse，包含操作结果
     */
    @OperationLog(module = "工作流管理", type = OperationLog.OperationType.UPDATE, description = "恢复流程实例")
    @Operation(operationId = "resumeInstance", summary = "恢复流程实例", description = "恢复挂起的流程实例，允许待办任务继续审批和流转")
    @Parameter(
            name = "instanceId",
            description = "流程实例ID",
            required = true,
            in = io.swagger.v3.oas.annotations.enums.ParameterIn.PATH,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "恢复流程请求信息",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ResumeInstanceRequest.class)))
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "恢复成功"),
        @ApiResponse(responseCode = "400", description = "请求参数错误或流程未挂起"),
        @ApiResponse(responseCode = "404", description = "流程实例不存在"),
        @ApiResponse(responseCode = "403", description = "权限不足，无法恢复该流程")
    })
    public Mono<ServerResponse> resumeInstance(ServerRequest request) {
        Long instanceId = Long.valueOf(request.pathVariable("instanceId"));

        return request.bodyToMono(ResumeInstanceRequest.class)
                .flatMap(this::validateAndThrow)
                .flatMap(req -> getCurrentUserId()
                        .flatMap(userId -> workflowService.resumeInstance(instanceId, userId, req.getReason())))
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 获取当前用户ID。
     *
     * @return 当前用户ID
     */
    private Mono<Long> getCurrentUserId() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .ofType(AuthenticatedUser.class)
                .map(AuthenticatedUser::userId)
                .switchIfEmpty(Mono.error(new IllegalStateException("当前请求未包含有效认证用户")));
    }
}
