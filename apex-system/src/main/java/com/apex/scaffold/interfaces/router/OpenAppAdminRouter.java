package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.OpenAppAdminHandler;
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
 * 开放应用管理路由（D2）。
 *
 * <p>{@code /api/v1/open-apps}：开放平台第三方应用的管理面入口，
 * 读写分别受 {@code open:app:read}/{@code open:app:manage} 权限码控制
 * （V81 权限种子随迁移登记）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class OpenAppAdminRouter {

    /**
     * 注册开放应用管理路由.
     *
     * @param handler 开放应用管理 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/open-apps",
                method = org.springframework.web.bind.annotation.RequestMethod.POST,
                beanClass = OpenAppAdminHandler.class,
                beanMethod = "createApp",
                operation =
                        @Operation(
                                operationId = "createOpenApp",
                                summary = "创建开放应用",
                                tags = {"开放应用管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "创建成功（含一次性明文密钥）",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/open-apps",
                method = org.springframework.web.bind.annotation.RequestMethod.GET,
                beanClass = OpenAppAdminHandler.class,
                beanMethod = "listApps",
                operation =
                        @Operation(
                                operationId = "listOpenApps",
                                summary = "分页查询开放应用",
                                tags = {"开放应用管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "应用列表",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/open-apps/scopes",
                method = org.springframework.web.bind.annotation.RequestMethod.GET,
                beanClass = OpenAppAdminHandler.class,
                beanMethod = "listScopes",
                operation =
                        @Operation(
                                operationId = "listOpenAppScopes",
                                summary = "平台 scope 目录",
                                tags = {"开放应用管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "scope 与权限码映射",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/open-apps/{clientId}",
                method = org.springframework.web.bind.annotation.RequestMethod.GET,
                beanClass = OpenAppAdminHandler.class,
                beanMethod = "getApp",
                operation =
                        @Operation(
                                operationId = "getOpenApp",
                                summary = "查询开放应用详情",
                                tags = {"开放应用管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "应用详情",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/open-apps/{clientId}",
                method = org.springframework.web.bind.annotation.RequestMethod.PUT,
                beanClass = OpenAppAdminHandler.class,
                beanMethod = "updateApp",
                operation =
                        @Operation(
                                operationId = "updateOpenApp",
                                summary = "更新开放应用",
                                tags = {"开放应用管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "更新成功",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/open-apps/{clientId}/reset-secret",
                method = org.springframework.web.bind.annotation.RequestMethod.POST,
                beanClass = OpenAppAdminHandler.class,
                beanMethod = "resetSecret",
                operation =
                        @Operation(
                                operationId = "resetOpenAppSecret",
                                summary = "重置开放应用密钥",
                                tags = {"开放应用管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "重置成功（含一次性明文密钥）",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    public RouterFunction<ServerResponse> openAppAdminRoutes(OpenAppAdminHandler handler) {
        return RouterFunctions.route()
                .POST("/api/v1/open-apps", handler::createApp)
                .GET("/api/v1/open-apps", handler::listApps)
                // scope 目录须先于 /{clientId} 声明，避免被路径变量吞掉
                .GET("/api/v1/open-apps/scopes", handler::listScopes)
                .GET("/api/v1/open-apps/{clientId}", handler::getApp)
                .PUT("/api/v1/open-apps/{clientId}", handler::updateApp)
                .POST("/api/v1/open-apps/{clientId}/reset-secret", handler::resetSecret)
                .build();
    }
}
