package com.apex.scaffold.authservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.auth.application.dto.AssignPermissionsRequest;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.EmptyResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.PermissionIdsResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.RoleListResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.RoleResult;
import com.apex.scaffold.auth.application.dto.RoleDTO;
import com.apex.scaffold.authservice.interfaces.handler.RoleHandler;
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
 * 角色管理路由配置
 *
 * <p>定义角色管理相关的HTTP路由规则，采用函数式路由方式配置。
 *
 * <p>路由规则：
 * <ul>
 *   <li>POST /auth/v1/roles - 创建角色</li>
 *   <li>PUT /auth/v1/roles/{id} - 更新角色</li>
 *   <li>DELETE /auth/v1/roles/{id} - 删除角色</li>
 *   <li>GET /auth/v1/roles/{id} - 查询角色详情</li>
 *   <li>GET /auth/v1/roles - 列出所有角色</li>
 *   <li>POST /auth/v1/roles/assign-permissions - 为角色分配权限</li>
 *   <li>GET /auth/v1/roles/{id}/permissions - 查询角色权限</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Configuration
@RequiredArgsConstructor
public class RoleRouter {

    private final RoleHandler roleHandler;

    /**
     * 配置角色管理路由
     *
     * @return RouterFunction
     */
    @Bean
    @RoleOperations
    public RouterFunction<ServerResponse> roleRoutes() {
        return RouterFunctions.route()
                .path("/auth/v1/roles", builder -> builder.POST(
                                "", accept(MediaType.APPLICATION_JSON), roleHandler::createRole)
                        .GET("", accept(MediaType.APPLICATION_JSON), roleHandler::listRoles)
                        .POST("/assign-permissions", accept(MediaType.APPLICATION_JSON), request -> request.bodyToMono(
                                        AssignPermissionsRequest.class)
                                .flatMap(roleHandler::assignPermissions))
                        .PUT("/{id}", accept(MediaType.APPLICATION_JSON), roleHandler::updateRole)
                        .DELETE("/{id}", roleHandler::deleteRole)
                        .GET("/{id}", roleHandler::getRoleById)
                        .GET("/{id}/permissions", roleHandler::getRolePermissions))
                .build();
    }

    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @RouterOperations({
        @RouterOperation(
                path = "/auth/v1/roles",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "createRole",
                                summary = "创建角色",
                                description = "创建角色；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"角色管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "创建角色请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema = @Schema(implementation = RoleDTO.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = RoleResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体"),
                                    @ApiResponse(
                                            responseCode = "403",
                                            description = "需要管理员权限",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = EmptyResult.class)))
                                })),
        @RouterOperation(
                path = "/auth/v1/roles",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "listRoles",
                                summary = "列出角色",
                                description = "列出角色；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"角色管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = RoleListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/roles/assign-permissions",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "assignPermissions",
                                summary = "分配权限",
                                description = "分配权限；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"角色管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "分配权限请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        AssignPermissionsRequest
                                                                                                .class))),
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
                path = "/auth/v1/roles/{id}",
                method = RequestMethod.PUT,
                operation =
                        @Operation(
                                operationId = "updateRole",
                                summary = "更新角色",
                                description = "更新角色；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"角色管理"},
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
                                                description = "更新角色请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema = @Schema(implementation = RoleDTO.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = RoleResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体"),
                                    @ApiResponse(
                                            responseCode = "403",
                                            description = "需要管理员权限",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = EmptyResult.class)))
                                })),
        @RouterOperation(
                path = "/auth/v1/roles/{id}",
                method = RequestMethod.DELETE,
                operation =
                        @Operation(
                                operationId = "deleteRole",
                                summary = "删除角色",
                                description = "删除角色；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"角色管理"},
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
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体"),
                                    @ApiResponse(
                                            responseCode = "403",
                                            description = "需要管理员权限",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = EmptyResult.class)))
                                })),
        @RouterOperation(
                path = "/auth/v1/roles/{id}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getRoleById",
                                summary = "查询角色",
                                description = "查询角色；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"角色管理"},
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
                                                            schema = @Schema(implementation = RoleResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/auth/v1/roles/{id}/permissions",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getRolePermissions",
                                summary = "查询角色权限",
                                description = "查询角色权限；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"角色管理"},
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
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    PermissionIdsResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                }))
    })
    private @interface RoleOperations {}
}
