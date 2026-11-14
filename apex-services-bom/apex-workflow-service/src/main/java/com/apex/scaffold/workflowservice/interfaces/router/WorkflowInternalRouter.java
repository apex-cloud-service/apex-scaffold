package com.apex.scaffold.workflowservice.interfaces.router;

import com.apex.scaffold.workflowservice.interfaces.handler.WorkflowInternalHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 工作流域内部契约路由（方案 D5 只读统计 / S4-a 统一待办）。
 *
 * <p>{@code GET /internal/v1/workflow/stats} 与 {@code GET /internal/v1/workflow/todo/tasks}：
 * 集群内服务直连，不经网关；安全配置要求已认证（复用验签 JWT 过滤器建立租户上下文与
 * 认证用户），供 system 聚合/待办门面转发调用方凭证消费。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class WorkflowInternalRouter {

    /** 内部统计端点路径。 */
    public static final String STATS_PATH = "/internal/v1/workflow/stats";

    /** 内部待办任务端点路径。 */
    public static final String TODO_TASKS_PATH = "/internal/v1/workflow/todo/tasks";

    /**
     * 注册工作流域内部统计与待办路由。
     *
     * @param handler 工作流内部 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = STATS_PATH,
                method = RequestMethod.GET,
                beanClass = WorkflowInternalHandler.class,
                beanMethod = "stats"),
        @RouterOperation(
                path = TODO_TASKS_PATH,
                method = RequestMethod.GET,
                beanClass = WorkflowInternalHandler.class,
                beanMethod = "todoTasks")
    })
    public RouterFunction<ServerResponse> workflowInternalRoutes(WorkflowInternalHandler handler) {
        return RouterFunctions.route()
                .GET(STATS_PATH, handler::stats)
                .GET(TODO_TASKS_PATH, handler::todoTasks)
                .build();
    }
}
