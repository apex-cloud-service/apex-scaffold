package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.InternalTenantHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 租户状态内部查询路由（统一认证收口 A6）。
 *
 * <p>{@code GET /internal/v1/tenants/{code}/status}：集群内服务直连（认证中心登录
 * 前置租户校验消费），不经网关；安全链路对 {@code /internal/v1/tenants/**} permitAll。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class InternalTenantRouter {

    /**
     * 注册租户状态内部查询路由
     *
     * <p>注册 {@code GET /internal/v1/tenants/{code}/status}，返回租户存在性与能力订阅快照（未知租户
     * 以 {@code exists=false} 数据化返回），委托 {@code InternalTenantHandler#getStatus} 处理。
     *
     * @param handler 租户状态内部 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/internal/v1/tenants/{code}/status",
                method = org.springframework.web.bind.annotation.RequestMethod.GET,
                beanClass = InternalTenantHandler.class,
                beanMethod = "getStatus",
                operation =
                        @Operation(
                                operationId = "internalTenantStatus",
                                summary = "查询租户认证期状态（内部）",
                                description = "集群内服务直连接口，不经网关。返回租户存在性、状态机、过期与配额摘要，"
                                        + "供认证中心登录前置校验消费（统一认证收口 A6）；未知租户以 exists=false 数据化返回。",
                                tags = {"内部契约"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Result 封装的状态快照",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/internal/v1/tenants/{code}/capabilities",
                method = org.springframework.web.bind.annotation.RequestMethod.GET,
                beanClass = InternalTenantHandler.class,
                beanMethod = "getCapabilities",
                operation =
                        @Operation(
                                operationId = "internalTenantCapabilities",
                                summary = "查询租户能力订阅快照（内部）",
                                description = "集群内服务直连接口，不经网关。返回租户存在性与能力订阅集合"
                                        + "（unbounded=true 表示全量订阅；features 为空表示未订阅任何能力），"
                                        + "供网关开放接口能力门禁消费（D4）；"
                                        + "未知租户以 exists=false 数据化返回。",
                                tags = {"内部契约"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Result 封装的能力订阅快照",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    public RouterFunction<ServerResponse> internalTenantRoutes(InternalTenantHandler handler) {
        return RouterFunctions.route()
                .GET("/internal/v1/tenants/{code}/status", handler::getStatus)
                .GET("/internal/v1/tenants/{code}/capabilities", handler::getCapabilities)
                .build();
    }
}
