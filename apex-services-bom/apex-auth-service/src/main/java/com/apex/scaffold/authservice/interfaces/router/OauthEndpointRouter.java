package com.apex.scaffold.authservice.interfaces.router;

import com.apex.scaffold.authservice.interfaces.handler.OAuthEndpointHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * OAuth2 授权、撤销与内省路由，均沿用原生协议响应，不使用 Result 包装。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Configuration
public class OauthEndpointRouter {
    /**
     * 注册要求已认证用户和 S256 PKCE 的授权码签发路由。
     *
     * @param handler OAuth2 授权端点处理器
     * @return 授权路由
     */
    @Bean
    @RouterOperation(
            path = "/oauth2/authorize",
            method = RequestMethod.GET,
            beanClass = OAuthEndpointHandler.class,
            beanMethod = "authorize",
            operation =
                    @Operation(
                            operationId = "oauth2Authorize",
                            summary = "签发 PKCE 授权码",
                            description = "使用已认证主体和可信租户签发授权码；成功重定向至已注册的 redirect_uri，附带 code 与可选 state。重复查询参数被拒绝。",
                            tags = {"OAuth2"},
                            responses = {
                                @ApiResponse(
                                        responseCode = "302",
                                        description = "授权成功，空响应体；Cache-Control: no-store",
                                        content = @Content,
                                        headers =
                                                @Header(
                                                        name = "Location",
                                                        description = "携带授权码和可选 state 的回调地址",
                                                        schema = @Schema(type = "string", format = "uri"))),
                                @ApiResponse(
                                        responseCode = "400",
                                        description = "授权请求无效",
                                        content =
                                                @Content(
                                                        mediaType = "application/json",
                                                        schema = @Schema(implementation = OAuthError.class))),
                                @ApiResponse(
                                        responseCode = "401",
                                        description = "处理器未获取到已认证用户时返回 access_denied；安全过滤器也可能提前拒绝请求",
                                        content =
                                                @Content(
                                                        mediaType = "application/json",
                                                        schema = @Schema(implementation = OAuthError.class)))
                            }))
    public RouterFunction<ServerResponse> oauth2EndpointRoutes(OAuthEndpointHandler handler) {
        return RouterFunctions.route()
                .GET("/oauth2/authorize", handler::authorize)
                // 统一认证收口（A5）：用户授权同意/拒绝端点（authorize 返回 consent_required 后引导）
                .POST("/oauth2/consent", handler::consent)
                .build();
    }

