package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.ApiCallLogHandler;
import com.apex.scaffold.interfaces.handler.AuditLogHandler;
import com.apex.scaffold.interfaces.handler.LoginLogHandler;
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
 * 日志路由配置（登录/审计/API 调用/运行时/业务日志）。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class LogRouter {

    /**
     * 系统管理-登录日志路由（/admin-api/v1/login-logs）。
     *
     * <p>提供登录日志的分页回溯查询端点，经 {@code RequirePermissionAspect}
     * 校验 {@code system:login-log:list} 权限。</p>
     *
     * @param handler 登录日志处理器
     * @return 登录日志管理路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/login-logs",
                method = RequestMethod.GET,
                beanClass = LoginLogHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                summary = "分页查询登录日志",
                                description = "分页查询系统登录成功与失败日志",
                                tags = {"登录日志"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> adminLoginLogRoutes(LoginLogHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/login-logs", handler::list)
                .build();
    }

    /**
     * 系统管理-审计日志路由（/admin-api/v1/audit-logs）。
     *
     * <p>提供操作审计日志的分页查询端点，经 {@code RequirePermissionAspect}
     * 校验 {@code system:audit-log:list} 权限（V4 迁移预置）。</p>
     *
     * @param handler 审计日志处理器
     * @return 审计日志管理路由函数
     * @since 2.2.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/audit-logs",
                method = RequestMethod.GET,
                beanClass = AuditLogHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                summary = "分页查询审计日志",
                                description = "分页查询操作审计日志",
                                tags = {"审计日志"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> adminAuditLogRoutes(AuditLogHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/audit-logs", handler::list)
                .build();
    }

    /**
     * 系统管理-API调用日志路由（/admin-api/v1/api-call-logs）。
     *
     * <p>提供对外部 HTTP 服务调用日志（{@code sys_api_call_log} 表）的分页查询端点，
     * 经 {@code RequirePermissionAspect} 校验 {@code system:api-call-log:list} 权限
     * （V13 迁移预置）。</p>
     *
     * @param handler API 调用日志处理器
     * @return API 调用日志管理路由函数
     * @since 2.3.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/api-call-logs",
                method = RequestMethod.GET,
                beanClass = ApiCallLogHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                summary = "分页查询 API 调用日志",
                                description = "分页查询外部 HTTP 服务调用日志",
                                tags = {"API调用日志"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> adminApiCallLogRoutes(ApiCallLogHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/api-call-logs", handler::list)
                .build();
    }

    /**
     * 运行时日志路由（/admin-api/v1/logs/runtime）。
     *
     * <p>提供运行时日志查询功能。</p>
     *
     * @param handler 运行时日志处理器
     * @return 日志路由函数
     * @since 1.3.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/logs/runtime",
                method = RequestMethod.GET,
                beanClass = com.apex.scaffold.interfaces.handler.RuntimeLogHandler.class,
                beanMethod = "queryLogs",
                operation =
                        @Operation(
                                summary = "查询运行时日志",
                                description = "按条件查询应用运行时日志",
                                tags = {"运行时日志"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/logs/runtime/count",
                method = RequestMethod.GET,
                beanClass = com.apex.scaffold.interfaces.handler.RuntimeLogHandler.class,
                beanMethod = "countLogs",
                operation =
                        @Operation(
                                summary = "统计运行时日志数量",
                                description = "按条件统计运行时日志数量",
                                tags = {"运行时日志"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> runtimeLogRoutes(
            com.apex.scaffold.interfaces.handler.RuntimeLogHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/logs/runtime", handler::queryLogs)
                .GET("/admin-api/v1/logs/runtime/count", handler::countLogs)
                .build();
    }

    /**
     * 业务日志统一查询路由（/admin-api/v1/logs/business）。
     *
     * <p>整合登录日志、操作日志、API调用日志，提供统一查询接口。</p>
     *
     * @param handler 业务日志处理器
     * @return 日志路由函数
     * @since 1.3.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/logs/business",
                method = RequestMethod.GET,
                beanClass = com.apex.scaffold.interfaces.handler.UnifiedBusinessLogHandler.class,
                beanMethod = "queryLogs",
                operation =
                        @Operation(
                                summary = "查询业务日志",
                                description = "按条件查询登录、操作与 API 调用等业务日志",
                                tags = {"业务日志"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/logs/business/count",
                method = RequestMethod.GET,
                beanClass = com.apex.scaffold.interfaces.handler.UnifiedBusinessLogHandler.class,
                beanMethod = "countLogs",
                operation =
                        @Operation(
                                summary = "统计业务日志数量",
                                description = "按条件统计业务日志数量",
                                tags = {"业务日志"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> businessLogRoutes(
            com.apex.scaffold.interfaces.handler.UnifiedBusinessLogHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/logs/business", handler::queryLogs)
                .GET("/admin-api/v1/logs/business/count", handler::countLogs)
                .build();
    }
}
