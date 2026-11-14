package com.apex.scaffold.workflowservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import com.apex.scaffold.workflowservice.interfaces.handler.WorkflowHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * 工作流函数式路由。
 *
 * <p>管理查询位于 /admin-api/v1/workflows，流程发起与待办操作位于 /api/v1/workflows，
 * 回退、转办、委派和挂起恢复位于 /api/workflow。请求体与响应契约在关联的 Handler 方法声明。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 * @see WorkflowHandler
 */
@Configuration
public class WorkflowRouter {

    /**
     * 注册实例分页、详情及统计查询。
     *
     * @param handler 工作流处理器
     * @return 管理查询路由
     */
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
                                description = "分页查询流程实例，支持按状态原值过滤",
                                parameters = {
                                    @Parameter(
                                            name = "pageNum",
                                            in = ParameterIn.QUERY,
                                            description = "页码",
                                            schema = @Schema(type = "integer", format = "int32", defaultValue = "1")),
                                    @Parameter(
                                            name = "pageSize",
                                            in = ParameterIn.QUERY,
                                            description = "每页数量",
                                            schema = @Schema(type = "integer", format = "int32", defaultValue = "10")),
                                    @Parameter(
                                            name = "status",
                                            in = ParameterIn.QUERY,
                                            description = "流程实例状态",
                                            schema = @Schema(type = "string"))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/workflows/instances/{instanceId}",
                method = RequestMethod.GET,
                beanClass = WorkflowHandler.class,
                beanMethod = "getInstanceDetail"),
        @RouterOperation(
                path = "/admin-api/v1/workflows/statistics",
                method = RequestMethod.GET,
                beanClass = WorkflowHandler.class,
                beanMethod = "getStatistics"),
        @RouterOperation(
                path = "/admin-api/v1/workflows/statistics/trend",
                method = RequestMethod.GET,
                beanClass = WorkflowHandler.class,
                beanMethod = "getTrend")
    })
    @Bean
    public RouterFunction<ServerResponse> adminWorkflowQueryRoutes(WorkflowHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/workflows", builder -> builder.GET("/instances", handler::listInstances)
                        .GET("/instances/{instanceId}", handler::getInstanceDetail)
                        .GET("/statistics", handler::getStatistics)
                        .GET("/statistics/trend", handler::getTrend))
                .build();
    }

    /**
     * 注册流程发起、当前用户待办查询和完成任务。
     *
     * @param handler 工作流处理器
     * @return 业务接口路由
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/workflows/{workflowCode}/instances",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "startProcessInstance"),
        @RouterOperation(
                path = "/api/v1/workflows/tasks/pending",
                method = RequestMethod.GET,
                beanClass = WorkflowHandler.class,
                beanMethod = "listPendingTasks"),
        @RouterOperation(
                path = "/api/v1/workflows/tasks/{taskId}/complete",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "completeTask")
    })
    @Bean
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
     * 注册任务流转及流程挂起恢复操作。
     *
     * @param handler 工作流处理器
     * @return 工作流操作路由
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/workflow/tasks/{taskId}/rollback",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "rollbackTask"),
        @RouterOperation(
                path = "/api/workflow/tasks/{taskId}/transfer",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "transferTask"),
        @RouterOperation(
                path = "/api/workflow/tasks/{taskId}/delegate",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "delegateTask"),
        @RouterOperation(
                path = "/api/workflow/instances/{instanceId}/suspend",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "suspendInstance"),
        @RouterOperation(
                path = "/api/workflow/instances/{instanceId}/resume",
                method = RequestMethod.POST,
                beanClass = WorkflowHandler.class,
                beanMethod = "resumeInstance")
    })
    @Bean
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