    /**
     * 注册 RFC 7009 撤销路由。
     *
     * @param handler OAuth2 授权端点处理器
     * @return 撤销路由
     */
    @Bean
    @RouterOperation(
            path = "/oauth2/revoke",
            method = RequestMethod.POST,
            beanClass = OAuthEndpointHandler.class,
            beanMethod = "revoke",
            operation =
                    @Operation(
                            operationId = "oauth2Revoke",
                            summary = "撤销授权令牌",
                            description =
                                    "以 HTTP Basic 或 client_id/client_secret 表单凭据认证客户端，两种方式互斥；重复参数被拒绝。返回原生 OAuth JSON。",
                            tags = {"OAuth2"},
                            requestBody =
                                    @RequestBody(
                                            required = true,
                                            description = "待撤销令牌及可选表单客户端凭据",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                                                            schema = @Schema(implementation = TokenActionForm.class))),
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "撤销完成，包括未找到令牌的情况；响应为空 JSON 对象",
                                        content =
                                                @Content(
                                                        mediaType = "application/json",
                                                        schema = @Schema(type = "object", example = "{}"))),
                                @ApiResponse(
                                        responseCode = "400",
                                        description = "请求无效",
                                        content =
                                                @Content(
                                                        mediaType = "application/json",
                                                        schema = @Schema(implementation = OAuthError.class))),
                                @ApiResponse(
                                        responseCode = "401",
                                        description = "客户端凭据校验失败",
                                        content =
                                                @Content(
                                                        mediaType = "application/json",
                                                        schema = @Schema(implementation = OAuthError.class)))
                            }))
    public RouterFunction<ServerResponse> oauth2RevokeRoutes(OAuthEndpointHandler handler) {
        return RouterFunctions.route().POST("/oauth2/revoke", handler::revoke).build();
    }

    /**
     * 注册 RFC 7662 令牌内省路由。
     *
     * @param handler OAuth2 授权端点处理器
     * @return 内省路由
     */
    @Bean
    @RouterOperation(
            path = "/oauth2/introspect",
            method = RequestMethod.POST,
            beanClass = OAuthEndpointHandler.class,
            beanMethod = "introspect",
            operation =
                    @Operation(
                            operationId = "oauth2Introspect",
                            summary = "查询令牌状态",
                            description =
                                    "以 HTTP Basic 或表单客户端凭据认证，两种方式互斥。令牌无效、过期、已撤销或不属于该客户端时，仅返回 active=false；响应不经 Result 包装。",
                            tags = {"OAuth2"},
                            requestBody =
                                    @RequestBody(
                                            required = true,
                                            description = "待查询令牌及可选表单客户端凭据",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                                                            schema = @Schema(implementation = TokenActionForm.class))),
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "令牌有效性及可选元数据",
                                        content =
                                                @Content(
                                                        mediaType = "application/json",
                                                        schema =
                                                                @Schema(implementation = IntrospectionResponse.class))),
                                @ApiResponse(
                                        responseCode = "400",
                                        description = "请求无效",
                                        content =
                                                @Content(
                                                        mediaType = "application/json",
                                                        schema = @Schema(implementation = OAuthError.class))),
                                @ApiResponse(
                                        responseCode = "401",
                                        description = "客户端凭据校验失败",
                                        content =
                                                @Content(
                                                        mediaType = "application/json",
                                                        schema = @Schema(implementation = OAuthError.class)))
                            }))
    public RouterFunction<ServerResponse> oauth2IntrospectRoutes(OAuthEndpointHandler handler) {
        return RouterFunctions.route()
                .POST("/oauth2/introspect", handler::introspect)
                .build();
    }

    /**
     * OAuth 原生错误体，仅用于 OpenAPI 建模。
     *
     * @param error OAuth 协议错误码
     */
    @Schema(description = "原生 OAuth 错误响应，不含 Result 字段")
    public record OAuthError(
            @Schema(description = "OAuth 协议错误码", requiredMode = Schema.RequiredMode.REQUIRED) String error) {}

    /**
     * 撤销和内省共用的表单文档模型。
     *
     * @param token 访问令牌或刷新令牌
     * @param clientId 表单客户端标识；不得与 HTTP Basic 同时提交
     * @param clientSecret 表单客户端密钥
     */
    @Schema(description = "token 必填；未使用 HTTP Basic 时 client_id 与 client_secret 必填")
    public record TokenActionForm(
            @Schema(description = "访问令牌或刷新令牌", requiredMode = Schema.RequiredMode.REQUIRED) String token,
            @Schema(name = "client_id", description = "表单客户端标识；不得与 HTTP Basic 同时提交") String clientId,
            @Schema(name = "client_secret", description = "表单客户端密钥", accessMode = Schema.AccessMode.WRITE_ONLY)
                    String clientSecret) {}

    /**
     * 原生内省结果，除 active 外的字段仅在令牌有效时返回。
     *
     * @param active 令牌是否有效
     * @param clientId 客户端标识
     * @param sub 令牌主体
     * @param scope 授权范围
     * @param tokenType 令牌类型
     * @param exp 过期时间，UTC Unix 秒
     */
    @Schema(description = "令牌内省结果")
    public record IntrospectionResponse(
            @Schema(description = "令牌是否有效", requiredMode = Schema.RequiredMode.REQUIRED) Boolean active,
            @Schema(name = "client_id", description = "客户端标识") String clientId,
            @Schema(description = "令牌主体") String sub,
            @Schema(description = "授权范围") String scope,
            @Schema(
                            name = "token_type",
                            description = "令牌类型",
                            allowableValues = {"Bearer"})
                    String tokenType,
            @Schema(description = "过期时间，UTC Unix 秒", type = "integer", format = "int64") Long exp) {}
}
