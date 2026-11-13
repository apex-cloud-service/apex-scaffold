package com.apex.scaffold.aggregationservice.interfaces;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 统一待办聚合路由（S4-c/S4-d 独立聚合服务口径）。
 *
 * <p>{@code GET /api/v2/todo}：管理后台个人工作台消费的跨域待办聚合，经网关
 * {@code lb://apex-aggregation-service} 暴露；用户取自认证上下文，仅要求认证
 * （个人自有数据，无需权限码）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class UnifiedTodoRouter {

    /**
     * 注册统一待办聚合路由。
     *
     * @param handler 统一待办聚合 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperation(
            path = "/api/v2/todo",
            method = RequestMethod.GET,
            beanClass = UnifiedTodoHandler.class,
            beanMethod = "todo",
            operation =
                    @Operation(
                            operationId = "unifiedTodo",
                            summary = "查询当前用户统一待办",
                            description =
                                    "聚合当前用户在工作流（待办任务）与 office（待审批/未读站内信）的待办条目；" + "任一数据源不可达时该源按 DEGRADED 降级（部分可用）。",
                            tags = {"统一待办"},
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "Result 封装的聚合响应",
                                        content =
                                                @io.swagger.v3.oas.annotations.media.Content(
                                                        mediaType = MediaType.APPLICATION_JSON_VALUE))
                            }))
    public RouterFunction<ServerResponse> unifiedTodoRoutes(UnifiedTodoHandler handler) {
        return RouterFunctions.route().GET("/api/v2/todo", handler::todo).build();
    }
}
