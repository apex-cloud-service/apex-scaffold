package com.apex.scaffold.authcenter.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.authcenter.application.dto.AuditLogQueryRequest;
import com.apex.scaffold.authcenter.application.dto.AuthCenterApiResponses.AuditLogListResult;
import com.apex.scaffold.authcenter.application.dto.AuthCenterApiResponses.AuditLogResult;
import com.apex.scaffold.authcenter.application.dto.AuthCenterApiResponses.CountResult;
import com.apex.scaffold.authcenter.interfaces.handler.AuditLogHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
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
 * 审计日志路由配置
 *
 * <p>定义审计日志相关的HTTP路由。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Configuration
@RequiredArgsConstructor
public class AuditLogRouter {

    private final AuditLogHandler auditLogHandler;

    /**
     * 审计日志路由
     *
     * @return 路由函数
     */
    @Bean
    @AuditLogOperations
    public RouterFunction<ServerResponse> auditLogRoutes() {
        return RouterFunctions
                // 具体路径路由必须在通用的 /{id} 路由之前，避免被错误匹配
                .route(GET("/api/v1/auth/audit-logs/failed"), auditLogHandler::listFailedLogs)
                .andRoute(GET("/api/v1/auth/audit-logs/high-risk"), auditLogHandler::listHighRiskLogs)
                .andRoute(GET("/api/v1/auth/audit-logs/user/{userId}"), auditLogHandler::listUserLogs)
                .andRoute(GET("/api/v1/auth/audit-logs/event-type/{eventType}"), auditLogHandler::listLogsByEventType)
                .andRoute(GET("/api/v1/auth/audit-logs/ip/{ipAddress}"), auditLogHandler::listLogsByIpAddress)
                .andRoute(
                        GET("/api/v1/auth/audit-logs/user/{userId}/login-failures"),
                        auditLogHandler::countLoginFailures)
                .andRoute(
                        POST("/api/v1/auth/audit-logs/time-range").and(accept(MediaType.APPLICATION_JSON)),
                        auditLogHandler::listLogsByTimeRange)
                .andRoute(
                        POST("/api/v1/auth/audit-logs/count").and(accept(MediaType.APPLICATION_JSON)),
                        auditLogHandler::countLogs)
                .andRoute(DELETE("/api/v1/auth/audit-logs/cleanup"), auditLogHandler::cleanupExpiredLogs)
                // 通用的 /{id} 路由放在最后，作为兜底匹配
                .andRoute(GET("/api/v1/auth/audit-logs/{id}"), auditLogHandler::getLog);
    }

    /**
     * 审计日志路由的 OpenAPI 元数据。
     *
     * @author Apex Scaffold Team
     */
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/auth/audit-logs/failed",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "listFailedAuditLogs",
                                summary = "查询失败日志",
                                description = "查询失败日志；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"审计日志管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "tenantId",
                                            description = "租户 ID，必须与可信租户上下文匹配，不能覆盖认证租户",
                                            in = ParameterIn.QUERY,
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
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    AuditLogListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/audit-logs/high-risk",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "listHighRiskAuditLogs",
                                summary = "查询高风险日志",
                                description = "查询高风险日志；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"审计日志管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "tenantId",
                                            description = "租户 ID，必须与可信租户上下文匹配，不能覆盖认证租户",
                                            in = ParameterIn.QUERY,
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
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    AuditLogListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/audit-logs/user/{userId}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "listUserAuditLogs",
                                summary = "查询用户审计日志",
                                description = "查询用户审计日志；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"审计日志管理"},
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
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    AuditLogListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/audit-logs/event-type/{eventType}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "listAuditLogsByEventType",
                                summary = "按事件类型查询",
                                description = "按事件类型查询；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"审计日志管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "eventType",
                                            description = "事件类型",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            schema = @Schema(implementation = String.class, type = "string")),
                                    @Parameter(
                                            name = "tenantId",
                                            description = "租户 ID，必须与可信租户上下文匹配，不能覆盖认证租户",
                                            in = ParameterIn.QUERY,
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
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    AuditLogListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/audit-logs/ip/{ipAddress}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "listAuditLogsByIpAddress",
                                summary = "按IP地址查询",
                                description = "按IP地址查询；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"审计日志管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "ipAddress",
                                            description = "IP 地址",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            schema = @Schema(implementation = String.class, type = "string")),
                                    @Parameter(
                                            name = "tenantId",
                                            description = "租户 ID，必须与可信租户上下文匹配，不能覆盖认证租户",
                                            in = ParameterIn.QUERY,
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
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    AuditLogListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/audit-logs/user/{userId}/login-failures",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "countUserLoginFailures",
                                summary = "统计登录失败次数",
                                description = "统计登录失败次数；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"审计日志管理"},
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
                                            required = true,
                                            schema = @Schema(implementation = String.class, type = "string")),
                                    @Parameter(
                                            name = "startTime",
                                            description = "开始时间，ISO 本地日期时间，无时区，如 2026-09-21T10:00:00",
                                            in = ParameterIn.QUERY,
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
                                                            schema = @Schema(implementation = CountResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/audit-logs/time-range",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "listAuditLogsByTimeRange",
                                summary = "按时间范围查询",
                                description = "按时间范围查询；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"审计日志管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "按时间范围查询请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AuditLogQueryRequest.class))),
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
                                                                                    AuditLogListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/audit-logs/count",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "countAuditLogs",
                                summary = "统计日志数量",
                                description = "统计日志数量；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"审计日志管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "统计日志数量请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AuditLogQueryRequest.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = CountResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/audit-logs/cleanup",
                method = RequestMethod.DELETE,
                operation =
                        @Operation(
                                operationId = "cleanupExpiredAuditLogs",
                                summary = "清理过期日志",
                                description = "清理过期日志；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"审计日志管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "tenantId",
                                            description = "租户 ID，必须与可信租户上下文匹配，不能覆盖认证租户",
                                            in = ParameterIn.QUERY,
                                            required = true,
                                            schema = @Schema(implementation = String.class, type = "string")),
                                    @Parameter(
                                            name = "beforeTime",
                                            description = "截止时间，ISO 本地日期时间，无时区，如 2026-09-21T10:00:00",
                                            in = ParameterIn.QUERY,
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
                                                            schema = @Schema(implementation = CountResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/audit-logs/{id}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getAuditLog",
                                summary = "获取审计日志详情",
                                description = "获取审计日志详情；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"审计日志管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "记录 ID",
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
                                                            schema = @Schema(implementation = AuditLogResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                }))
    })
    private @interface AuditLogOperations {}
}
