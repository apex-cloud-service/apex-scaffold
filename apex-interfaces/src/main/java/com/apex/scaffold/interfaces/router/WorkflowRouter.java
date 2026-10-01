package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.interfaces.dto.workflow.CompleteTaskRequest;
import com.apex.scaffold.interfaces.dto.workflow.DelegateTaskRequest;
import com.apex.scaffold.interfaces.dto.workflow.ResumeInstanceRequest;
import com.apex.scaffold.interfaces.dto.workflow.RollbackTaskRequest;
import com.apex.scaffold.interfaces.dto.workflow.StartProcessInstanceRequest;
import com.apex.scaffold.interfaces.dto.workflow.SuspendInstanceRequest;
import com.apex.scaffold.interfaces.dto.workflow.TransferTaskRequest;
import com.apex.scaffold.interfaces.handler.WorkflowHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 工作流路由配置。
 *
 * <p>基于 WebFlux 函数式编程模型的路由配置，
 * 将 HTTP 请求路由到对应的 Handler 方法。
 *
 * <h2>路由规则</h2>
 * <ul>
 *   <li>GET /admin-api/v1/workflows/instances → WorkflowHandler.listInstances</li>
 *   <li>GET /admin-api/v1/workflows/statistics → WorkflowHandler.getStatistics</li>
 *   <li>GET /admin-api/v1/workflows/statistics/trend → WorkflowHandler.getTrend</li>
 *   <li>POST /api/workflow/tasks/{taskId}/rollback → WorkflowHandler.rollbackTask</li>
 *   <li>POST /api/workflow/tasks/{taskId}/transfer → WorkflowHandler.transferTask</li>
 *   <li>POST /api/workflow/tasks/{taskId}/delegate → WorkflowHandler.delegateTask</li>
 *   <li>POST /api/workflow/instances/{instanceId}/suspend → WorkflowHandler.suspendInstance</li>
 *   <li>POST /api/workflow/instances/{instanceId}/resume → WorkflowHandler.resumeInstance</li>
 * </ul>
 *
 * <h2>特点</h2>
 * <ul>
 *   <li><strong>函数式风格</strong>：使用 RouterFunction 替代传统的 @RequestMapping</li>
 *   <li><strong>类型安全</strong>：编译期检查路由配置</li>
 *   <li><strong>响应式</strong>：完全基于 Reactor 的非阻塞处理</li>
 *   <li><strong>统一前缀</strong>：查询接口以 /admin-api/v1/workflows 开头，操作接口以 /api/workflow 开头</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 * @see WorkflowHandler
 */
@Configuration
public class WorkflowRouter {

