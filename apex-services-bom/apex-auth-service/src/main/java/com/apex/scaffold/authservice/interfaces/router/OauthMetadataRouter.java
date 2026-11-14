package com.apex.scaffold.authservice.interfaces.router;

import com.apex.scaffold.auth.application.service.OAuth2MetadataService;
import com.apex.scaffold.auth.config.OAuth2Properties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Map;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * OAuth2元数据发现路由配置。
 *
 * <p>定义OpenID Connect Discovery（{@code GET /.well-known/openid-configuration}）与
 * JWKS公钥集（{@code GET /.well-known/jwks.json}）两类公开路由绑定，供客户端自动发现
 * 认证中心的端点地址与令牌签名公钥。两者均为公开只读元数据接口，不涉及凭据校验，
 * 因此不接入审计日志。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see OAuth2MetadataService
 */
@Configuration
@EnableConfigurationProperties(OAuth2Properties.class)
public class OauthMetadataRouter {
    /**
     * 注册OAuth2服务发现与JWKS公钥集路由。
     *
     * @param metadata OAuth2元数据服务
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/.well-known/openid-configuration",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "oauth2Discovery",
                                summary = "OAuth2/OIDC服务发现",
                                description = "返回认证中心的OAuth2/OpenID Connect元数据，" + "包含授权端点、令牌端点、JWKS地址等信息，供客户端自动发现。",
                                tags = {"OAuth2"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "返回服务发现元数据",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = Map.class)))
                                })),
        @RouterOperation(
                path = "/.well-known/jwks.json",
                method = RequestMethod.GET,
                operation =
                        @Operation(
                                operationId = "oauth2Jwks",
                                summary = "OAuth2公钥集",
                                description = "返回用于校验令牌签名的JWKS公钥集（JSON Web Key Set），" + "供资源服务器验证访问令牌签名。",
                                tags = {"OAuth2"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "返回JWKS公钥集",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = Map.class)))
                                }))
    })
    public RouterFunction<ServerResponse> oauth2MetadataRoutes(OAuth2MetadataService metadata) {
        return RouterFunctions.route()
                .GET("/.well-known/openid-configuration", request -> ServerResponse.ok()
                        .bodyValue(metadata.discovery()))
                // 统一认证收口（A2）：JWKS 聚合轮换密钥与过渡静态密钥，改为响应式输出
                .GET("/.well-known/jwks.json", request -> ServerResponse.ok().body(metadata.jwks(), Map.class))
                .build();
    }
}
