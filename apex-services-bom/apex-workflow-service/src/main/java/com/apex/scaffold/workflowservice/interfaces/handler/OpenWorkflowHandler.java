package com.apex.scaffold.workflowservice.interfaces.handler;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.workflow.application.query.WorkflowQueryService;
import com.apex.scaffold.workflow.application.service.WorkflowService;
import com.apex.scaffold.workflowservice.interfaces.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 开放工作流接口：只读流程定义、发起实例与查询本人待办。
 *
 * <p>发起人必须来自开放应用绑定的主体账号（{@code sub_uid}），否则拒绝；不开放审批、
 * 转办、委派、挂起等需要用户权限的管理操作。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
@Tag(name = "开放平台-工作流", description = "开放应用级只读定义、发起实例与本人待办；主体账号须由开放应用绑定")
public class OpenWorkflowHandler {

    /** 流程定义分页的统一响应结构，仅用于接口文档。 */
    @Schema(name = "OpenWorkflowDefinitionPageResult")
    public static class DefinitionPageResult extends Result<com.apex.scaffold.common.dto.PageResponse<Object>> {}

    /** 单条数据的统一响应结构，仅用于接口文档。 */
    @Schema(name = "OpenWorkflowObjectResult")
    public static class ObjectResult extends Result<Object> {}

    private final WorkflowQueryService queries;

    private final WorkflowService workflows;

    /**
     * 构造处理器。
     *
     * @param queries 流程查询服务
     * @param workflows 流程实例服务
     */
    public OpenWorkflowHandler(WorkflowQueryService queries, WorkflowService workflows) {
        this.queries = queries;
        this.workflows = workflows;
    }

