package com.apex.scaffold.workflowservice.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.dto.PageResponse;
import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.workflow.application.dto.WorkflowDefinitionDTO;
import com.apex.scaffold.workflow.application.query.WorkflowQueryService;
import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflow.domain.model.WorkflowDefinition;
import com.apex.scaffold.workflowservice.interfaces.dto.workflow.CreateWorkflowDefinitionRequest;
import com.apex.scaffold.workflowservice.interfaces.dto.workflow.UpdateWorkflowDefinitionRequest;
import com.apex.scaffold.workflowservice.interfaces.security.AuthenticatedUser;
import com.apex.scaffold.workflowservice.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 流程定义生命周期处理器（函数式端点）。
 *
 * <p>提供草稿创建与编辑、结构校验、发布、启停、版本列表与详情查询。
 * 写操作要求编辑/发布/启停权限，并按既有权重记录操作日志；租户范围来自认证上下文。</p>
 *
 * <h2>接口列表</h2>
 * <ul>
 *   <li>GET /api/v2/workflow-definitions — 分页查询流程定义</li>
 *   <li>POST /api/v2/workflow-definitions — 创建草稿</li>
 *   <li>GET /api/v2/workflow-definitions/{definitionId} — 定义详情</li>
 *   <li>PUT /api/v2/workflow-definitions/{definitionId} — 更新草稿</li>
 *   <li>POST /api/v2/workflow-definitions/{definitionId}/validate — 结构校验</li>
 *   <li>POST /api/v2/workflow-definitions/{definitionId}/publish — 发布</li>
 *   <li>POST /api/v2/workflow-definitions/{definitionId}/enable — 启用</li>
 *   <li>POST /api/v2/workflow-definitions/{definitionId}/disable — 停用</li>
 *   <li>GET /api/v2/workflow-definitions/{definitionId}/versions — 版本列表</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 * @see WorkflowService
 */
@Slf4j
@Component
@Tag(name = "流程定义管理", description = "流程定义草稿、校验、发布、启停与版本查询接口")
public class WorkflowDefinitionHandler {

    /** 流程定义分页响应文档模型。 */
    @Schema(name = "WorkflowDefinitionPageResult")
    public static class WorkflowDefinitionPageResult extends Result<PageResponse<WorkflowDefinitionDTO>> {}

    /** 流程定义详情响应文档模型。 */
    @Schema(name = "WorkflowDefinitionDetailResult")
    public static class WorkflowDefinitionDetailResult extends Result<WorkflowDefinitionDTO> {}

    /** 流程定义写入响应文档模型。 */
    @Schema(name = "WorkflowDefinitionWriteResult")
    public static class WorkflowDefinitionWriteResult extends Result<WorkflowDefinition> {}

    /** 流程定义版本列表响应文档模型。 */
    @Schema(name = "WorkflowDefinitionVersionListResult")
    public static class WorkflowDefinitionVersionListResult extends Result<List<WorkflowDefinitionDTO>> {}

    /** 无数据操作响应文档模型。 */
    @Schema(name = "WorkflowDefinitionVoidResult")
    public static class WorkflowDefinitionVoidResult extends Result<Void> {}

    private final WorkflowService workflowService;

    private final WorkflowQueryService workflowQueryService;

    private final RequestHandlerUtil requestHandlerUtil;

    public WorkflowDefinitionHandler(
            WorkflowService workflowService,
            WorkflowQueryService workflowQueryService,
            RequestHandlerUtil requestHandlerUtil) {
        this.workflowService = workflowService;
        this.workflowQueryService = workflowQueryService;
        this.requestHandlerUtil = requestHandlerUtil;
    }

