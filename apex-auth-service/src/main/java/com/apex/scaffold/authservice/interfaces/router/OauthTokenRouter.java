package com.apex.scaffold.authservice.interfaces.router;

import com.apex.scaffold.authservice.interfaces.handler.OauthTokenHandler;
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
 * OAuth2 令牌端点路由，返回原生协议 JSON，不使用 Result 包装。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
public class OauthTokenRouter {
    /**
     * 注册 OAuth2 令牌签发路由。
     *
     * @param handler 令牌端点处理器
     * @return 令牌路由
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
                            description =
                                    "支持 authorization_code、password、client_credentials、refresh_token。客户端通过 HTTP Basic 或表单认证，二者互斥；拒绝重复参数。响应禁止缓存。",
                            tags = {"OAuth2"},
                            requestBody =
                                    @RequestBody(
                                            required = true,
                                            description = "表单编码参数；各授权类型所需字段见模型说明",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
                                                            schema = @Schema(implementation = TokenForm.class))),
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "原生 OAuth 令牌响应",
                                        content =
                                                @Content(
                                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                        schema = @Schema(implementation = TokenResponse.class))),
                                @ApiResponse(
                                        responseCode = "400",
                                        description = "参数、授权或授权类型无效",
                                        content =
                                                @Content(
                                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                        schema =
                                                                @Schema(
                                                                        implementation =
                                                                                OauthEndpointRouter.OAuthError.class))),
                                @ApiResponse(
                                        responseCode = "401",
                                        description = "客户端认证失败",
                                        headers =
                                                @Header(
                                                        name = "WWW-Authenticate",
                                                        schema =
                                                                @Schema(
                                                                        type = "string",
                                                                        example = "Basic realm=oauth2")),
                                        content =
                                                @Content(
                                                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                        schema =
                                                                @Schema(
                                                                        implementation =
                                                                                OauthEndpointRouter.OAuthError.class)))
                            }))
    public RouterFunction<ServerResponse> oauth2TokenRoutes(OauthTokenHandler handler) {
        return RouterFunctions.route().POST("/oauth2/token", handler::token).build();
    }

    /** 令牌请求表单；除 grant_type 外的必填性由授权类型和客户端认证方式决定。 */
    @Schema(
            description =
                    "authorization_code 需要 code、redirect_uri、code_verifier；password 需要 username、password；refresh_token 需要 refresh_token；未使用 Basic 时需要 client_id、client_secret")
    public record TokenForm(
            @Schema(
                            name = "grant_type",
                            requiredMode = Schema.RequiredMode.REQUIRED,
                            allowableValues = {"authorization_code", "password", "client_credentials", "refresh_token"})
                    String grantType,
            @Schema(name = "client_id", description = "表单客户端标识，不得与 Basic 同时提交") String clientId,
            @Schema(name = "client_secret", description = "表单客户端密钥", accessMode = Schema.AccessMode.WRITE_ONLY)
                    String clientSecret,
            @Schema(description = "以空格分隔的授权范围") String scope,
            @Schema(description = "password 授权的用户名") String username,
            @Schema(description = "password 授权的密码", accessMode = Schema.AccessMode.WRITE_ONLY) String password,
            @Schema(description = "authorization_code 授权的授权码") String code,
            @Schema(name = "redirect_uri", description = "与授权请求一致的回调地址") String redirectUri,
            @Schema(name = "code_verifier", description = "PKCE S256 校验值", pattern = "[A-Za-z0-9._~-]{43,128}")
                    String codeVerifier,
            @Schema(
                            name = "refresh_token",
                            description = "refresh_token 授权的刷新令牌",
                            accessMode = Schema.AccessMode.WRITE_ONLY)
                    String refreshToken) {}

    /** 原生 OAuth 令牌响应字段。 */
    public record TokenResponse(
            @Schema(name = "access_token", description = "访问令牌") String accessToken,
            @Schema(
                            name = "token_type",
                            allowableValues = {"Bearer"})
                    String tokenType,
            @Schema(name = "expires_in", description = "访问令牌有效秒数", type = "integer", format = "int64") Long expiresIn,
            @Schema(name = "refresh_token", description = "刷新令牌；client_credentials 不返回此字段") String refreshToken,
            @Schema(description = "实际授予的授权范围") String scope) {}
}
