package com.apex.scaffold.authservice.interfaces.router;

import com.apex.scaffold.authservice.interfaces.handler.UnifiedAuthorizationHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 注册受保护的统一授权快照接口。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class UnifiedAuthorizationRouter {
    /**
     * 注册统一授权快照查询路由。
     *
     * @param handler 统一授权处理器
     * @return 路由函数
     */
    @Bean
    @RouterOperation(
            path = "/auth/v1/authorization/users/{userId}",
            method = RequestMethod.GET,
            beanClass = UnifiedAuthorizationHandler.class,
            beanMethod = "query",
            operation =
                    @Operation(
                            operationId = "queryUnifiedAuthorization",
                            summary = "查询中心用户授权",
                            description = "仅可信授权查询账号可以查询其访问令牌租户内的用户",
                            tags = {"统一授权"},
                            security = @SecurityRequirement(name = "bearerAuth"),
                            parameters =
                                    @Parameter(
                                            name = "userId",
                                            description = "中心用户正整数 ID",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            schema =
                                                    @io.swagger.v3.oas.annotations.media.Schema(
                                                            implementation = Long.class,
                                                            type = "integer",
                                                            format = "int64",
                                                            minimum = "1")),
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "Result 封装的授权快照",
                                        content =
                                                @io.swagger.v3.oas.annotations.media.Content(
                                                        mediaType = "application/json",
                                                        schema =
                                                                @io.swagger.v3.oas.annotations.media.Schema(
                                                                        implementation =
                                                                                com.apex.scaffold.authservice.interfaces
                                                                                        .dto
                                                                                        .UnifiedAuthorizationResponse
                                                                                        .UnifiedAuthorizationResult
                                                                                        .class))),
                                @ApiResponse(
                                        responseCode = "400",
                                        description = "用户 ID 非法",
                                        content =
                                                @io.swagger.v3.oas.annotations.media.Content(
                                                        mediaType = "application/json",
                                                        schema =
                                                                @io.swagger.v3.oas.annotations.media.Schema(
                                                                        implementation =
                                                                                com.apex.scaffold.common.result.Result
                                                                                        .class))),
                                @ApiResponse(
                                        responseCode = "401",
                                        description = "未认证",
                                        content = @io.swagger.v3.oas.annotations.media.Content),
                                @ApiResponse(
                                        responseCode = "403",
                                        description = "禁止查询",
                                        content =
                                                @io.swagger.v3.oas.annotations.media.Content(
                                                        mediaType = "application/json",
                                                        schema =
                                                                @io.swagger.v3.oas.annotations.media.Schema(
                                                                        implementation =
                                                                                com.apex.scaffold.common.result.Result
                                                                                        .class)))
                            }))
    public RouterFunction<ServerResponse> unifiedAuthorizationRoutes(UnifiedAuthorizationHandler handler) {
        return RouterFunctions.route()
                .GET("/auth/v1/authorization/users/{userId}", handler::query)
                .build();
    }
}
