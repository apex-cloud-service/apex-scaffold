package com.apex.scaffold.authservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.EmptyResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.OnlineSessionsResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.OnlineStatisticsResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.SessionListResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.SessionResult;
import com.apex.scaffold.authservice.interfaces.handler.SessionHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 会话管理路由配置
 *
 * <p>定义会话管理相关的HTTP路由规则，采用函数式路由方式配置。
 *
 * <p>路由规则：
 * <ul>
 *   <li>GET /auth/v1/sessions/user/{userId} - 查询用户会话列表</li>
 *   <li>DELETE /auth/v1/sessions/{sessionId} - 终止指定会话</li>
 *   <li>DELETE /auth/v1/sessions/user/{userId} - 批量终止用户所有会话</li>
 *   <li>GET /auth/v1/sessions/online - 在线用户列表</li>
 *   <li>GET /auth/v1/sessions/statistics - 在线用户统计</li>
 *   <li>GET /auth/v1/sessions/{sessionId} - 查询会话详情</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Configuration
@RequiredArgsConstructor
public class SessionRouter {

    private final SessionHandler sessionHandler;

    /**
     * 配置会话管理路由
     *
     * @return RouterFunction
     */
    @Bean
    @SessionOperations
    public RouterFunction<ServerResponse> sessionRoutes() {
        return RouterFunctions.route()
                .path("/auth/v1/sessions", builder -> builder.GET("/user/{userId}", sessionHandler::getUserSessions)
                        .DELETE("/user/{userId}", sessionHandler::terminateUserSessions)
                        .GET("/online", sessionHandler::getOnlineUsers)
                        .GET("/statistics", sessionHandler::getOnlineStatistics)
                        .DELETE("/{sessionId}", sessionHandler::terminateSession)
                        .GET("/{sessionId}", sessionHandler::getSessionById))
                .build();
    }

    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @RouterOperations({
        @RouterOperation(
                path = "/auth/v1/sessions/user/{userId}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getUserSessions",
                                summary = "查询用户会话",
                                description = "查询用户会话；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"会话管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            schema =
                                                    @Schema(
                                                            implementation = Long.class,
                                                            type = "integer",
                                                            format = "int64")),
                                    @Parameter(
                                            name = "tenantId",
                                            description = "租户 ID，必须与可信租户上下文匹配，不能覆盖认证租户",
                                            in = ParameterIn.QUERY,
                                            required = false,
                                            schema =
                                                    @Schema(
                                                            implementation = String.class,
                                                            type = "string",
                                                            defaultValue = "default"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(implementation = SessionListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/sessions/user/{userId}",
                method = RequestMethod.DELETE,
                operation =
                        @Operation(
                                operationId = "terminateUserSessions",
                                summary = "批量下线",
                                description = "批量下线；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"会话管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            schema =
                                                    @Schema(
                                                            implementation = Long.class,
                                                            type = "integer",
                                                            format = "int64")),
                                    @Parameter(
                                            name = "tenantId",
                                            description = "租户 ID，必须与可信租户上下文匹配，不能覆盖认证租户",
                                            in = ParameterIn.QUERY,
                                            required = false,
                                            schema =
                                                    @Schema(
                                                            implementation = String.class,
                                                            type = "string",
                                                            defaultValue = "default"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = EmptyResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/sessions/online",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getOnlineUsers",
                                summary = "在线用户列表",
                                description = "在线用户列表；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"会话管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    OnlineSessionsResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/sessions/statistics",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getOnlineStatistics",
                                summary = "在线用户统计",
                                description = "在线用户统计；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"会话管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    OnlineStatisticsResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/sessions/{sessionId}",
                method = RequestMethod.DELETE,
                operation =
                        @Operation(
                                operationId = "terminateSession",
                                summary = "终止会话",
                                description = "终止会话；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"会话管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "sessionId",
                                            description = "会话唯一标识（非数据库数字 ID）",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            schema = @Schema(implementation = String.class, type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = EmptyResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/sessions/{sessionId}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getSessionById",
                                summary = "查询会话详情",
                                description = "查询会话详情；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"会话管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "sessionId",
                                            description = "会话记录数字 ID",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            schema =
                                                    @Schema(
                                                            implementation = Long.class,
                                                            type = "integer",
                                                            format = "int64"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = SessionResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                }))
    })
    private @interface SessionOperations {}
}
