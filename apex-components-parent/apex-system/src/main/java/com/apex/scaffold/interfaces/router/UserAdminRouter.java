package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.AdminUserManagementHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
 * 后台用户管理路由配置（/admin-api/v1/users、/staff-roles）。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class UserAdminRouter {

    /**
     * 超级管理员用户查询与角色配置路由
     *
     * <p>注册 {@code /admin-api/v1/staff-roles} 与 {@code /admin-api/v1/users/**} 下的用户列表、详情、
     * 创建、编辑、角色替换、会话撤销与启停端点，委托 {@code AdminUserManagementHandler} 处理。
     *
     * @param handler 后台用户管理 Handler
     * @return 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/staff-roles",
                method = RequestMethod.GET,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "roles",
                operation =
                        @Operation(
                                summary = "查询可配置的后台角色",
                                description = "查询当前操作人可配置给其他用户的角色列表",
                                tags = {"用户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/users",
                method = RequestMethod.GET,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                summary = "分页查询用户列表",
                                description = "分页查询后台用户列表，支持条件筛选",
                                tags = {"用户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/users",
                method = RequestMethod.POST,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                summary = "创建用户",
                                description = "创建新的后台用户账号",
                                tags = {"用户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/users/{userId}",
                method = RequestMethod.GET,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                summary = "查询用户详情",
                                description = "根据用户 ID 查询用户详细信息",
                                tags = {"用户管理"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/users/{userId}",
                method = RequestMethod.PUT,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                summary = "更新用户信息",
                                description = "根据用户 ID 更新用户基本信息",
                                tags = {"用户管理"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/users/{userId}/sessions/revoke",
                method = RequestMethod.POST,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "revokeSessions",
                operation =
                        @Operation(
                                summary = "吊销用户会话",
                                description = "吊销指定用户的全部登录会话，强制其重新登录",
                                tags = {"用户管理"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/users/{userId}/status",
                method = RequestMethod.PUT,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                summary = "修改用户状态",
                                description = "启用或禁用指定用户账号",
                                tags = {"用户管理"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/users/{userId}/roles",
                method = RequestMethod.PUT,
                beanClass = AdminUserManagementHandler.class,
                beanMethod = "replaceRoles",
                operation =
                        @Operation(
                                summary = "重置用户角色",
                                description = "全量替换指定用户的角色列表",
                                tags = {"用户管理"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    /**
     * 注册用户管理路由。
     *
     * <p>覆盖员工角色查询与 {@code /admin-api/v1/users} 的列表、创建、详情、更新、删除、
     * 状态与密码等管理动作；写操作要求 JSON 请求体。
     *
     * @param handler 用户管理处理器
     * @return 用户管理路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> adminUserManagementRoutes(AdminUserManagementHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/staff-roles", handler::roles)
                .path("/admin-api/v1/users", routes -> routes.GET("", handler::list)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .GET("/{userId}", handler::detail)
                        .PUT("/{userId}", contentType(MediaType.APPLICATION_JSON), handler::update)
                        .POST("/{userId}/sessions/revoke", handler::revokeSessions)
                        .PUT("/{userId}/status", contentType(MediaType.APPLICATION_JSON), handler::updateStatus)
                        .PUT("/{userId}/roles", contentType(MediaType.APPLICATION_JSON), handler::replaceRoles))
                .build();
    }
}
