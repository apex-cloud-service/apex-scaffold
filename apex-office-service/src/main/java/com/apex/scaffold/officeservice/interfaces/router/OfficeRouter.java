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
 * 办公平台统一认证与状态查询的函数式路由。
 *
 * <p>声明 {@code /api/v1/office/**} 下的三个只读 GET 端点：
 *
 * <ul>
 *   <li>{@code /status}：查询当前已接入的办公平台；
 *   <li>{@code /login}：获取第三方办公平台的授权跳转地址；
 *   <li>{@code /callback}：接收第三方平台授权回调。
 * </ul>
 *
 * <p>回调入口由外部办公平台服务端或浏览器重定向发起，不携带前端登录态，
 * 因此不得要求本系统 JWT；其身份绑定与状态校验由 Handler 依据授权码与票据完成。
 * 该路径必须与网关 {@code office-domain} 前缀保持可达。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.2.0
 */
@Configuration
public class OfficeRouter {
    /**
     * 注册办公平台统一认证与状态路由。
     *
     * @param handler 办公平台处理器，负责授权地址生成、回调处理与状态查询
     * @return 办公平台路由函数，挂载于 {@code /api/v1/office}
     */
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
