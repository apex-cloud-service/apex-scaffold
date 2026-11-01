package com.apex.scaffold.authservice.interfaces.router;

import com.apex.scaffold.authservice.interfaces.handler.OpenAppHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * 开放平台应用管理路由（方案 M4 / 批次 D2）。
 *
 * <p>{@code /admin-api/v1/open-apps}：开放应用列表/详情与平台 scope 目录。
 * 应用注册与凭据轮换沿用既有 OAUTH2_CLIENT 管理端点。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class OpenAppRouter {

    /**
     * 注册开放平台管理路由。
     *
     * @param handler 开放应用 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/open-apps",
                method = RequestMethod.GET,
                beanClass = OpenAppHandler.class,
                beanMethod = "listApps",
                operation =
                        @Operation(
                                operationId = "listOpenApps",
                                summary = "列出开放应用",
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Result 封装的应用列表",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/open-apps/scopes",
                method = RequestMethod.GET,
                beanClass = OpenAppHandler.class,
                beanMethod = "listScopes",
                operation =
                        @Operation(
                                operationId = "listOpenAppScopes",
                                summary = "平台 scope 目录",
                                responses = {@ApiResponse(responseCode = "200", description = "scope 与权限码映射")})),
        @RouterOperation(
                path = "/admin-api/v1/open-apps/{clientId}",
                method = RequestMethod.GET,
                beanClass = OpenAppHandler.class,
                beanMethod = "getApp",
                operation =
                        @Operation(
                                operationId = "getOpenApp",
                                summary = "开放应用详情",
                                responses = {
                                    @ApiResponse(responseCode = "200", description = "应用详情"),
                                    @ApiResponse(responseCode = "404", description = "应用不存在")
                                }))
    })
    public RouterFunction<ServerResponse> openAppRoutes(OpenAppHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/open-apps", RequestPredicates.accept(MediaType.APPLICATION_JSON), handler::listApps)
                .GET(
                        "/admin-api/v1/open-apps/scopes",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        handler::listScopes)
                .GET(
                        "/admin-api/v1/open-apps/{clientId}",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        handler::getApp)
                .build();
    }
}
