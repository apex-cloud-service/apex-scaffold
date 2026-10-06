package com.apex.scaffold.authcenter.interfaces.router;

import com.apex.scaffold.authcenter.interfaces.handler.OauthEndpointHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Map;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * OAuth2授权端点路由配置。
 *
 * <p>定义授权码签发（{@code GET /oauth2/authorize}）、令牌撤销（RFC 7009，
 * {@code POST /oauth2/revoke}）、令牌状态查询（RFC 7662，{@code POST /oauth2/introspect}）
 * 三类路由绑定，具体处理逻辑见{@link OauthEndpointHandler}。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see OauthEndpointHandler
 */
@Configuration
public class OauthEndpointRouter {
    /**
     * 注册OAuth2授权码签发路由。
     *
     * @param handler OAuth2授权端点处理器
     * @return 路由函数
     */
    @Bean
    @RouterOperation(
            path = "/oauth2/authorize",
            method = RequestMethod.GET,
            beanClass = OauthEndpointHandler.class,
            beanMethod = "authorize",
            operation =
                    @Operation(
                            operationId = "oauth2Authorize",
                            summary = "签发PKCE授权码",
                            description =
                                    "基于当前已认证的用户主体签发OAuth2授权码，" + "用于后续在Token Endpoint换取访问令牌（authorization_code授权类型）。",
                            tags = {"OAuth2"},
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "授权码签发成功",
                                        content =
                                                @Content(
                                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                        schema = @Schema(implementation = Map.class))),
                                @ApiResponse(responseCode = "400", description = "请求参数无效"),
                                @ApiResponse(responseCode = "401", description = "用户未认证")
                            }))
    public RouterFunction<ServerResponse> oauth2EndpointRoutes(OauthEndpointHandler handler) {
        return RouterFunctions.route()
                .GET("/oauth2/authorize", handler::authorize)
                .build();
    }

    /**
     * 注册OAuth2令牌撤销路由。
     *
     * @param handler OAuth2授权端点处理器
     * @return 路由函数
     */
    @Bean
    @RouterOperation(
            path = "/oauth2/revoke",
            method = RequestMethod.POST,
            beanClass = OauthEndpointHandler.class,
            beanMethod = "revoke",
            operation =
                    @Operation(
                            operationId = "oauth2Revoke",
                            summary = "撤销授权令牌",
                            description = "依据RFC 7009撤销指定的访问令牌或刷新令牌，" + "客户端需通过HTTP Basic或表单方式提供凭据。",
                            tags = {"OAuth2"},
                            requestBody =
                                    @RequestBody(
                                            description = "表单编码的撤销请求参数（token字段）",
                                            required = true,
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                                                            schema = @Schema(implementation = Map.class))),
                            responses = {
                                @ApiResponse(responseCode = "200", description = "撤销成功"),
                                @ApiResponse(responseCode = "401", description = "客户端凭据校验失败")
                            }))
    public RouterFunction<ServerResponse> oauth2RevokeRoutes(OauthEndpointHandler handler) {
        return RouterFunctions.route().POST("/oauth2/revoke", handler::revoke).build();
    }

    /**
     * 注册OAuth2令牌内省路由。
     *
     * @param handler OAuth2授权端点处理器
     * @return 路由函数
     */
    @Bean
    @RouterOperation(
            path = "/oauth2/introspect",
            method = RequestMethod.POST,
            beanClass = OauthEndpointHandler.class,
            beanMethod = "introspect",
            operation =
                    @Operation(
                            operationId = "oauth2Introspect",
                            summary = "查询令牌状态",
                            description = "依据RFC 7662查询指定令牌的有效性及其元数据，" + "客户端需通过HTTP Basic或表单方式提供凭据。",
                            tags = {"OAuth2"},
                            requestBody =
                                    @RequestBody(
                                            description = "表单编码的查询请求参数（token字段）",
                                            required = true,
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                                                            schema = @Schema(implementation = Map.class))),
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "查询成功，返回令牌状态及元数据",
                                        content =
                                                @Content(
                                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                        schema = @Schema(implementation = Map.class))),
                                @ApiResponse(responseCode = "401", description = "客户端凭据校验失败")
                            }))
    public RouterFunction<ServerResponse> oauth2IntrospectRoutes(OauthEndpointHandler handler) {
        return RouterFunctions.route()
                .POST("/oauth2/introspect", handler::introspect)
                .build();
    }
}
