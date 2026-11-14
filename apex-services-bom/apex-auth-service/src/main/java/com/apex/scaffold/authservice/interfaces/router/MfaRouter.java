package com.apex.scaffold.authservice.interfaces.router;

import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.EmptyResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.MfaDisabledResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.MfaEnabledResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.MfaSetupResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.MfaStatusResult;
import com.apex.scaffold.auth.application.dto.AuthCenterApiResponses.MfaVerifiedResult;
import com.apex.scaffold.auth.application.dto.MfaSetupRequest;
import com.apex.scaffold.auth.application.dto.MfaVerifyRequest;
import com.apex.scaffold.authservice.interfaces.handler.MfaHandler;
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
 * MFA 多因素认证路由配置
 *
 * <p>定义 MFA 相关的 HTTP 路由，包括设置、验证、禁用和状态查询。
 *
 * @author Apex Scaffold Team
 * @since 1.3.1
 */
@Configuration
@RequiredArgsConstructor
public class MfaRouter {

    private final MfaHandler mfaHandler;

    /**
     * MFA 路由配置
     *
     * <p>路由清单：
     * <ul>
     *   <li>POST /api/v1/mfa/setup - 设置 TOTP MFA</li>
     *   <li>POST /api/v1/mfa/verify - 验证 TOTP 并启用 MFA</li>
     *   <li>POST /api/v1/mfa/verify-code - 验证 TOTP 验证码（登录时）</li>
     *   <li>POST /api/v1/mfa/verify-backup - 验证备份码</li>
     *   <li>POST /api/v1/mfa/disable - 禁用 MFA</li>
     *   <li>GET  /api/v1/mfa/status - 查询 MFA 状态</li>
     * </ul>
     *
     * @return 路由函数
     */
    @Bean
    @MfaOperations
    public RouterFunction<ServerResponse> mfaRoutes() {
        return RouterFunctions.route()
                .POST(
                        "/api/v1/mfa/setup",
                        org.springframework.web.reactive.function.server.RequestPredicates.accept(
                                MediaType.APPLICATION_JSON),
                        mfaHandler::setupMfa)
                .POST(
                        "/api/v1/mfa/verify",
                        org.springframework.web.reactive.function.server.RequestPredicates.accept(
                                MediaType.APPLICATION_JSON),
                        mfaHandler::verifyAndEnableMfa)
                .POST(
                        "/api/v1/mfa/verify-code",
                        org.springframework.web.reactive.function.server.RequestPredicates.accept(
                                MediaType.APPLICATION_JSON),
                        mfaHandler::verifyMfaCode)
                .POST(
                        "/api/v1/mfa/verify-backup",
                        org.springframework.web.reactive.function.server.RequestPredicates.accept(
                                MediaType.APPLICATION_JSON),
                        mfaHandler::verifyBackupCode)
                .POST(
                        "/api/v1/mfa/disable",
                        org.springframework.web.reactive.function.server.RequestPredicates.accept(
                                MediaType.APPLICATION_JSON),
                        mfaHandler::disableMfa)
                .GET("/api/v1/mfa/status", mfaHandler::getMfaStatus)
                .build();
    }

    /**
     * 多因素认证路由的 OpenAPI 元数据。
     *
     * @author Apex Scaffold Team
     */
    @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
    @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/mfa/setup",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "setupMfa",
                                summary = "设置 TOTP MFA",
                                description = "设置 TOTP MFA；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"MFA 多因素认证"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "设置 TOTP MFA请求",
                                                content =
                                                        @Content(
                                                                mediaType = "application/json",
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        MfaSetupRequest.class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = MfaSetupResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体"),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "参数校验或业务异常",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = EmptyResult.class)))
                                })),
        @RouterOperation(
                path = "/api/v1/mfa/verify",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "verifyAndEnableMfa",
                                summary = "验证 TOTP 并启用 MFA",
                                description = "验证 TOTP 并启用 MFA；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"MFA 多因素认证"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "验证 TOTP 并启用 MFA请求",
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
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = MfaEnabledResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体"),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "参数校验或业务异常",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = EmptyResult.class)))
                                })),
        @RouterOperation(
                path = "/api/v1/mfa/verify-code",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "verifyMfaCode",
                                summary = "验证 TOTP 验证码",
                                description = "验证 TOTP 验证码；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"MFA 多因素认证"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "验证 TOTP 验证码请求",
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
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(implementation = MfaVerifiedResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体"),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "参数校验或业务异常",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = EmptyResult.class)))
                                })),
        @RouterOperation(
                path = "/api/v1/mfa/verify-backup",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "verifyBackupCode",
                                summary = "验证备份码",
                                description = "验证备份码；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"MFA 多因素认证"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "验证备份码请求",
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
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(implementation = MfaVerifiedResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体"),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "参数校验或业务异常",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = EmptyResult.class)))
                                })),
        @RouterOperation(
                path = "/api/v1/mfa/disable",
                method = RequestMethod.POST,
                operation =
                        @Operation(
                                operationId = "disableMfa",
                                summary = "禁用 MFA",
                                description = "禁用 MFA；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"MFA 多因素认证"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                requestBody =
                                        @RequestBody(
                                                required = true,
                                                description = "禁用 MFA请求",
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
                                            description = "处理结果",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema =
                                                                    @Schema(implementation = MfaDisabledResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体"),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "参数校验或业务异常",
                                            content =
                                                    @Content(
                                                            mediaType = "application/json",
                                                            schema = @Schema(implementation = EmptyResult.class)))
                                })),
        @RouterOperation(
                path = "/api/v1/mfa/status",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "getMfaStatus",
                                summary = "查询 MFA 状态",
                                description = "查询 MFA 状态；业务处理结果见响应体 code、msg，业务失败可能使用 HTTP 200 返回。",
                                tags = {"MFA 多因素认证"},
                                security = {@SecurityRequirement(name = "bearerAuth")},
                                parameters = {
                                    @Parameter(
                                            name = "userId",
                                            description = "用户 ID",
                                            in = ParameterIn.QUERY,
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
                                                            schema = @Schema(implementation = MfaStatusResult.class))),
                                    @ApiResponse(responseCode = "401", description = "未认证或访问令牌无效，空响应体")
                                }))
    })
    private @interface MfaOperations {}
}
