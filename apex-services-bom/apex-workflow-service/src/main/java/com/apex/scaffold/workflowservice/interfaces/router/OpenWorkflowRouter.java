package com.apex.scaffold.workflowservice.interfaces.router;

import com.apex.scaffold.workflowservice.interfaces.handler.OpenWorkflowHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 开放工作流路由：独立于管理端流程路由，仅暴露只读定义、发起实例与本人待办。
 *
 * <p>{@code @RouterOperation} 只声明路由绑定；操作说明、参数、请求体与响应模型统一由
 * {@link OpenWorkflowHandler} 方法上的 SpringDoc 注解决定。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class OpenWorkflowRouter {

    /**
     * 注册开放工作流路由。
     *
     * @param handler 开放工作流处理器
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/open-api/workflow/definitions",
                method = RequestMethod.GET,
                beanClass = OpenWorkflowHandler.class,
                beanMethod = "definitions"),
        @RouterOperation(
                path = "/open-api/workflow/definitions/{workflowCode}",
                method = RequestMethod.GET,
                beanClass = OpenWorkflowHandler.class,
                beanMethod = "definition"),
        @RouterOperation(
                path = "/open-api/workflow/{workflowCode}/instances",
                method = RequestMethod.POST,
                beanClass = OpenWorkflowHandler.class,
                beanMethod = "start"),
        @RouterOperation(
                path = "/open-api/workflow/tasks/my",
                method = RequestMethod.GET,
                beanClass = OpenWorkflowHandler.class,
                beanMethod = "myTasks")
    })
    public RouterFunction<ServerResponse> openWorkflowRoutes(OpenWorkflowHandler handler) {
        return RouterFunctions.route()
                .GET("/open-api/workflow/definitions", handler::definitions)
                .GET("/open-api/workflow/definitions/{workflowCode}", handler::definition)
                .POST(
                        "/open-api/workflow/{workflowCode}/instances",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        handler::start)
                .GET("/open-api/workflow/tasks/my", handler::myTasks)
                .build();
    }
}
