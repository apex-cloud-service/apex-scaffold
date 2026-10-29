package com.apex.scaffold.officeservice.interfaces.router;

import com.apex.scaffold.officeservice.interfaces.handler.OfficeHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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
 * 办公平台统一认证与状态路由配置。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Configuration
public class OfficeRouter {
    /** 办公平台统一认证与状态路由。 */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/office/status",
                method = RequestMethod.GET,
                beanClass = OfficeHandler.class,
                beanMethod = "status",
                operation =
                        @Operation(
                                summary = "查询已接入的办公平台",
                                description = "查询当前已接入的办公平台列表",
                                tags = {"办公集成管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    OfficeHandler.PlatformStatusResult
                                                                                            .class)))
                                })),
        @RouterOperation(
                path = "/api/v1/office/login",
                method = RequestMethod.GET,
                beanClass = OfficeHandler.class,
                beanMethod = "start",
                operation =
                        @Operation(
                                summary = "获取办公平台授权地址",
                                description = "获取办公平台统一认证的授权登录地址",
                                tags = {"办公集成管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    OfficeHandler.AuthorizationUrlResult
                                                                                            .class)))
                                })),
        @RouterOperation(
                path = "/api/v1/office/callback",
                method = RequestMethod.GET,
                beanClass = OfficeHandler.class,
                beanMethod = "callback",
                operation =
                        @Operation(
                                summary = "办公平台授权回调",
                                description = "处理办公平台统一认证的授权回调",
                                tags = {"办公集成管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    OfficeHandler.LoginResult.class)))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> officeRoutes(OfficeHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/office/status", handler::status)
                .GET("/api/v1/office/login", handler::start)
                .GET("/api/v1/office/callback", handler::callback)
                .build();
    }
}