    /**
     * 工作流查询路由（管理后台）。
     *
     * <p>定义工作流查询相关的所有路由规则。
     *
     * @param handler WorkflowHandler
     * @return RouterFunction
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/workflows/instances",
                method = RequestMethod.GET,
                beanClass = WorkflowHandler.class,
                beanMethod = "listInstances",
                operation =
                        @Operation(
                                operationId = "listInstances",
                                summary = "分页查询流程实例列表",
                                parameters = {
                                    @Parameter(
                                            name = "pageNum",
                                            description = "页码，从 1 开始",
                                            in = ParameterIn.QUERY,
                                            schema =
                                                    @io.swagger.v3.oas.annotations.media.Schema(
                                                            type = "integer",
                                                            defaultValue = "1",
                                                            minimum = "1")),
                                    @Parameter(
                                            name = "pageSize",
                                            description = "每页数量",
                                            in = ParameterIn.QUERY,
                                            schema =
                                                    @io.swagger.v3.oas.annotations.media.Schema(
                                                            type = "integer",
                                                            defaultValue = "10",
                                                            minimum = "1")),
                                    @Parameter(name = "status", description = "流程实例状态", in = ParameterIn.QUERY)
                                },
                                description = "按条件分页查询工作流流程实例，支持状态与时间范围筛选",
                                tags = {"工作流管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/workflows/instances/{instanceId}",
                method = RequestMethod.GET,
                beanClass = WorkflowHandler.class,
                beanMethod = "getInstanceDetail",
                operation =
                        @Operation(
                                operationId = "getInstanceDetail",
                                summary = "查询流程实例详情",
                                description = "根据流程实例 ID 查询实例详情与任务进度",
                                tags = {"工作流管理"},
                                parameters = {
                                    @Parameter(
                                            name = "instanceId",
                                            description = "流程实例 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/workflows/statistics",
                method = RequestMethod.GET,
                beanClass = WorkflowHandler.class,
                beanMethod = "getStatistics",
                operation =
                        @Operation(
                                operationId = "getStatistics",
                                summary = "查询工作流统计概览",
                                description = "查询工作流整体的实例数、任务数等统计指标",
                                tags = {"工作流管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/workflows/statistics/trend",
                method = RequestMethod.GET,
                beanClass = WorkflowHandler.class,
                beanMethod = "getTrend",
                operation =
                        @Operation(
                                operationId = "getTrend",
                                summary = "查询工作流趋势数据",
                                parameters = {
                                    @Parameter(
                                            name = "days",
                                            description = "查询天数",
                                            in = ParameterIn.QUERY,
                                            schema =
                                                    @io.swagger.v3.oas.annotations.media.Schema(
                                                            type = "integer",
                                                            defaultValue = "7",
                                                            minimum = "1"))
                                },
                                description = "按时间维度查询工作流实例与任务的趋势统计数据",
                                tags = {"工作流管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    public RouterFunction<ServerResponse> adminWorkflowQueryRoutes(WorkflowHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/workflows", builder -> builder.GET("/instances", handler::listInstances)
                        .GET("/instances/{instanceId}", handler::getInstanceDetail)
                        .GET("/statistics", handler::getStatistics)
                        .GET("/statistics/trend", handler::getTrend))
                .build();
    }

    /**
     * 工作流业务接口路由（API）。
     *
     * <p>定义工作流业务操作相关的所有路由规则。
     *
     * @param handler WorkflowHandler
     * @return RouterFunction
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/workflows/{workflowCode}/instances",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "startProcessInstance",
                operation =
                        @Operation(
                                operationId = "startProcessInstance",
                                summary = "发起流程实例",
                                description = "根据流程定义编码发起新的流程实例",
                                tags = {"工作流管理"},
                                requestBody =
                                        @RequestBody(
                                                description = "流程实例创建请求",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        StartProcessInstanceRequest
                                                                                                .class))),
                                parameters = {
                                    @Parameter(
                                            name = "workflowCode",
                                            description = "流程定义编码",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "发起成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/workflows/tasks/pending",
                method = RequestMethod.GET,
                beanClass = WorkflowHandler.class,
                beanMethod = "listPendingTasks",
                operation =
                        @Operation(
                                operationId = "listPendingTasks",
                                summary = "查询待办任务列表",
                                description = "查询当前用户的待办工作流任务列表",
                                tags = {"工作流管理"},
                                parameters = {
                                    @Parameter(
                                            name = "pageNum",
                                            description = "页码，从 1 开始",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "1", minimum = "1")),
                                    @Parameter(
                                            name = "pageSize",
                                            description = "每页数量",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "10", minimum = "1"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/workflows/tasks/{taskId}/complete",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "completeTask",
                operation =
                        @Operation(
                                operationId = "completeTask",
                                summary = "办理完成待办任务",
                                description = "根据任务 ID 提交办理意见并完成任务",
                                requestBody =
                                        @RequestBody(
                                                description = "完成任务请求",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        CompleteTaskRequest.class))),
                                tags = {"工作流管理"},
                                parameters = {
                                    @Parameter(
                                            name = "taskId",
                                            description = "工作流任务 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "办理成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    public RouterFunction<ServerResponse> workflowApiRoutes(WorkflowHandler handler) {
        return RouterFunctions.route()
                .path("/api/v1/workflows", builder -> builder.POST(
                                "/{workflowCode}/instances",
                                accept(MediaType.APPLICATION_JSON),
                                handler::startProcessInstance)
                        .GET("/tasks/pending", handler::listPendingTasks)
                        .POST("/tasks/{taskId}/complete", accept(MediaType.APPLICATION_JSON), handler::completeTask))
                .build();
    }

    /**
     * 工作流操作路由（API）。
     *
     * <p>定义工作流操作相关的所有路由规则。
     *
     * @param handler WorkflowHandler
     * @return RouterFunction
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/workflow/tasks/{taskId}/rollback",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "rollbackTask",
                operation =
                        @Operation(
                                operationId = "rollbackTask",
                                summary = "回滚流程任务",
                                description = "将已办理的流程任务回退到指定节点",
                                requestBody =
                                        @RequestBody(
                                                description = "回退请求信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        RollbackTaskRequest.class))),
                                tags = {"工作流管理"},
                                parameters = {
                                    @Parameter(
                                            name = "taskId",
                                            description = "工作流任务 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "回滚成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/workflow/tasks/{taskId}/transfer",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "transferTask",
                operation =
                        @Operation(
                                operationId = "transferTask",
                                summary = "转办流程任务",
                                description = "将当前流程任务转办给其他办理人",
                                requestBody =
                                        @RequestBody(
                                                description = "任务转办请求信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        TransferTaskRequest.class))),
                                tags = {"工作流管理"},
                                parameters = {
                                    @Parameter(
                                            name = "taskId",
                                            description = "工作流任务 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "转办成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/workflow/tasks/{taskId}/delegate",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "delegateTask",
                operation =
                        @Operation(
                                operationId = "delegateTask",
                                summary = "委派流程任务",
                                description = "将当前流程任务委派给其他办理人协办",
                                requestBody =
                                        @RequestBody(
                                                description = "任务委派请求信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        DelegateTaskRequest.class))),
                                tags = {"工作流管理"},
                                parameters = {
                                    @Parameter(
                                            name = "taskId",
                                            description = "工作流任务 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "委派成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/workflow/instances/{instanceId}/suspend",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "suspendInstance",
                operation =
                        @Operation(
                                operationId = "suspendInstance",
                                summary = "挂起流程实例",
                                description = "挂起指定流程实例，暂停其后续流转",
                                requestBody =
                                        @RequestBody(
                                                description = "挂起流程请求信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        SuspendInstanceRequest.class))),
                                tags = {"工作流管理"},
                                parameters = {
                                    @Parameter(
                                            name = "instanceId",
                                            description = "流程实例 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "挂起成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/workflow/instances/{instanceId}/resume",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "resumeInstance",
                operation =
                        @Operation(
                                operationId = "resumeInstance",
                                summary = "恢复流程实例",
                                description = "恢复已挂起的流程实例，继续后续流转",
                                requestBody =
                                        @RequestBody(
                                                description = "恢复流程请求信息",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        ResumeInstanceRequest.class))),
                                tags = {"工作流管理"},
                                parameters = {
                                    @Parameter(
                                            name = "instanceId",
                                            description = "流程实例 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "恢复成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    public RouterFunction<ServerResponse> workflowRoutes(WorkflowHandler handler) {
        return RouterFunctions.route()
                .path("/api/workflow", builder -> builder.POST(
                                "/tasks/{taskId}/rollback", accept(MediaType.APPLICATION_JSON), handler::rollbackTask)
                        .POST("/tasks/{taskId}/transfer", accept(MediaType.APPLICATION_JSON), handler::transferTask)
                        .POST("/tasks/{taskId}/delegate", accept(MediaType.APPLICATION_JSON), handler::delegateTask)
                        .POST(
                                "/instances/{instanceId}/suspend",
                                accept(MediaType.APPLICATION_JSON),
                                handler::suspendInstance)
                        .POST(
                                "/instances/{instanceId}/resume",
                                accept(MediaType.APPLICATION_JSON),
                                handler::resumeInstance))
                .build();
    }
}
