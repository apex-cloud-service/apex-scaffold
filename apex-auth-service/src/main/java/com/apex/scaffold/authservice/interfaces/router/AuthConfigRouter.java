package com.apex.scaffold.authservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.AuthConfigListResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.AuthConfigResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.EmptyResult;
import com.apex.scaffold.auth.application.dto.CreateAuthConfigRequest;
import com.apex.scaffold.auth.application.dto.UpdateAuthConfigRequest;
import com.apex.scaffold.authservice.interfaces.handler.AuthConfigHandler;
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
 * 认证配置路由配置
 *
 * <p>定义认证配置相关的HTTP路由。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Configuration
@RequiredArgsConstructor
public class AuthConfigRouter {

    private final AuthConfigHandler authConfigHandler;

    /**
     * 认证配置路由
     *
     * @return 路由函数
     */
    @Bean
    @AuthConfigOperations
    public RouterFunction<ServerResponse> authConfigRoutes() {
        return RouterFunctions.route(
                        POST("/api/v1/auth/configs").and(accept(MediaType.APPLICATION_JSON)),
                        authConfigHandler::createConfig)
                .andRoute(
                        PUT("/api/v1/auth/configs/{id}").and(accept(MediaType.APPLICATION_JSON)),
                        authConfigHandler::updateConfig)
                .andRoute(DELETE("/api/v1/auth/configs/{id}"), authConfigHandler::deleteConfig)
                // 注意：更具体的路径必须在通配路径之前
                .andRoute(GET("/api/v1/auth/configs/effective"), authConfigHandler::getEffectiveConfig)
                .andRoute(GET("/api/v1/auth/configs/type/{type}"), authConfigHandler::getConfigsByType)
                .andRoute(GET("/api/v1/auth/configs"), authConfigHandler::listConfigs)
                .andRoute(GET("/api/v1/auth/configs/{id}"), authConfigHandler::getConfig);
    }

    /**
     * 认证配置路由的 OpenAPI 元数据。
     *
     * @author Apex Scaffold Team
     */
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/auth/configs",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "createAuthConfig",
                                summary = "创建认证配置",
                                description = "创建认证配置；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"认证配置管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "创建认证配置请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        CreateAuthConfigRequest
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = AuthConfigResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/configs/{id}",
                method = RequestMethod.PUT,
                operation =
                        @Operation(
                                operationId = "updateAuthConfig",
                                summary = "更新认证配置",
                                description = "更新认证配置；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"认证配置管理"},
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
                                                            format = "int64")),
                                    @Parameter(
                                            name = "tenantId",
                                            description = "租户 ID，必须与可信租户上下文匹配，不能覆盖认证租户",
                                            in = ParameterIn.QUERY,
                                            required = true,
                                            schema = @Schema(implementation = String.class, type = "string"))
                                },
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "更新认证配置请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        UpdateAuthConfigRequest
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = AuthConfigResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/configs/{id}",
                method = RequestMethod.DELETE,
                operation =
                        @Operation(
                                operationId = "deleteAuthConfig",
                                summary = "删除认证配置",
                                description = "删除认证配置；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"认证配置管理"},
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
                                                            schema = @Schema(implementation = EmptyResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/configs/effective",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getEffectiveAuthConfig",
                                summary = "获取有效配置",
                                description = "获取有效配置；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"认证配置管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "configType",
                                            description = "配置类型",
                                            in = ParameterIn.QUERY,
                                            required = true,
                                            schema = @Schema(implementation = String.class, type = "string")),
                                    @Parameter(
                                            name = "configKey",
                                            description = "配置键",
                                            in = ParameterIn.QUERY,
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
                                                            schema = @Schema(implementation = AuthConfigResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/configs/type/{type}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getAuthConfigsByType",
                                summary = "根据类型获取配置",
                                description = "根据类型获取配置；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"认证配置管理"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "type",
                                            description = "配置类型",
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
                                                                                    AuthConfigListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/configs",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "listAuthConfigs",
                                summary = "获取认证配置列表",
                                description = "获取认证配置列表；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"认证配置管理"},
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
                                                                                    AuthConfigListResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                })),
        @RouterOperation(
                path = "/api/v1/auth/configs/{id}",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getAuthConfig",
                                summary = "获取认证配置详情",
                                description = "获取认证配置详情；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"认证配置管理"},
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
                                                            schema = @Schema(implementation = AuthConfigResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                }))
    })
    private @interface AuthConfigOperations {}
}
