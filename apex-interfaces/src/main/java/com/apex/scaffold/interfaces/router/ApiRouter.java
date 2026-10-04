package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.AdminUserManagementHandler;
import com.apex.scaffold.interfaces.handler.ApiCallLogHandler;
import com.apex.scaffold.interfaces.handler.AuditLogHandler;
import com.apex.scaffold.interfaces.handler.AuthHandler;
import com.apex.scaffold.interfaces.handler.AuthorizationHandler;
import com.apex.scaffold.interfaces.handler.CaptchaHandler;
import com.apex.scaffold.interfaces.handler.ConfigHandler;
import com.apex.scaffold.interfaces.handler.DatabaseConfigHandler;
import com.apex.scaffold.interfaces.handler.DeptHandler;
import com.apex.scaffold.interfaces.handler.DictHandler;
import com.apex.scaffold.interfaces.handler.ExcelExportHandler;
import com.apex.scaffold.interfaces.handler.FileHandler;
import com.apex.scaffold.interfaces.handler.HealthHandler;
import com.apex.scaffold.interfaces.handler.JobHandler;
import com.apex.scaffold.interfaces.handler.LoginLogHandler;
import com.apex.scaffold.interfaces.handler.MenuHandler;
import com.apex.scaffold.interfaces.handler.MonitorDashboardHandler;
import com.apex.scaffold.interfaces.handler.NotificationSseHandler;
import com.apex.scaffold.interfaces.handler.OfficeHandler;
import com.apex.scaffold.interfaces.handler.OnlineUserHandler;
import com.apex.scaffold.interfaces.handler.UserHandler;
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
 * API 路由配置（函数式端点）
 *
 * <p>
 * 将所有接口以 {@link RouterFunction} 方式注册，替代基于注解的 Controller
 *
 * <h2>路由分组</h2>
 * <ul>
 * <li>/api/v1/auth - 认证与授权接口</li>
 * <li>/api/v1/user - 用户管理接口</li>
 * <li>/api/v1/file、/api/v1/files - 文件上传下载接口</li>
 * <li>/api/v1/health、/api/v1/info - 健康检查接口</li>
 * <li>/admin-api/v1 - 后台用户管理接口</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
public class ApiRouter {

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
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
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
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
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
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
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

    /** 超级管理员用户查询与角色配置路由。 */
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

