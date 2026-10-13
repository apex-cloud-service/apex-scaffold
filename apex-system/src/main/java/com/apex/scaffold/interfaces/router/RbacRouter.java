package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.DeptHandler;
import com.apex.scaffold.interfaces.handler.MenuHandler;
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
 * RBAC 路由配置（菜单 /admin-api/v1/menus、部门 /admin-api/v1/depts）。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class RbacRouter {

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
}
