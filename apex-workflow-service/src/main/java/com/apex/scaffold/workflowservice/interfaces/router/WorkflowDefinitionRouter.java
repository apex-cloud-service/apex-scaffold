package com.apex.scaffold.workflowservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import com.apex.scaffold.workflowservice.interfaces.handler.WorkflowDefinitionHandler;
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
 * 流程定义生命周期函数式路由。
 *
 * <p>全部接口位于 /api/v2/workflow-definitions，覆盖列表、草稿创建与更新、结构校验、
 * 发布、启停及版本列表。请求体与响应契约在 {@link WorkflowDefinitionHandler} 方法声明。
 *
 * @author Apex Team
 * @version 1.4.0
 * @since 1.4.0
 * @see WorkflowDefinitionHandler
 */
@Configuration
public class WorkflowDefinitionRouter {

    /**
     * 注册流程定义生命周期接口。
     *
     * @param handler 流程定义处理器
     * @return 流程定义路由
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v2/workflow-definitions",
                method = RequestMethod.GET,
                beanClass = WorkflowDefinitionHandler.class,
                beanMethod = "listDefinitions",
                operation =
                        @Operation(
                                operationId = "listWorkflowDefinitions",
                                summary = "分页查询流程定义",
                                parameters = {
                                    @Parameter(
                                            name = "workflowCode",
                                            in = ParameterIn.QUERY,
                                            description = "流程编码过滤条件",
                                            schema = @Schema(type = "string")),
                                    @Parameter(
                                            name = "status",
                                            in = ParameterIn.QUERY,
                                            description = "生命周期状态：DRAFT/PUBLISHED/DISABLED",
                                            schema = @Schema(type = "string")),
                                    @Parameter(
                                            name = "pageNum",
                                            in = ParameterIn.QUERY,
                                            description = "页码",
                                            schema = @Schema(type = "integer", format = "int32", defaultValue = "1")),
                                    @Parameter(
                                            name = "pageSize",
                                            in = ParameterIn.QUERY,
                                            description = "每页数量",
                                            schema = @Schema(type = "integer", format = "int32", defaultValue = "10"))
                                })),
        @RouterOperation(
                path = "/api/v2/workflow-definitions",
                method = RequestMethod.POST,
                beanClass = WorkflowDefinitionHandler.class,
                beanMethod = "createDraft"),
        @RouterOperation(
                path = "/api/v2/workflow-definitions/{definitionId}",
                method = RequestMethod.GET,
                beanClass = WorkflowDefinitionHandler.class,
                beanMethod = "getDefinition"),
        @RouterOperation(
                path = "/api/v2/workflow-definitions/{definitionId}",
                method = RequestMethod.PUT,
                beanClass = WorkflowDefinitionHandler.class,
                beanMethod = "updateDraft"),
        @RouterOperation(
                path = "/api/v2/workflow-definitions/{definitionId}/validate",
                method = RequestMethod.POST,
                beanClass = WorkflowDefinitionHandler.class,
                beanMethod = "validateDefinition"),
        @RouterOperation(
                path = "/api/v2/workflow-definitions/{definitionId}/publish",
                method = RequestMethod.POST,
                beanClass = WorkflowDefinitionHandler.class,
                beanMethod = "publishDefinition"),
        @RouterOperation(
                path = "/api/v2/workflow-definitions/{definitionId}/enable",
                method = RequestMethod.POST,
                beanClass = WorkflowDefinitionHandler.class,
                beanMethod = "enableDefinition"),
        @RouterOperation(
                path = "/api/v2/workflow-definitions/{definitionId}/disable",
                method = RequestMethod.POST,
                beanClass = WorkflowDefinitionHandler.class,
                beanMethod = "disableDefinition"),
        @RouterOperation(
                path = "/api/v2/workflow-definitions/{definitionId}/versions",
                method = RequestMethod.GET,
                beanClass = WorkflowDefinitionHandler.class,
                beanMethod = "listVersions")
    })
    @Bean
    public RouterFunction<ServerResponse> workflowDefinitionRoutes(WorkflowDefinitionHandler handler) {
        return RouterFunctions.route()
                .path("/api/v2/workflow-definitions", builder -> builder.GET("", handler::listDefinitions)
                        .POST("", accept(MediaType.APPLICATION_JSON), handler::createDraft)
                        .GET("/{definitionId}", handler::getDefinition)
                        .PUT("/{definitionId}", accept(MediaType.APPLICATION_JSON), handler::updateDraft)
                        .POST(
                                "/{definitionId}/validate",
                                accept(MediaType.APPLICATION_JSON),
                                handler::validateDefinition)
                        .POST("/{definitionId}/publish", accept(MediaType.APPLICATION_JSON), handler::publishDefinition)
                        .POST("/{definitionId}/enable", accept(MediaType.APPLICATION_JSON), handler::enableDefinition)
                        .POST("/{definitionId}/disable", accept(MediaType.APPLICATION_JSON), handler::disableDefinition)
                        .GET("/{definitionId}/versions", handler::listVersions))
                .build();
    }
}