    /**
     * 系统管理-菜单路由（/admin-api/v1/menus）。
     *
     * <p>提供菜单树的查询、详情、创建、更新、删除与启停管理端点，
     * 所有端点经 {@code RequirePermissionAspect} 校验 {@code system:menu:*} 权限。</p>
     *
     * @param handler 系统菜单处理器
     * @return 菜单管理路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/menus",
                method = RequestMethod.GET,
                beanClass = MenuHandler.class,
                beanMethod = "tree",
                operation =
                        @Operation(
                                summary = "查询菜单树",
                                description = "查询完整菜单树结构",
                                tags = {"菜单管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/menus/{menuId}",
                method = RequestMethod.GET,
                beanClass = MenuHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                summary = "查询菜单详情",
                                description = "根据菜单 ID 查询菜单详细信息",
                                tags = {"菜单管理"},
                                parameters = {
                                    @Parameter(
                                            name = "menuId",
                                            description = "菜单 ID",
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
                path = "/admin-api/v1/menus",
                method = RequestMethod.POST,
                beanClass = MenuHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                summary = "创建菜单",
                                description = "创建新的菜单节点",
                                tags = {"菜单管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/menus/{menuId}",
                method = RequestMethod.PUT,
                beanClass = MenuHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                summary = "更新菜单",
                                description = "根据菜单 ID 更新菜单信息",
                                tags = {"菜单管理"},
                                parameters = {
                                    @Parameter(
                                            name = "menuId",
                                            description = "菜单 ID",
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
                path = "/admin-api/v1/menus/{menuId}",
                method = RequestMethod.DELETE,
                beanClass = MenuHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                summary = "删除菜单",
                                description = "根据菜单 ID 删除菜单",
                                tags = {"菜单管理"},
                                parameters = {
                                    @Parameter(
                                            name = "menuId",
                                            description = "菜单 ID",
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
                path = "/admin-api/v1/menus/{menuId}/status",
                method = RequestMethod.PUT,
                beanClass = MenuHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                summary = "修改菜单启停状态",
                                description = "启用或禁用指定菜单",
                                tags = {"菜单管理"},
                                parameters = {
                                    @Parameter(
                                            name = "menuId",
                                            description = "菜单 ID",
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
    @Bean
    public RouterFunction<ServerResponse> adminMenuRoutes(MenuHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/menus", routes -> routes.GET("", handler::tree)
                        .GET("/{menuId}", handler::detail)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .PUT("/{menuId}", contentType(MediaType.APPLICATION_JSON), handler::update)
                        .DELETE("/{menuId}", handler::delete)
                        .PUT("/{menuId}/status", contentType(MediaType.APPLICATION_JSON), handler::updateStatus))
                .build();
    }

    /**
     * 系统管理-字典路由（/admin-api/v1/dict）。
     *
     * <p>提供字典类型与字典数据的查询、创建、更新、删除与启停管理端点，
     * 所有端点经 {@code RequirePermissionAspect} 校验 {@code system:dict:*} 权限。</p>
     *
     * @param handler 系统字典处理器
     * @return 字典管理路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/dict/types",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "listTypes",
                operation =
                        @Operation(
                                summary = "分页查询字典类型",
                                description = "分页查询字典类型列表，支持条件筛选",
                                tags = {"字典管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/dict/types/{id}",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "getType",
                operation =
                        @Operation(
                                summary = "查询字典类型详情",
                                description = "根据 ID 查询字典类型详细信息",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典类型 ID",
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
                path = "/admin-api/v1/dict/types",
                method = RequestMethod.POST,
                beanClass = DictHandler.class,
                beanMethod = "createType",
                operation =
                        @Operation(
                                summary = "创建字典类型",
                                description = "创建新的字典类型",
                                tags = {"字典管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/dict/types/{id}",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateType",
                operation =
                        @Operation(
                                summary = "更新字典类型",
                                description = "根据 ID 更新字典类型信息",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典类型 ID",
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
                path = "/admin-api/v1/dict/types/{id}",
                method = RequestMethod.DELETE,
                beanClass = DictHandler.class,
                beanMethod = "deleteType",
                operation =
                        @Operation(
                                summary = "删除字典类型",
                                description = "根据 ID 删除字典类型",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典类型 ID",
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
                path = "/admin-api/v1/dict/types/{id}/status",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateTypeStatus",
                operation =
                        @Operation(
                                summary = "修改字典类型启停状态",
                                description = "启用或禁用指定字典类型",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典类型 ID",
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
                path = "/admin-api/v1/dict/data",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "listData",
                operation =
                        @Operation(
                                summary = "分页查询字典数据",
                                description = "分页查询指定字典类型下的字典数据列表",
                                tags = {"字典管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/dict/data/{id}",
                method = RequestMethod.GET,
                beanClass = DictHandler.class,
                beanMethod = "getData",
                operation =
                        @Operation(
                                summary = "查询字典数据详情",
                                description = "根据 ID 查询字典数据详细信息",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典数据 ID",
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
                path = "/admin-api/v1/dict/data",
                method = RequestMethod.POST,
                beanClass = DictHandler.class,
                beanMethod = "createData",
                operation =
                        @Operation(
                                summary = "创建字典数据",
                                description = "在指定字典类型下创建新的字典数据",
                                tags = {"字典管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/dict/data/{id}",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateData",
                operation =
                        @Operation(
                                summary = "更新字典数据",
                                description = "根据 ID 更新字典数据信息",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典数据 ID",
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
                path = "/admin-api/v1/dict/data/{id}",
                method = RequestMethod.DELETE,
                beanClass = DictHandler.class,
                beanMethod = "deleteData",
                operation =
                        @Operation(
                                summary = "删除字典数据",
                                description = "根据 ID 删除字典数据",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典数据 ID",
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
                path = "/admin-api/v1/dict/data/{id}/status",
                method = RequestMethod.PUT,
                beanClass = DictHandler.class,
                beanMethod = "updateDataStatus",
                operation =
                        @Operation(
                                summary = "修改字典数据启停状态",
                                description = "启用或禁用指定字典数据",
                                tags = {"字典管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "字典数据 ID",
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
    @Bean
    public RouterFunction<ServerResponse> adminDictRoutes(DictHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/dict/types", types -> types.GET("", handler::listTypes)
                        .GET("/{id}", handler::getType)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::createType)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::updateType)
                        .DELETE("/{id}", handler::deleteType)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateTypeStatus))
                .path("/admin-api/v1/dict/data", data -> data.GET("", handler::listData)
                        .GET("/{id}", handler::getData)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::createData)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::updateData)
                        .DELETE("/{id}", handler::deleteData)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateDataStatus))
                .build();
    }

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
     * 系统管理-部门管理路由（/admin-api/v1/depts）。
     *
     * <p>提供部门树与 CRUD 端点（C3 数据权限基础），经
     * {@code RequirePermissionAspect} 校验 {@code system:dept:*} 权限（V6 迁移预置）。</p>
     *
     * @param handler 部门管理处理器
     * @return 部门管理路由函数
     * @since 2.2.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/depts",
                method = RequestMethod.GET,
                beanClass = DeptHandler.class,
                beanMethod = "listTree",
                operation =
                        @Operation(
                                summary = "查询部门树",
                                description = "查询完整部门树结构",
                                tags = {"部门管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/depts/{id}",
                method = RequestMethod.GET,
                beanClass = DeptHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                summary = "查询部门详情",
                                description = "根据部门 ID 查询部门详细信息",
                                tags = {"部门管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "部门 ID",
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
                path = "/admin-api/v1/depts",
                method = RequestMethod.POST,
                beanClass = DeptHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                summary = "创建部门",
                                description = "创建新的部门节点",
                                tags = {"部门管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/depts/{id}",
                method = RequestMethod.PUT,
                beanClass = DeptHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                summary = "更新部门",
                                description = "根据部门 ID 更新部门信息",
                                tags = {"部门管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "部门 ID",
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
                path = "/admin-api/v1/depts/{id}",
                method = RequestMethod.DELETE,
                beanClass = DeptHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                summary = "删除部门",
                                description = "根据部门 ID 删除部门",
                                tags = {"部门管理"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "部门 ID",
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
    @Bean
    public RouterFunction<ServerResponse> adminDeptRoutes(DeptHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/depts", handler::listTree)
                .GET("/admin-api/v1/depts/{id}", handler::detail)
                .POST("/admin-api/v1/depts", handler::create)
                .PUT("/admin-api/v1/depts/{id}", handler::update)
                .DELETE("/admin-api/v1/depts/{id}", handler::delete)
                .build();
    }
    /**
     * 数据导出路由（/admin-api/v1/export/users）。
     *
     * <p>提供 Excel 导出端点（C5），经 {@code RequirePermissionAspect}
     * 校验 {@code system:user:list} 权限。</p>
     *
     * @param handler Excel 导出处理器
     * @return 导出路由函数
     * @since 2.2.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/export/users",
                method = RequestMethod.GET,
                beanClass = ExcelExportHandler.class,
                beanMethod = "exportUsers",
                operation =
                        @Operation(
                                summary = "导出用户列表",
                                description = "按条件导出用户列表为 Excel 文件",
                                tags = {"数据导出"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> adminExportRoutes(ExcelExportHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/export/users", handler::exportUsers)
                .build();
    }

    /**
     * 站内通知 SSE 路由（/api/v1/notifications/stream）。
     *
     * <p>提供实时通知推送端点（C6），基于认证 Principal 定向推送。</p>
     *
     * @param handler SSE 通知处理器
     * @return SSE 推送路由函数
     * @since 2.2.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/notifications/stream",
                method = RequestMethod.GET,
                beanClass = NotificationSseHandler.class,
                beanMethod = "stream",
                operation =
                        @Operation(
                                operationId = "streamNotifications",
                                summary = "订阅实时通知",
                                description = "建立 SSE 连接，接收站内实时通知推送",
                                tags = {"通知中心"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "SSE连接建立成功，开始推送通知流",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                                                            schema = @Schema(implementation = String.class)))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> notificationSseRoutes(NotificationSseHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/notifications/stream", handler::stream)
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
     * 图形验证码路由（/api/v1/auth/captcha）。
     *
     * <p>提供验证码获取与一次性校验端点，供登录场景防自动化爆破使用。</p>
     *
     * @param handler 图形验证码处理器
     * @return 验证码路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/auth/captcha",
                method = RequestMethod.GET,
                beanClass = CaptchaHandler.class,
                beanMethod = "generate",
                operation =
                        @Operation(
                                summary = "获取图形验证码",
                                description = "生成图形验证码，用于登录防自动化爆破",
                                tags = {"验证码"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/captcha/verify",
                method = RequestMethod.POST,
                beanClass = CaptchaHandler.class,
                beanMethod = "verify",
                operation =
                        @Operation(
                                summary = "校验图形验证码",
                                description = "一次性校验图形验证码是否正确",
                                tags = {"验证码"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> captchaRoutes(CaptchaHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/auth/captcha", handler::generate)
                .POST("/api/v1/auth/captcha/verify", contentType(MediaType.APPLICATION_JSON), handler::verify)
                .build();
    }

    /**
     * 系统管理-定时任务路由（/admin-api/v1/jobs）。
     *
     * <p>提供定时任务的查询、创建、更新、删除、启停、手动触发与
     * 执行日志端点，所有端点经 {@code RequirePermissionAspect} 校验
     * {@code system:job:*} 权限。</p>
     *
     * @param handler 定时任务处理器
     * @return 定时任务管理路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/jobs",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                summary = "查询定时任务列表",
                                description = "查询全部定时任务",
                                tags = {"定时任务"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/jobs/page",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "page",
                operation =
                        @Operation(
                                summary = "分页查询定时任务",
                                description = "分页查询定时任务列表，支持条件筛选",
                                tags = {"定时任务"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                summary = "查询定时任务详情",
                                description = "根据任务 ID 查询定时任务详细信息",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
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
                path = "/admin-api/v1/jobs",
                method = RequestMethod.POST,
                beanClass = JobHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                summary = "创建定时任务",
                                description = "创建新的定时任务",
                                tags = {"定时任务"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.PUT,
                beanClass = JobHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                summary = "更新定时任务",
                                description = "根据任务 ID 更新定时任务信息",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
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
                path = "/admin-api/v1/jobs/{id}",
                method = RequestMethod.DELETE,
                beanClass = JobHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                summary = "删除定时任务",
                                description = "根据任务 ID 删除定时任务",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
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
                path = "/admin-api/v1/jobs/{id}/status",
                method = RequestMethod.PUT,
                beanClass = JobHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                summary = "修改定时任务启停状态",
                                description = "启用或禁用指定定时任务",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
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
                path = "/admin-api/v1/jobs/{id}/run",
                method = RequestMethod.POST,
                beanClass = JobHandler.class,
                beanMethod = "runOnce",
                operation =
                        @Operation(
                                summary = "手动触发定时任务",
                                description = "立即触发指定定时任务执行一次",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
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
                path = "/admin-api/v1/jobs/{id}/logs",
                method = RequestMethod.GET,
                beanClass = JobHandler.class,
                beanMethod = "logs",
                operation =
                        @Operation(
                                summary = "查询任务执行日志",
                                description = "分页查询指定定时任务的执行日志",
                                tags = {"定时任务"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "任务 ID",
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
    @Bean
    public RouterFunction<ServerResponse> adminJobRoutes(JobHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/jobs", routes -> routes.GET("", handler::list)
                        .GET("/page", handler::page)
                        .GET("/{id}", handler::detail)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::update)
                        .DELETE("/{id}", handler::delete)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateStatus)
                        .POST("/{id}/run", handler::runOnce)
                        .GET("/{id}/logs", handler::logs))
                .build();
    }

    /**
     * 系统管理-数据库配置路由（/admin-api/v1/database）。
     *
     * <p>提供数据库连接测试、部署配置生成、当前数据库信息查询，以及数据源
     * 管理的完整 CRUD 与连接池监控端点。所有端点经 {@code RequirePermissionAspect}
     * 校验 {@code system:database:*} 权限；数据源管理端点要求
     * {@code system:database:datasource:*} 细粒度权限。</p>
     *
     * <h2>数据源管理端点</h2>
     * <ul>
     *   <li>GET /admin-api/v1/database/datasources - 分页查询数据源列表</li>
     *   <li>POST /admin-api/v1/database/datasources - 创建数据源</li>
     *   <li>PUT /admin-api/v1/database/datasources/{id} - 更新数据源</li>
     *   <li>DELETE /admin-api/v1/database/datasources/{id} - 删除数据源</li>
     *   <li>GET /admin-api/v1/database/datasources/{id}/metrics - 查询连接池监控指标</li>
     * </ul>
     *
     * @param handler 数据库配置处理器
     * @return 数据库配置管理路由函数
     * @since 1.2.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/database/test",
                method = RequestMethod.POST,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "testConnection",
                operation =
                        @Operation(
                                summary = "测试数据库连接",
                                description = "使用给定连接信息测试数据库连通性",
                                tags = {"数据库配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/database/generate-config",
                method = RequestMethod.POST,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "generateConfig",
                operation =
                        @Operation(
                                summary = "生成部署配置",
                                description = "根据连接信息生成数据源部署配置",
                                tags = {"数据库配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/database/current",
                method = RequestMethod.GET,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "getCurrentInfo",
                operation =
                        @Operation(
                                summary = "查询当前数据库信息",
                                description = "查询当前应用所连接数据库的信息",
                                tags = {"数据库配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/database/datasources",
                method = RequestMethod.GET,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "listDataSources",
                operation =
                        @Operation(
                                summary = "分页查询数据源列表",
                                description = "分页查询已登记的数据源列表",
                                tags = {"数据库配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/database/datasources",
                method = RequestMethod.POST,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "createDataSource",
                operation =
                        @Operation(
                                summary = "创建数据源",
                                description = "登记新的数据源配置",
                                tags = {"数据库配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/database/datasources/{id}",
                method = RequestMethod.PUT,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "updateDataSource",
                operation =
                        @Operation(
                                summary = "更新数据源",
                                description = "根据 ID 更新数据源配置信息",
                                tags = {"数据库配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "数据源 ID",
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
                path = "/admin-api/v1/database/datasources/{id}",
                method = RequestMethod.DELETE,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "deleteDataSource",
                operation =
                        @Operation(
                                summary = "删除数据源",
                                description = "根据 ID 删除数据源配置",
                                tags = {"数据库配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "数据源 ID",
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
                path = "/admin-api/v1/database/datasources/{id}/metrics",
                method = RequestMethod.GET,
                beanClass = DatabaseConfigHandler.class,
                beanMethod = "getDataSourceMetrics",
                operation =
                        @Operation(
                                summary = "查询数据源连接池指标",
                                description = "查询指定数据源的连接池监控指标",
                                tags = {"数据库配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "数据源 ID",
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
    @Bean
    public RouterFunction<ServerResponse> adminDatabaseConfigRoutes(DatabaseConfigHandler handler) {
        return RouterFunctions.route()
                .POST("/admin-api/v1/database/test", contentType(MediaType.APPLICATION_JSON), handler::testConnection)
                .POST(
                        "/admin-api/v1/database/generate-config",
                        contentType(MediaType.APPLICATION_JSON),
                        handler::generateConfig)
                .GET("/admin-api/v1/database/current", handler::getCurrentInfo)
                .path("/admin-api/v1/database/datasources", routes -> routes.GET("", handler::listDataSources)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::createDataSource)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::updateDataSource)
                        .DELETE("/{id}", handler::deleteDataSource)
                        .GET("/{id}/metrics", handler::getDataSourceMetrics))
                .build();
    }

    /**
     * 系统管理-参数配置路由（/admin-api/v1/configs）。
     *
     * <p>提供参数配置的查询、详情、创建、更新、删除与启停管理端点，
     * 所有端点经 {@code RequirePermissionAspect} 校验 {@code system:config:*} 权限。</p>
     *
     * @param handler 系统参数配置处理器
     * @return 参数配置管理路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/configs",
                method = RequestMethod.GET,
                beanClass = ConfigHandler.class,
                beanMethod = "list",
                operation =
                        @Operation(
                                summary = "分页查询参数配置",
                                description = "分页查询系统参数配置列表，支持条件筛选",
                                tags = {"参数配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/configs/{id}",
                method = RequestMethod.GET,
                beanClass = ConfigHandler.class,
                beanMethod = "detail",
                operation =
                        @Operation(
                                summary = "查询参数配置详情",
                                description = "根据 ID 查询参数配置详细信息",
                                tags = {"参数配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "配置 ID",
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
                path = "/admin-api/v1/configs",
                method = RequestMethod.POST,
                beanClass = ConfigHandler.class,
                beanMethod = "create",
                operation =
                        @Operation(
                                summary = "创建参数配置",
                                description = "创建新的系统参数配置",
                                tags = {"参数配置"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/configs/{id}",
                method = RequestMethod.PUT,
                beanClass = ConfigHandler.class,
                beanMethod = "update",
                operation =
                        @Operation(
                                summary = "更新参数配置",
                                description = "根据 ID 更新参数配置信息",
                                tags = {"参数配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "配置 ID",
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
                path = "/admin-api/v1/configs/{id}",
                method = RequestMethod.DELETE,
                beanClass = ConfigHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                summary = "删除参数配置",
                                description = "根据 ID 删除参数配置",
                                tags = {"参数配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "配置 ID",
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
                path = "/admin-api/v1/configs/{id}/status",
                method = RequestMethod.PUT,
                beanClass = ConfigHandler.class,
                beanMethod = "updateStatus",
                operation =
                        @Operation(
                                summary = "修改参数配置启停状态",
                                description = "启用或禁用指定参数配置",
                                tags = {"参数配置"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "配置 ID",
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
    @Bean
    public RouterFunction<ServerResponse> adminConfigRoutes(ConfigHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/configs", routes -> routes.GET("", handler::list)
                        .GET("/{id}", handler::detail)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::create)
                        .PUT("/{id}", contentType(MediaType.APPLICATION_JSON), handler::update)
                        .DELETE("/{id}", handler::delete)
                        .PUT("/{id}/status", contentType(MediaType.APPLICATION_JSON), handler::updateStatus))
                .build();
    }

    /**
     * 注册所有 API 路由（/api/v1）。
     *
     * @param authHandler 认证处理器
     * @param authorizationHandler 授权处理器
     * @param userHandler 用户管理处理器
     * @param fileHandler 文件处理器
     * @param healthHandler 健康检查处理器
     * @return 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/health",
                method = RequestMethod.GET,
                beanClass = HealthHandler.class,
                beanMethod = "health",
                operation =
                        @Operation(
                                summary = "健康检查",
                                description = "返回应用健康状态",
                                tags = {"健康检查"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/info",
                method = RequestMethod.GET,
                beanClass = HealthHandler.class,
                beanMethod = "info",
                operation =
                        @Operation(
                                summary = "查询应用信息",
                                description = "返回应用名称、版本等基本信息",
                                tags = {"健康检查"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/register",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "register",
                operation =
                        @Operation(
                                summary = "用户注册",
                                description = "使用一次性票据完成用户注册",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/wechat/bind-phone",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "bindWechatPhone",
                operation =
                        @Operation(
                                summary = "验证并绑定微信手机号",
                                description = "注册前验证并绑定微信手机号",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/login",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "login",
                operation =
                        @Operation(
                                summary = "用户登录",
                                description = "APP 登录，支持微信登录、创蓝一键登录与短信验证码登录",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/refresh",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "refresh",
                operation =
                        @Operation(
                                summary = "刷新令牌",
                                description = "使用刷新令牌换取新的访问令牌",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/logout",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "logout",
                operation =
                        @Operation(
                                summary = "退出登录",
                                description = "注销当前访问令牌并清除会话",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/sms/code",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "sendSmsCode",
                operation =
                        @Operation(
                                summary = "发送短信验证码",
                                description = "发送微信短信登录所需的短信验证码",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/cl/sms/code",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "sendClSmsCode",
                operation =
                        @Operation(
                                summary = "发送创蓝短信验证码",
                                description = "发送创蓝短信登录所需的短信验证码",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/cl/validate-mobile",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "validateMobile",
                operation =
                        @Operation(
                                summary = "本机号校验",
                                description = "校验本机号码是否可用于一键登录",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/wx/phone",
                method = RequestMethod.POST,
                beanClass = AuthHandler.class,
                beanMethod = "bindPhone",
                operation =
                        @Operation(
                                summary = "绑定微信手机号",
                                description = "将微信授权手机号绑定到当前账号",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/roles/my",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getMyRoles",
                operation =
                        @Operation(
                                summary = "查询我的角色",
                                description = "查询当前登录用户的角色列表",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/permissions/my",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getMyPermissions",
                operation =
                        @Operation(
                                summary = "查询我的权限",
                                description = "查询当前登录用户的权限列表",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/roles",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getAllRoles",
                operation =
                        @Operation(
                                summary = "查询全部角色",
                                description = "查询系统中全部角色列表",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/permissions",
                method = RequestMethod.GET,
                beanClass = AuthorizationHandler.class,
                beanMethod = "getAllPermissions",
                operation =
                        @Operation(
                                summary = "查询全部权限",
                                description = "查询系统中全部权限列表",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/roles",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "createRole",
                operation =
                        @Operation(
                                summary = "创建角色",
                                description = "创建新的系统角色",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/roles/update",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "updateRole",
                operation =
                        @Operation(
                                summary = "更新角色",
                                description = "更新指定角色的信息",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/roles/{id}",
                method = RequestMethod.DELETE,
                beanClass = AuthorizationHandler.class,
                beanMethod = "deleteRole",
                operation =
                        @Operation(
                                summary = "删除角色",
                                description = "根据角色 ID 删除角色",
                                tags = {"认证授权"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "角色 ID",
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
                path = "/api/v1/auth/permissions",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "createPermission",
                operation =
                        @Operation(
                                summary = "创建权限",
                                description = "创建新的系统权限",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/permissions/update",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "updatePermission",
                operation =
                        @Operation(
                                summary = "更新权限",
                                description = "更新指定权限的信息",
                                tags = {"认证授权"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/auth/permissions/{id}",
                method = RequestMethod.DELETE,
                beanClass = AuthorizationHandler.class,
                beanMethod = "deletePermission",
                operation =
                        @Operation(
                                summary = "删除权限",
                                description = "根据权限 ID 删除权限",
                                tags = {"认证授权"},
                                parameters = {
                                    @Parameter(
                                            name = "id",
                                            description = "权限 ID",
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
                path = "/api/v1/auth/users/{userId}/roles/{roleId}",
                method = RequestMethod.POST,
                beanClass = AuthorizationHandler.class,
                beanMethod = "assignRoleToUser",
                operation =
                        @Operation(
                                summary = "为用户分配角色",
                                description = "将指定角色分配给指定用户",
                                tags = {"认证授权"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string")),
                                    @Parameter(
                                            name = "roleId",
                                            description = "角色 ID",
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
                path = "/api/v1/auth/users/{userId}/roles/{roleId}",
                method = RequestMethod.DELETE,
                beanClass = AuthorizationHandler.class,
                beanMethod = "removeRoleFromUser",
                operation =
                        @Operation(
                                summary = "移除用户角色",
                                description = "移除指定用户的指定角色",
                                tags = {"认证授权"},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string")),
                                    @Parameter(
                                            name = "roleId",
                                            description = "角色 ID",
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
                path = "/api/v1/user/info",
                method = RequestMethod.GET,
                beanClass = UserHandler.class,
                beanMethod = "getCurrentUserInfo",
                operation =
                        @Operation(
                                summary = "获取当前用户信息",
                                description = "获取当前登录用户的详细信息",
                                tags = {"用户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/user/me/deletion-code",
                method = RequestMethod.POST,
                beanClass = UserHandler.class,
                beanMethod = "sendAccountDeletionCode",
                operation =
                        @Operation(
                                summary = "发送账号注销验证码",
                                description = "向当前账号绑定手机号发送注销二次验证短信",
                                tags = {"用户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/user/me",
                method = RequestMethod.DELETE,
                beanClass = UserHandler.class,
                beanMethod = "deleteCurrentUser",
                operation =
                        @Operation(
                                summary = "注销当前账号",
                                description = "校验注销验证码并注销当前登录账号",
                                tags = {"用户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/user/{userId}",
                method = RequestMethod.GET,
                beanClass = UserHandler.class,
                beanMethod = "getUserById",
                operation =
                        @Operation(
                                summary = "查询用户信息",
                                description = "根据用户 ID 查询用户信息",
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
                path = "/api/v1/user/list",
                method = RequestMethod.POST,
                beanClass = UserHandler.class,
                beanMethod = "listUsers",
                operation =
                        @Operation(
                                summary = "查询用户列表",
                                description = "按条件查询用户列表",
                                tags = {"用户管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/upload",
                method = RequestMethod.POST,
                beanClass = FileHandler.class,
                beanMethod = "upload",
                operation =
                        @Operation(
                                summary = "上传文件",
                                description = "上传文件并返回文件标识",
                                tags = {"文件管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/content",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "content",
                operation =
                        @Operation(
                                summary = "获取文件内容",
                                description = "按文件标识获取文件内容流",
                                tags = {"文件管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/url",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "getUrl",
                operation =
                        @Operation(
                                summary = "获取文件访问地址",
                                description = "按文件标识获取文件的访问 URL",
                                tags = {"文件管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/delete",
                method = RequestMethod.DELETE,
                beanClass = FileHandler.class,
                beanMethod = "delete",
                operation =
                        @Operation(
                                summary = "删除文件",
                                description = "按文件标识删除文件",
                                tags = {"文件管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/file/exists",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "exists",
                operation =
                        @Operation(
                                summary = "检查文件是否存在",
                                description = "按文件标识检查文件是否存在",
                                tags = {"文件管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/files/generic",
                method = RequestMethod.POST,
                beanClass = FileHandler.class,
                beanMethod = "uploadGenericFile",
                operation =
                        @Operation(
                                summary = "上传通用文件",
                                description = "通过 multipart 表单上传通用文件",
                                tags = {"文件管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/",
                method = RequestMethod.GET,
                beanClass = HealthHandler.class,
                beanMethod = "root",
                operation =
                        @Operation(
                                summary = "服务根路径",
                                description = "返回服务欢迎信息，用于确认服务可用",
                                tags = {"健康检查"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> apiRoutes(
            AuthHandler authHandler,
            AuthorizationHandler authorizationHandler,
            UserHandler userHandler,
            FileHandler fileHandler,
            HealthHandler healthHandler) {
        RouterFunctions.Builder routerBuilder = RouterFunctions.route();
        routerBuilder.path(
                "/api/v1",
                api -> buildApiRoutes(api, authHandler, authorizationHandler, userHandler, fileHandler, healthHandler));
        routerBuilder.GET("/", healthHandler::root);
        return routerBuilder.build();
    }

    /**
     * 构建 /api/v1 下的所有通用路由。
     *
     * @param api 路由构建器
     * @param authHandler 认证处理器
     * @param authorizationHandler 授权处理器
     * @param userHandler 用户管理处理器
     * @param fileHandler 文件处理器
     * @param healthHandler 健康检查处理器
     * @return 构建后的路由构建器
     */
    private RouterFunctions.Builder buildApiRoutes(
            RouterFunctions.Builder api,
            AuthHandler authHandler,
            AuthorizationHandler authorizationHandler,
            UserHandler userHandler,
            FileHandler fileHandler,
            HealthHandler healthHandler) {
        return api.GET("/health", healthHandler::health)
                .GET("/info", healthHandler::info)
                .path("/auth", auth -> buildAuthRoutes(auth, authHandler, authorizationHandler))
                .path("/user", user -> buildUserRoutes(user, userHandler))
                .path("/file", file -> buildFileRoutes(file, fileHandler))
                .path(
                        "/files",
                        files -> files.POST(
                                "/generic",
                                contentType(MediaType.MULTIPART_FORM_DATA),
                                fileHandler::uploadGenericFile));
    }

