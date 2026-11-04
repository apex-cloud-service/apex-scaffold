package com.apex.scaffold.workflowservice.interfaces.router;

import com.apex.scaffold.workflowservice.interfaces.handler.OpenWorkflowHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 开放工作流路由：独立于管理端流程路由，仅暴露只读定义、发起实例与本人待办。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class OpenWorkflowRouter {

    @Bean
    /**
     * 注册开放工作流路由。
     *
     * @param handler 开放工作流处理器
     * @return 路由函数
     */
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
