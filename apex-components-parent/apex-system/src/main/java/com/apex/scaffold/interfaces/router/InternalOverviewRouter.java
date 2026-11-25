package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.InternalOverviewHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * 跨域只读聚合门面路由（方案 D5）。
 *
 * <p>{@code GET /internal/v1/overview}：集群内服务直连，不经网关；安全链路对
 * {@code /internal/v1/overview} permitAll。聚合文件存储用量、工作流进行中
 * 实例数与集成设备数，单源不可达时按源降级（部分可用）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class InternalOverviewRouter {

    /**
     * 注册聚合门面路由
     *
     * <p>注册 {@code GET /internal/v1/overview}，委托 {@code InternalOverviewHandler#getOverview}
     * 返回指定租户的跨域聚合概览。
     *
     * @param handler 聚合门面 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperation(
            path = "/internal/v1/overview",
            method = RequestMethod.GET,
            beanClass = InternalOverviewHandler.class,
            beanMethod = "getOverview",
            operation =
                    @Operation(
                            operationId = "internalOverview",
                            summary = "跨域只读概览聚合（内部）",
                            description =
                                    "集群内服务直连接口，不经网关。并行拉取文件存储用量、工作流进行中实例数" + "与集成设备数并聚合；任一数据源不可达时该源按 DEGRADED 降级（部分可用）。",
                            parameters = {
                                @Parameter(
                                        name = "tenantId",
                                        in = ParameterIn.QUERY,
                                        required = true,
                                        description = "租户标识",
                                        schema = @Schema(type = "string"))
                            },
                            tags = {"内部契约"},
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "Result 封装的聚合响应",
                                        content =
                                                @io.swagger.v3.oas.annotations.media.Content(
                                                        mediaType = MediaType.APPLICATION_JSON_VALUE))
                            }))
    public RouterFunction<ServerResponse> internalOverviewRoutes(InternalOverviewHandler handler) {
        return RouterFunctions.route()
                .GET("/internal/v1/overview", handler::getOverview)
                .build();
    }
}
