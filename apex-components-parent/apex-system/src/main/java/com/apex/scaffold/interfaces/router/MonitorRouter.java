package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.MonitorDashboardHandler;
import com.apex.scaffold.interfaces.handler.OnlineUserHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * 监控路由配置（/admin-api/v1/monitor、online-users）。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class MonitorRouter {

    /**
     * 监控大盘路由（/admin-api/v1/monitor）。
     *
     * <p>提供监控概览、系统资源、中间件、治理与核心业务五类监控聚合数据，
     * 经 {@code RequirePermissionAspect} 校验 {@code system:monitor:view} 权限。</p>
     *
     * @param handler 监控大盘处理器
     * @return 监控路由函数
     * @since 2.3.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/monitor/overview",
                method = RequestMethod.GET,
                beanClass = MonitorDashboardHandler.class,
                beanMethod = "overview",
                operation =
                        @Operation(
                                summary = "查询监控概览",
                                description = "查询监控大盘总览数据",
                                tags = {"监控管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/monitor/system",
                method = RequestMethod.GET,
                beanClass = MonitorDashboardHandler.class,
                beanMethod = "system",
                operation =
                        @Operation(
                                summary = "查询系统资源监控",
                                description = "查询主机与 JVM 等系统资源监控数据",
                                tags = {"监控管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/monitor/middleware",
                method = RequestMethod.GET,
                beanClass = MonitorDashboardHandler.class,
                beanMethod = "middleware",
                operation =
                        @Operation(
                                summary = "查询中间件监控",
                                description = "查询数据库、缓存、消息队列等中间件监控数据",
                                tags = {"监控管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/monitor/governance",
                method = RequestMethod.GET,
                beanClass = MonitorDashboardHandler.class,
                beanMethod = "governance",
                operation =
                        @Operation(
                                summary = "查询治理监控",
                                description = "查询限流、熔断、任务调度等治理监控数据",
                                tags = {"监控管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/monitor/business",
                method = RequestMethod.GET,
                beanClass = MonitorDashboardHandler.class,
                beanMethod = "business",
                operation =
                        @Operation(
                                summary = "查询核心业务监控",
                                description = "查询核心业务指标监控数据",
                                tags = {"监控管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> adminMonitorRoutes(MonitorDashboardHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/monitor", routes -> routes.GET("/overview", handler::overview)
                        .GET("/system", handler::system)
                        .GET("/middleware", handler::middleware)
                        .GET("/governance", handler::governance)
                        .GET("/business", handler::business))
                .build();
    }

    /**
     * 系统管理-在线用户路由（/admin-api/v1/online-users）。
     *
     * <p>提供在线用户列表查询端点（C4），经 {@code RequirePermissionAspect}
     * 校验 {@code system:online-user:list} 权限（V5 迁移预置）。</p>
     *
     * @param handler 在线用户处理器
     * @return 在线用户管理路由函数
     * @since 2.2.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/online-users",
                method = RequestMethod.GET,
                beanClass = OnlineUserHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                summary = "查询在线用户列表",
                                description = "查询当前在线用户列表",
                                tags = {"在线用户"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> adminOnlineUserRoutes(OnlineUserHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/online-users", handler::list)
                .build();
    }
}