    /**
     * 构建认证与授权相关路由。
     *
     * <p>
     * 路由前缀：/api/v1/auth
     *
     * <p>
     * 提供以下认证接口：
     * <ul>
     *   <li>POST /api/v1/auth/login - APP登录（支持微信登录、创蓝一键登录、短信验证码登录）</li>
     *   <li>POST /api/v1/auth/register - 使用一次性票据完成注册</li>
     *   <li>POST /api/v1/auth/wechat/bind-phone - 注册前验证并绑定微信手机号</li>
     *   <li>POST /api/v1/auth/refresh - 刷新Token</li>
     *   <li>POST /api/v1/auth/logout - 退出登录</li>
     *   <li>POST /api/v1/auth/sms/code - 发送短信验证码（微信短信登录）</li>
     *   <li>POST /api/v1/auth/cl/sms/code - 发送创蓝短信验证码（创蓝短信登录）</li>
     *   <li>POST /api/v1/auth/wx/phone - 绑定微信手机号</li>
     * </ul>
     *
     * @param auth 路由构建器
     * @param authHandler 认证处理器
     * @param authorizationHandler 授权处理器
     * @return 构建后的路由构建器
     */
    private RouterFunctions.Builder buildAuthRoutes(
            RouterFunctions.Builder auth, AuthHandler authHandler, AuthorizationHandler authorizationHandler) {
        auth.POST("/register", accept(MediaType.APPLICATION_JSON), authHandler::register);
        auth.POST("/wechat/bind-phone", accept(MediaType.APPLICATION_JSON), authHandler::bindWechatPhone);
        return auth.POST("/login", accept(MediaType.APPLICATION_JSON), authHandler::login)
                .POST("/refresh", authHandler::refresh)
                .POST("/logout", authHandler::logout)
                .POST("/sms/code", accept(MediaType.APPLICATION_JSON), authHandler::sendSmsCode)
                .POST("/cl/sms/code", accept(MediaType.APPLICATION_JSON), authHandler::sendClSmsCode)
                .POST("/cl/validate-mobile", authHandler::validateMobile)
                .POST("/wx/phone", accept(MediaType.APPLICATION_JSON), authHandler::bindPhone)
                .GET("/roles/my", authorizationHandler::getMyRoles)
                .GET("/permissions/my", authorizationHandler::getMyPermissions)
                .GET("/roles", authorizationHandler::getAllRoles)
                .GET("/permissions", authorizationHandler::getAllPermissions)
                .POST("/roles", accept(MediaType.APPLICATION_JSON), authorizationHandler::createRole)
                .POST("/roles/update", accept(MediaType.APPLICATION_JSON), authorizationHandler::updateRole)
                .DELETE("/roles/{id}", authorizationHandler::deleteRole)
                .POST("/permissions", accept(MediaType.APPLICATION_JSON), authorizationHandler::createPermission)
                .POST("/permissions/update", accept(MediaType.APPLICATION_JSON), authorizationHandler::updatePermission)
                .DELETE("/permissions/{id}", authorizationHandler::deletePermission)
                .POST("/users/{userId}/roles/{roleId}", authorizationHandler::assignRoleToUser)
                .DELETE("/users/{userId}/roles/{roleId}", authorizationHandler::removeRoleFromUser);
    }

