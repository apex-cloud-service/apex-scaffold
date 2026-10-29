package com.apex.scaffold.authservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.EmptyResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.PermissionListResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.PermissionResult;
import com.apex.scaffold.auth.application.dto.PermissionDTO;
import com.apex.scaffold.authservice.interfaces.handler.PermissionHandler;
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
 * 权限管理路由配置
 *
 * <p>定义权限管理相关的HTTP路由规则，采用函数式路由方式配置。
 *
 * <p>路由规则：
 * <ul>
 *   <li>POST /auth/v1/permissions - 创建权限</li>
 *   <li>PUT /auth/v1/permissions/{id} - 更新权限</li>
 *   <li>DELETE /auth/v1/permissions/{id} - 删除权限</li>
 *   <li>GET /auth/v1/permissions/{id} - 查询权限详情</li>
 *   <li>GET /auth/v1/permissions - 列出所有权限</li>
 *   <li>GET /auth/v1/permissions/resource/{resource} - 按资源查询权限</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Configuration
@RequiredArgsConstructor
public class PermissionRouter {

    private final PermissionHandler permissionHandler;

    /**
     * 配置权限管理路由
     *
     * @return RouterFunction
     */
    @Bean
    @PermissionOperations
    public RouterFunction<ServerResponse> permissionRoutes() {
        return RouterFunctions.route()
                .path("/auth/v1/permissions", builder -> builder.POST(
                                "", accept(MediaType.APPLICATION_JSON), permissionHandler::createPermission)
                        .GET("", accept(MediaType.APPLICATION_JSON), permissionHandler::listPermissions)
                        .GET("/resource/{resource}", permissionHandler::getPermissionsByResource)
                        .PUT("/{id}", accept(MediaType.APPLICATION_JSON), permissionHandler::updatePermission)
                        .DELETE("/{id}", permissionHandler::deletePermission)
                        .GET("/{id}", permissionHandler::getPermissionById))
                .build();
    }

    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @RouterOperations({
        @RouterOperation(
                path = "/auth/v1/permissions",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "createPermission",
                                summary = "创建权限",
                                description = "创建权限；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"权限管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "创建权限请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema =
                                                                        @Schema(implementation = PermissionDTO.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = PermissionResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/permissions",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "listPermissions",
                                summary = "列出权限",
                                description = "列出权限；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"权限管理"},
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
                                                                                    PermissionListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/permissions/resource/{resource}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getPermissionsByResource",
                                summary = "按资源查询权限",
                                description = "按资源查询权限；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"权限管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "resource",
                                            description = "资源标识",
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
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    PermissionListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/permissions/{id}",
                method = RequestMethod.PUT,
                operation =
                        @Operation(
                                operationId = "updatePermission",
                                summary = "更新权限",
                                description = "更新权限；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"权限管理"},
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
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "更新权限请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema =
                                                                        @Schema(implementation = PermissionDTO.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = PermissionResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/permissions/{id}",
                method = RequestMethod.DELETE,
                operation =
                        @Operation(
                                operationId = "deletePermission",
                                summary = "删除权限",
                                description = "删除权限；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"权限管理"},
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
                                                            schema = @Schema(implementation = EmptyResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/permissions/{id}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getPermissionById",
                                summary = "查询权限",
                                description = "查询权限；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"权限管理"},
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
                                                            schema = @Schema(implementation = PermissionResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                }))
    })
    private @interface PermissionOperations {}
}