    /**
     * 分页查询已发布的流程定义。
     *
     * @param request 服务器请求（query 参数 pageNum/pageSize）
     * @return 统一响应，{@code data} 为分页结果
     * @throws BusinessException 分页参数非法时抛出
     */
    @Operation(
            operationId = "openListWorkflowDefinitions",
            summary = "开放平台分页查询已发布流程定义",
            description = "只返回已发布的流程定义；不读取管理端的草稿与停用定义")
    @Parameter(
            name = "pageNum",
            in = ParameterIn.QUERY,
            description = "页码，从 1 开始，缺省 1",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "1", minimum = "1"))
    @Parameter(
            name = "pageSize",
            in = ParameterIn.QUERY,
            description = "每页数量，缺省 20",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "20", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为流程定义分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DefinitionPageResult.class))),
        @ApiResponse(responseCode = "400", description = "分页参数非法"),
        @ApiResponse(responseCode = "401", description = "未通过网关 scope 断言或能力门禁")
    })
    public Mono<ServerResponse> definitions(ServerRequest request) {
        int page = parse(request.queryParam("pageNum").orElse("1"));
        int size = parse(request.queryParam("pageSize").orElse("20"));
        return queries.listDefinitions(null, null, page, size)
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 查询单个流程定义详情。
     *
     * @param request 服务器请求，路径参数 {@code workflowCode}
     * @return 统一响应，{@code data} 为流程定义详情
     */
    @Operation(
            operationId = "openGetWorkflowDefinition",
            summary = "开放平台查询流程定义详情",
            description = "按流程编码返回已发布定义的节点与表单结构")
    @Parameter(
            name = "workflowCode",
            in = ParameterIn.PATH,
            required = true,
            description = "流程编码",
            schema = @Schema(type = "string", example = "leave_approval"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为流程定义详情",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ObjectResult.class))),
        @ApiResponse(responseCode = "404", description = "流程定义不存在或未发布"),
        @ApiResponse(responseCode = "401", description = "未通过网关 scope 断言或能力门禁")
    })
    public Mono<ServerResponse> definition(ServerRequest request) {
        return queries.getDefinitionDetail(request.pathVariable("workflowCode"))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    /**
     * 以开放应用绑定的主体账号发起流程实例。
     *
     * @param request 服务器请求，请求体为 {@link StartRequest}
     * @return 统一响应，{@code data} 为流程实例
     * @throws BusinessException 请求体缺失、主体账号未绑定或流程定义不存在时抛出
     */
    @Operation(
            operationId = "openStartWorkflowInstance",
            summary = "开放平台发起流程实例",
            description = "以开放应用绑定的主体账号发起流程实例；主体账号必须由开放应用绑定（sub_uid），" + "否则返回 403。业务主键用于幂等关联调用方业务单据")
    @Parameter(
            name = "workflowCode",
            in = ParameterIn.PATH,
            required = true,
            description = "流程编码",
            schema = @Schema(type = "string", example = "leave_approval"))
    @RequestBody(
            required = true,
            description = "发起流程请求",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = StartRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "发起成功，data 为流程实例",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ObjectResult.class))),
        @ApiResponse(responseCode = "400", description = "请求体缺失或参数非法"),
        @ApiResponse(responseCode = "403", description = "开放应用未绑定主体账号"),
        @ApiResponse(responseCode = "404", description = "流程定义不存在或未发布"),
        @ApiResponse(responseCode = "401", description = "未通过网关 scope 断言或能力门禁")
    })
    public Mono<ServerResponse> start(ServerRequest request) {
        return request.bodyToMono(StartRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体缺失")))
                .flatMap(body -> currentSubject().flatMap(subject -> workflows
                        .startProcessInstance(
                                request.pathVariable("workflowCode"), body.businessKey(), subject, body.contextJson())
                        .flatMap(instance -> ServerResponse.ok().bodyValue(Result.success(instance)))));
    }

    /**
     * 分页查询本人待办任务。
     *
     * @param request 服务器请求（query 参数 pageNum/pageSize）
     * @return 统一响应，{@code data} 为分页结果
     * @throws BusinessException 主体账号未绑定或分页参数非法时抛出
     */
    @Operation(
            operationId = "openListMyWorkflowTasks",
            summary = "开放平台分页查询本人待办任务",
            description = "按开放应用绑定的主体账号查询其待办任务；主体账号未绑定时返回 403")
    @Parameter(
            name = "pageNum",
            in = ParameterIn.QUERY,
            description = "页码，从 1 开始，缺省 1",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "1", minimum = "1"))
    @Parameter(
            name = "pageSize",
            in = ParameterIn.QUERY,
            description = "每页数量，缺省 20",
            schema = @Schema(type = "integer", format = "int32", defaultValue = "20", minimum = "1"))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功，data 为待办任务分页结果",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = DefinitionPageResult.class))),
        @ApiResponse(responseCode = "400", description = "分页参数非法"),
        @ApiResponse(responseCode = "403", description = "开放应用未绑定主体账号"),
        @ApiResponse(responseCode = "401", description = "未通过网关 scope 断言或能力门禁")
    })
    public Mono<ServerResponse> myTasks(ServerRequest request) {
        int page = parse(request.queryParam("pageNum").orElse("1"));
        int size = parse(request.queryParam("pageSize").orElse("20"));
        return currentSubject()
                .flatMap(subject -> queries.listMyTasks(subject, page, size))
                .flatMap(result -> ServerResponse.ok().bodyValue(Result.success(result)));
    }

    private Mono<Long> currentSubject() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication())
                .map(authentication -> authentication == null ? null : authentication.getPrincipal())
                .ofType(AuthenticatedUser.class)
                .map(AuthenticatedUser::userId)
                .filter(id -> id != null && id > 0)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.FORBIDDEN, "开放应用未绑定主体账号，无法发起或查询个人待办")));
    }

    private int parse(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "分页参数非法");
        }
    }

    /**
     * 发起流程请求体。
     *
     * @param businessKey 业务主键
     * @param contextJson 流程上下文 JSON
     */
    @Schema(name = "OpenStartWorkflowRequest", description = "开放平台发起流程请求体")
    public record StartRequest(
            @Schema(
                            description = "调用方业务单据主键，用于关联外部业务数据",
                            example = "LEAVE-2026-11-0001",
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String businessKey,
            @Schema(description = "流程上下文 JSON 字符串，作为流程变量传入", example = "{\"days\":3,\"reason\":\"探亲\"}")
                    String contextJson) {}
}
