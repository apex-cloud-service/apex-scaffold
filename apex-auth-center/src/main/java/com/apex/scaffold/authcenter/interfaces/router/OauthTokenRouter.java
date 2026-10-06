package com.apex.scaffold.authcenter.interfaces.router;

import com.apex.scaffold.authcenter.interfaces.handler.OauthTokenHandler;
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
 * OAuth2令牌路由配置。
 *
 * <p>定义RFC 6749 Token Endpoint（{@code POST /oauth2/token}）的路由绑定，
 * 具体处理逻辑见{@link OauthTokenHandler#token}。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see OauthTokenHandler
 */
@Configuration
public class OauthTokenRouter {
    /**
     * 注册OAuth2令牌签发路由。
     *
     * @param handler OAuth2令牌端点处理器
     * @return 路由函数
     */
    @Bean
    @RouterOperation(
            path = "/oauth2/token",
            method = RequestMethod.POST,
            beanClass = OauthTokenHandler.class,
            beanMethod = "token",
            operation =
                    @Operation(
                            operationId = "oauth2Token",
                            summary = "OAuth2令牌签发",
                            description = "签发OAuth2访问令牌，支持authorization_code/password/"
                                    + "client_credentials/refresh_token四种授权类型，"
                                    + "客户端需通过HTTP Basic或表单方式提供凭据。",
                            tags = {"OAuth2"},
                            requestBody =
                                    @RequestBody(
                                            description = "表单编码的令牌请求参数（grant_type及对应授权类型所需字段）",
                                            required = true,
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                                                            schema = @Schema(implementation = Map.class))),
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "令牌签发成功",
                                        content =
                                                @Content(
                                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                        schema = @Schema(implementation = Map.class))),
                                @ApiResponse(responseCode = "400", description = "请求参数无效或授权类型不受支持"),
                                @ApiResponse(responseCode = "401", description = "客户端凭据校验失败")
                            }))
    public RouterFunction<ServerResponse> oauth2TokenRoutes(OauthTokenHandler handler) {
        return RouterFunctions.route().POST("/oauth2/token", handler::token).build();
    }
}
