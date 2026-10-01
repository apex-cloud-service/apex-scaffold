package com.apex.scaffold.interfaces.handler;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * OAuth2 JWKS 处理器（函数式端点）。
 *
 * <p>提供 OAuth2 授权服务器的 JSON Web Key Set（JWKS）公开端点，
 * 客户端可通过该端点获取 JWT 令牌签名验证所需的公钥元数据，
 * 无需在客户端预置密钥即可完成令牌验签。</p>
 *
 * <h2>主要功能</h2>
 * <ul>
 *   <li>暴露 JWKS 端点，返回签名密钥集（kty/kid/alg/use）</li>
 *   <li>支持 HS256 对称签名密钥的元数据发布</li>
 *   <li>配合 OAuth2 服务发现端点 {@code jwks_uri} 使用</li>
 * </ul>
 *
 * <h2>接口清单</h2>
 * <ul>
 *   <li>GET /oauth2/jwks - 获取 JWT 公钥集（JWKS）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see OAuth2DiscoveryHandler
 */
@SuppressWarnings("checkstyle:TypeName")
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "OAuth2 JWKS", description = "OAuth2 JSON Web Key Set公钥端点,用于JWT令牌签名验证")
public class OAuth2JwksHandler {

    /**
     * 获取 JWT 公钥集（JWKS）
     *
     * <p>返回 OAuth2 授权服务器的 JWT 密钥集元数据，客户端可用于验证 JWT 令牌签名。</p>
     *
     * @param request 服务器请求对象
     * @return 包含 JWT 公钥集（JWKS）的响应对象
     */
    @Operation(
            operationId = "oauth2Jwks",
            summary = "获取JWT公钥集(JWKS)",
            description = "返回OAuth2授权服务器的JWT密钥集元数据,客户端可用于验证JWT令牌签名")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "返回JWKS密钥集信息",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                {
                  "keys": [
                    {
                      "kty": "oct",
                      "kid": "apex-oauth2-key-2026",
                      "alg": "HS256",
                      "use": "sig"
                    }
                  ]
                }
                """)))
    })
    public Mono<ServerResponse> jwks(ServerRequest request) {
        Map<String, Object> jwksResponse = new HashMap<>(2);

        Map<String, Object> key = new HashMap<>(8);
        key.put("kty", "oct");
        key.put("kid", "apex-oauth2-key-2026");
        key.put("alg", "HS256");
        key.put("use", "sig");

        jwksResponse.put("keys", List.of(key));

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(jwksResponse);
    }
}