    /**
     * 构建用户管理相关路由。
     *
     * <p>
     * 路由前缀：/api/v1/user
     *
     * @param user 路由构建器
     * @param handler 用户管理处理器
     * @return 构建后的路由构建器
     */
    private RouterFunctions.Builder buildUserRoutes(RouterFunctions.Builder user, UserHandler handler) {
        return user.GET("/info", handler::getCurrentUserInfo)
                .POST("/me/deletion-code", handler::sendAccountDeletionCode)
                .DELETE("/me", contentType(MediaType.APPLICATION_JSON), handler::deleteCurrentUser)
                .GET("/{userId}", handler::getUserById)
                .POST("/list", accept(MediaType.APPLICATION_JSON), handler::listUsers);
    }

    /**
     * 构建文件相关路由。
     *
     * <p>
     * 路由前缀：/api/v1/file
     *
     * @param file 路由构建器
     * @param handler 文件处理器
     * @return 构建后的路由构建器
     */
    private RouterFunctions.Builder buildFileRoutes(RouterFunctions.Builder file, FileHandler handler) {
        return file.POST("/upload", handler::upload)
                .GET("/content", handler::content)
                .GET("/url", handler::getUrl)
                .DELETE("/delete", handler::delete)
                .GET("/exists", handler::exists);
    }

    /**
     * 统一搜索路由（/admin-api/v1/search）。
     *
     * <p>提供全文搜索和索引管理功能。</p>
     *
     * @param handler 统一搜索处理器
     * @return 搜索路由函数
     * @since 1.3.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/search",
                method = RequestMethod.GET,
                beanClass = com.apex.scaffold.interfaces.handler.UnifiedSearchHandler.class,
                beanMethod = "search",
                operation =
                        @Operation(
                                summary = "统一全文搜索",
                                description = "按关键词执行统一全文搜索",
                                tags = {"统一搜索"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/search/rebuild",
                method = RequestMethod.POST,
                beanClass = com.apex.scaffold.interfaces.handler.UnifiedSearchHandler.class,
                beanMethod = "rebuildIndex",
                operation =
                        @Operation(
                                summary = "重建搜索索引",
                                description = "触发统一搜索索引重建",
                                tags = {"统一搜索"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> unifiedSearchRoutes(
            com.apex.scaffold.interfaces.handler.UnifiedSearchHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/search", handler::search)
                .POST("/admin-api/v1/search/rebuild", handler::rebuildIndex)
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
