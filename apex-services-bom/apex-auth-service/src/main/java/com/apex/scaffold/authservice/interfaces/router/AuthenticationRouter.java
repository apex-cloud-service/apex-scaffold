package com.apex.scaffold.authservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.apex.scaffold.auth.application.dto.LoginRequest;
import com.apex.scaffold.auth.application.dto.LoginResponse;
import com.apex.scaffold.auth.application.dto.MfaVerifyRequest;
import com.apex.scaffold.auth.application.dto.RefreshTokenRequest;
import com.apex.scaffold.auth.domain.model.vo.TokenPair;
import com.apex.scaffold.authservice.interfaces.handler.AuthenticationHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 认证路由配置，注册登录、注销与令牌刷新接口。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "仅接受 ACCESS 访问令牌；刷新令牌不能用于认证")
@Configuration
@RequiredArgsConstructor
public class AuthenticationRouter {

    private final AuthenticationHandler authenticationHandler;

    /**
     * 注册认证相关路由：登录、登出、刷新与 MFA 二步验证。
     *
     * @return 认证路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/auth/v1/login",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "login",
                                summary = "用户名密码登录",
                                description =
                                        "无需认证。租户由服务端 auth-center.login-tenant 确定；请求 tenantId 仅用于匹配校验，不能覆盖可信租户。成功直接返回登录响应，不包装 Result。",
                                tags = {"认证"},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "用户名密码登录请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema = @Schema(implementation = LoginRequest.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = LoginResponse.class))),
                                    @ApiResponse(
                                            responseCode = "401",
                                            description = "未认证或凭据无效",
                                            content =
                                                    @Content(
                                                            mediaType = "text/plain",
                                                            schema =
                                                                    @Schema(
                                                                            implementation = String.class,
                                                                            type = "string"))),
                                    @ApiResponse(
                                            responseCode = "403",
                                            description = "账户禁用、锁定或租户不匹配",
                                            content =
                                                    @Content(
                                                            mediaType = "text/plain",
                                                            schema =
                                                                    @Schema(
                                                                            implementation = String.class,
                                                                            type = "string"))),
                                    @ApiResponse(
                                            responseCode = "500",
                                            description = "系统错误",
                                            content =
                                                    @Content(
                                                            mediaType = "text/plain",
                                                            schema =
                                                                    @Schema(
                                                                            implementation = String.class,
                                                                            type = "string")))
                                })),
        @RouterOperation(
                path = "/auth/v1/logout/{userId}",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "logout",
                                summary = "注销当前用户",
                                description =
                                        "必须携带 Bearer 访问令牌，刷新令牌不能用于认证。userId 必须与认证主体一致；租户取自已验证令牌，忽略外部 tenantId 与 X-Tenant-Id。注销撤销该用户的全部令牌，成功返回空响应体。",
                                tags = {"认证"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID，注销时必须等于认证主体",
                                            in = ParameterIn.PATH,
                                            required = true,
                                            schema =
                                                    @Schema(
                                                            implementation = Long.class,
                                                            type = "integer",
                                                            format = "int64"))
                                },
                                responses = {
                                    @ApiResponse(responseCode = "200", description = "注销成功，空响应体"),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效"),
                                    @ApiResponse(responseCode = "403", description = "路径用户与认证主体不一致")
                                })),
        @RouterOperation(
                path = "/auth/v1/refresh",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "refreshToken",
                                summary = "轮换刷新令牌",
                                description =
                                        "无需访问令牌。刷新令牌仅能使用一次，成功轮换为新令牌对；重放、撤销或过期令牌返回 401。租户由刷新令牌确定。成功直接返回 TokenPair，不包装 Result。",
                                tags = {"认证"},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "轮换刷新令牌请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        RefreshTokenRequest.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = TokenPair.class))),
                                    @ApiResponse(
                                            responseCode = "401",
                                            description = "刷新令牌无效、过期、撤销或重复使用",
                                            content =
                                                    @Content(
                                                            mediaType = "text/plain",
                                                            schema =
                                                                    @Schema(
                                                                            implementation = String.class,
                                                                            type = "string"))),
                                    @ApiResponse(
                                            responseCode = "500",
                                            description = "刷新失败",
                                            content =
                                                    @Content(
                                                            mediaType = "text/plain",
                                                            schema =
                                                                    @Schema(
                                                                            implementation = String.class,
                                                                            type = "string")))
                                })),
        @RouterOperation(
                path = "/auth/v1/mfa/verify",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "verifyMfaChallenge",
                                summary = "MFA 二步验证",
                                description = "无需访问令牌：以登录返回的短 TTL 挑战凭据和 TOTP 验证码交换正式令牌。"
                                        + "挑战凭据一次性消费；无效、已使用或验证码错误返回 401，账户禁用或锁定返回 403。"
                                        + "成功返回与登录同构的 LoginResponse。",
                                tags = {"认证"},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "MFA 挑战凭据与验证码",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        MfaVerifyRequest.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "交换成功，返回正式令牌",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = LoginResponse.class))),
                                    @ApiResponse(responseCode = "401", description = "挑战凭据无效、已使用或验证码错误"),
                                    @ApiResponse(responseCode = "403", description = "账户禁用或锁定")
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> authenticationRoutes() {
        return route().POST(
                        "/auth/v1/login",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        authenticationHandler::login)
                .POST(
                        "/auth/v1/logout/{userId}",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        authenticationHandler::logout)
                .POST(
                        "/auth/v1/refresh",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        authenticationHandler::refreshToken)
                // 统一认证收口（A4）：MFA 二步验证，挑战凭据 + 验证码交换正式令牌
                .POST(
                        "/auth/v1/mfa/verify",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        authenticationHandler::verifyMfa)
                .build();
    }
}
