package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.interfaces.handler.WorkflowHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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
 *   <li><strong>统一前缀</strong>：所有工作流接口以 /api/workflow 开头</li>
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
     * 工作流路由函数。
     *
     * <p>定义工作流相关的所有路由规则。
     *
     * @param handler WorkflowHandler
     * @return RouterFunction
     */
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
