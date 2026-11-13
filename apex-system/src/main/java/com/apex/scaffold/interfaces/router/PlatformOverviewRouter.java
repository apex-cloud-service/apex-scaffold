package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.PlatformOverviewHandler;
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
 * 业务端跨域只读聚合路由（方案 D5 对外口径）。
 *
 * <p>{@code GET /api/v2/overview}：管理后台消费的聚合概览，经网关暴露；
 * 租户取自可信租户上下文（不接受查询参数覆盖），权限码 {@code system:monitor:view}。
 * 与内部端点 {@code /internal/v1/overview} 共用同一聚合门面服务。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class PlatformOverviewRouter {

    /**
     * 注册业务端聚合概览路由。
     *
     * @param handler 业务端聚合 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperation(
            path = "/api/v2/overview",
            method = RequestMethod.GET,
            beanClass = PlatformOverviewHandler.class,
            beanMethod = "overview",
            operation =
                    @Operation(
                            operationId = "platformOverview",
                            summary = "跨域只读概览聚合",
                            description = "按当前租户聚合文件存储用量、工作流进行中实例数与集成设备数；"
                                    + "任一数据源不可达时该源按 DEGRADED 降级（部分可用），响应含 degraded 标记。",
                            tags = {"后台-数据总览"},
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "Result 封装的聚合响应",
                                        content =
                                                @io.swagger.v3.oas.annotations.media.Content(
                                                        mediaType = MediaType.APPLICATION_JSON_VALUE))
                            }))
    public RouterFunction<ServerResponse> platformOverviewRoutes(PlatformOverviewHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v2/overview", handler::overview)
                .build();
    }
}