    /**
     * 分页查询流程定义。
     *
     * @param request ServerRequest，支持 workflowCode、status、pageNum、pageSize
     * @return 分页结果
     */
    @Operation(operationId = "listWorkflowDefinitions", summary = "分页查询流程定义", description = "按流程编码与生命周期状态过滤，最近更新的记录在前")
    @Parameter(
            name = "workflowCode",
            in = ParameterIn.QUERY,
            description = "流程编码过滤条件",
            schema = @Schema(type = "string"))
    @Parameter(
            name = "status",
            in = ParameterIn.QUERY,
            description = "生命周期状态：DRAFT/PUBLISHED/DISABLED",
            schema = @Schema(type = "string"))
    @Parameter(
            name = "pageNum",
            in = ParameterIn.QUERY,
            description = "页码",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "1"))
    @Parameter(
            name = "pageSize",
            in = ParameterIn.QUERY,
            description = "每页数量",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "10"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = WorkflowDefinitionPageResult.class))),
        @ApiResponse(responseCode = "400", description = "状态参数不支持")
    })
    @RequirePermission("workflow:definition:read")
    public Mono<ServerResponse> listDefinitions(ServerRequest request) {
        String workflowCode = request.queryParam("workflowCode").orElse(null);
        String status = request.queryParam("status").orElse(null);
        int pageNum = Integer.parseInt(request.queryParam("pageNum").orElse("1"));
        int pageSize = Integer.parseInt(request.queryParam("pageSize").orElse("10"));

        return workflowQueryService
                .listDefinitions(workflowCode, status, pageNum, pageSize)
                .flatMap(data -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(data)));
    }

    /**
     * 创建流程定义草稿。
     *
     * @param request ServerRequest，请求体 CreateWorkflowDefinitionRequest
     * @return 创建后的草稿
     */
    @Operation(operationId = "createWorkflowDefinitionDraft", summary = "创建流程定义草稿", description = "同一流程编码下版本号自动递增")
    @RequestBody(
            description = "流程定义草稿创建请求",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreateWorkflowDefinitionRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "创建成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = WorkflowDefinitionWriteResult.class))),
        @ApiResponse(responseCode = "400", description = "请求参数错误"),
        @ApiResponse(responseCode = "409", description = "同版本草稿并发创建")
    })
    @RequirePermission("workflow:definition:edit")
    @OperationLog(
            module = "流程定义管理",
            type = OperationLog.OperationType.INSERT,
            description = "创建流程定义草稿",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> createDraft(ServerRequest request) {
        return request.bodyToMono(CreateWorkflowDefinitionRequest.class)
                .flatMap(requestHandlerUtil::validateAndThrow)
                .flatMap(req -> getCurrentUserId().flatMap(userId -> {
                    WorkflowDefinition payload = new WorkflowDefinition();
                    payload.setWorkflowCode(req.getWorkflowCode());
                    payload.setWorkflowName(req.getWorkflowName());
                    payload.setDescription(req.getDescription());
                    payload.setDagJson(req.getDagJson());
                    return workflowService.createDraft(payload, userId);
                }))
                .flatMap(definition -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(definition)));
    }

    /**
     * 查询流程定义详情。
     *
     * @param request ServerRequest，路径参数 definitionId
     * @return 流程定义详情
     */
    @Operation(operationId = "getWorkflowDefinition", summary = "查询流程定义详情", description = "按定义 ID 加载 DAG 结构")
    @Parameter(
            name = "definitionId",
            in = ParameterIn.PATH,
            description = "流程定义 ID",
            required = true,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = WorkflowDefinitionDetailResult.class))),
        @ApiResponse(responseCode = "404", description = "流程定义不存在")
    })
    @RequirePermission("workflow:definition:read")
    public Mono<ServerResponse> getDefinition(ServerRequest request) {
        Long definitionId = parseDefinitionId(request);
        return requireDefinition(workflowQueryService.getDefinitionDetail(definitionId))
                .flatMap(definition -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(definition)));
    }

    /**
     * 更新流程定义草稿。
     *
     * @param request ServerRequest，路径参数 definitionId，请求体 UpdateWorkflowDefinitionRequest
     * @return 更新后的草稿
     */
    @Operation(operationId = "updateWorkflowDefinitionDraft", summary = "更新流程定义草稿", description = "仅草稿状态允许编辑")
    @Parameter(
            name = "definitionId",
            in = ParameterIn.PATH,
            description = "流程定义 ID",
            required = true,
            schema = @Schema(type = "integer", format = "int64"))
    @RequestBody(
            description = "流程定义草稿更新请求",
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateWorkflowDefinitionRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "更新成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = WorkflowDefinitionWriteResult.class))),
        @ApiResponse(responseCode = "400", description = "请求参数错误或定义非草稿状态"),
        @ApiResponse(responseCode = "404", description = "流程定义不存在")
    })
    @RequirePermission("workflow:definition:edit")
    @OperationLog(
            module = "流程定义管理",
            type = OperationLog.OperationType.UPDATE,
            description = "更新流程定义草稿",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> updateDraft(ServerRequest request) {
        Long definitionId = parseDefinitionId(request);
        return request.bodyToMono(UpdateWorkflowDefinitionRequest.class)
                .flatMap(requestHandlerUtil::validateAndThrow)
                .flatMap(req -> getCurrentUserId().flatMap(userId -> {
                    WorkflowDefinition payload = new WorkflowDefinition();
                    payload.setWorkflowName(req.getWorkflowName());
                    payload.setDescription(req.getDescription());
                    payload.setDagJson(req.getDagJson());
                    return workflowService.updateDraft(definitionId, payload, userId);
                }))
                .flatMap(definition -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(definition)));
    }

    /**
     * 校验流程定义 DAG 结构。
     *
     * @param request ServerRequest，路径参数 definitionId
     * @return 校验通过时 data 为空
     */
    @Operation(
            operationId = "validateWorkflowDefinition",
            summary = "校验流程定义结构",
            description = "拒绝未知节点、悬空边、环路及非首期支持的审批人配置")
    @Parameter(
            name = "definitionId",
            in = ParameterIn.PATH,
            description = "流程定义 ID",
            required = true,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "校验通过，data 为空",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = WorkflowDefinitionVoidResult.class))),
        @ApiResponse(responseCode = "400", description = "流程定义不合法"),
        @ApiResponse(responseCode = "404", description = "流程定义不存在")
    })
    @RequirePermission("workflow:definition:edit")
    public Mono<ServerResponse> validateDefinition(ServerRequest request) {
        Long definitionId = parseDefinitionId(request);
        return workflowService
                .validateDefinition(definitionId)
                .then(ServerResponse.ok().bodyValue(Result.success()));
    }

    /**
     * 发布流程定义草稿。
     *
     * @param request ServerRequest，路径参数 definitionId
     * @return 发布后的流程定义
     */
    @Operation(operationId = "publishWorkflowDefinition", summary = "发布流程定义", description = "发布后该版本内容不可变")
    @Parameter(
            name = "definitionId",
            in = ParameterIn.PATH,
            description = "流程定义 ID",
            required = true,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "发布成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = WorkflowDefinitionWriteResult.class))),
        @ApiResponse(responseCode = "400", description = "定义非草稿状态或 DAG 不合法"),
        @ApiResponse(responseCode = "404", description = "流程定义不存在")
    })
    @RequirePermission("workflow:definition:publish")
    @OperationLog(
            module = "流程定义管理",
            type = OperationLog.OperationType.UPDATE,
            description = "发布流程定义",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> publishDefinition(ServerRequest request) {
        Long definitionId = parseDefinitionId(request);
        return getCurrentUserId()
                .flatMap(userId -> workflowService.publishDefinition(definitionId, userId))
                .flatMap(definition -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(definition)));
    }

    /**
     * 启用已发布的流程定义。
     *
     * @param request ServerRequest，路径参数 definitionId
     * @return 启用后的流程定义
     */
    @Operation(operationId = "enableWorkflowDefinition", summary = "启用流程定义", description = "启用后允许发起新实例")
    @Parameter(
            name = "definitionId",
            in = ParameterIn.PATH,
            description = "流程定义 ID",
            required = true,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "启用成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = WorkflowDefinitionWriteResult.class))),
        @ApiResponse(responseCode = "400", description = "定义尚未发布"),
        @ApiResponse(responseCode = "404", description = "流程定义不存在")
    })
    @RequirePermission("workflow:definition:lifecycle")
    @OperationLog(
            module = "流程定义管理",
            type = OperationLog.OperationType.UPDATE,
            description = "启用流程定义",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> enableDefinition(ServerRequest request) {
        Long definitionId = parseDefinitionId(request);
        return getCurrentUserId()
                .flatMap(userId -> workflowService.enableDefinition(definitionId, userId))
                .flatMap(definition -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(definition)));
    }

    /**
     * 停用已发布的流程定义。
     *
     * @param request ServerRequest，路径参数 definitionId
     * @return 停用后的流程定义
     */
    @Operation(operationId = "disableWorkflowDefinition", summary = "停用流程定义", description = "停用后仅阻止新发起，运行中实例继续执行")
    @Parameter(
            name = "definitionId",
            in = ParameterIn.PATH,
            description = "流程定义 ID",
            required = true,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "停用成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = WorkflowDefinitionWriteResult.class))),
        @ApiResponse(responseCode = "400", description = "定义尚未发布"),
        @ApiResponse(responseCode = "404", description = "流程定义不存在")
    })
    @RequirePermission("workflow:definition:lifecycle")
    @OperationLog(
            module = "流程定义管理",
            type = OperationLog.OperationType.UPDATE,
            description = "停用流程定义",
            saveRequest = false,
            saveResponse = false)
    public Mono<ServerResponse> disableDefinition(ServerRequest request) {
        Long definitionId = parseDefinitionId(request);
        return getCurrentUserId()
                .flatMap(userId -> workflowService.disableDefinition(definitionId, userId))
                .flatMap(definition -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(definition)));
    }

    /**
     * 查询指定定义所属流程编码的全部版本。
     *
     * @param request ServerRequest，路径参数 definitionId
     * @return 版本列表，按版本号降序
     */
    @Operation(
            operationId = "listWorkflowDefinitionVersions",
            summary = "查询流程定义版本列表",
            description = "按版本号降序返回同一流程编码的所有版本")
    @Parameter(
            name = "definitionId",
            in = ParameterIn.PATH,
            description = "流程定义 ID",
            required = true,
            schema = @Schema(type = "integer", format = "int64"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = WorkflowDefinitionVersionListResult.class))),
        @ApiResponse(responseCode = "404", description = "流程定义不存在")
    })
    @RequirePermission("workflow:definition:read")
    public Mono<ServerResponse> listVersions(ServerRequest request) {
        Long definitionId = parseDefinitionId(request);
        return workflowQueryService
                .listDefinitionVersions(definitionId)
                .flatMap(versions -> versions.isEmpty()
                        ? Mono.error(new BusinessException(ResultCode.NOT_FOUND, "流程定义不存在"))
                        : Mono.just(versions))
                .flatMap(versions -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(Result.success(versions)));
    }

    private Mono<WorkflowDefinitionDTO> requireDefinition(Mono<WorkflowDefinitionDTO> definition) {
        return definition.switchIfEmpty(Mono.error(new BusinessException(ResultCode.NOT_FOUND, "流程定义不存在")));
    }

    private Long parseDefinitionId(ServerRequest request) {
        return Long.valueOf(request.pathVariable("definitionId"));
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
