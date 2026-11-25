package com.apex.scaffold.interfaces.handler;

import com.apex.scaffold.common.annotation.OperationLog;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * OAuth2 服务发现处理器
 *
 * <p>实现 OAuth2 授权服务器元数据发现端点,符合 RFC 8414 规范。
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>暴露 OAuth2 授权服务器元数据端点 {@code /.well-known/oauth-authorization-server}</li>
 *   <li>提供服务器支持的端点、授权类型、作用域等配置信息</li>
 *   <li>支持客户端自动发现和配置</li>
 * </ul>
 *
 * <h2>设计说明</h2>
 * <ul>
 *   <li>{@code issuer} - 授权服务器标识符</li>
 *   <li>{@code authorization_endpoint} - 授权端点</li>
 *   <li>{@code token_endpoint} - 令牌端点</li>
 *   <li>{@code jwks_uri} - JSON Web Key Set 端点</li>
 *   <li>{@code grant_types_supported} - 支持的授权类型(authorization_code, client_credentials, password, refresh_token)</li>
 *   <li>{@code response_types_supported} - 支持的响应类型(code)</li>
 *   <li>{@code scopes_supported} - 支持的作用域列表</li>
 *   <li>{@code code_challenge_methods_supported} - 支持的 PKCE 方法(plain, S256)</li>
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 客户端通过标准端点发现服务器配置
 * GET http://localhost:8080/.well-known/oauth-authorization-server
 *
 * // 返回示例
 * {
 *   "issuer": "http://localhost:8080",
 *   "authorization_endpoint": "http://localhost:8080/oauth2/authorize",
 *   "token_endpoint": "http://localhost:8080/oauth2/token",
 *   "jwks_uri": "http://localhost:8080/oauth2/jwks",
 *   ...
 * }
 * }</pre>
 *
 * <h2>设计说明</h2>
 * <p>符合以下标准:
 * <ul>
 *   <li>RFC 8414 - OAuth 2.0 Authorization Server Metadata</li>
 *   <li>RFC 7636 - Proof Key for Code Exchange (PKCE)</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.1.0
 * @see <a href="https://tools.ietf.org/html/rfc8414">RFC 8414</a>
 */
@SuppressWarnings({"checkstyle:TypeName", "PMD.ClassNamingShouldBeCamelRule"})
@Slf4j
@Component
@Tag(name = "OAuth2服务发现", description = "OAuth2授权服务器元数据发现端点,符合RFC 8414规范")
public class OAuth2DiscoveryHandler {

    /**
     * 服务器端口配置
     *
     * <p>从配置文件读取，默认8080
     */
    private final int serverPort;

    /**
     * 创建 OAuth2 服务发现处理器。
     *
     * @param serverPort 服务器端口，默认 8080
     */
    public OAuth2DiscoveryHandler(@Value("${server.port:8080}") int serverPort) {
        this.serverPort = serverPort;
    }

    /**
     * OAuth2服务器元数据发现
     *
     * <p>返回符合 RFC 8414 标准的 OAuth2 授权服务器元数据，客户端可通过此端点自动发现服务器配置。</p>
     *
     * @param request 服务器请求对象
     * @return 包含 OAuth2 服务器元数据的响应对象
     */
    @Operation(
            operationId = "oauth2Discovery",
            summary = "OAuth2服务器元数据发现",
            description = "返回符合RFC 8414标准的OAuth2授权服务器元数据,客户端可通过此端点自动发现服务器配置")
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "返回OAuth2服务器元数据,包含所有支持的端点、授权类型和作用域",
                content =
                        @Content(
                                mediaType = "application/json",
                                examples =
                                        @ExampleObject(
                                                value =
                                                        """
                {
                  "issuer": "http://localhost:8080",
                  "authorization_endpoint": "http://localhost:8080/oauth2/authorize",
                  "token_endpoint": "http://localhost:8080/oauth2/token",
                  "jwks_uri": "http://localhost:8080/oauth2/jwks",
                  "grant_types_supported": ["authorization_code", "client_credentials", "password", "refresh_token"],
                  "response_types_supported": ["code"],
                  "scopes_supported": ["read", "write", "user:profile", "admin:system"],
                  "code_challenge_methods_supported": ["plain", "S256"]
                }
                """)))
    })
    @OperationLog(
            module = "OAuth2Discovery",
            type = OperationLog.OperationType.SELECT,
            description = "discovery",
            saveRequest = false,
            saveResponse = false)
    /**
     * OAuth2 授权服务器元数据发现端点
     *
     * <p>按 RFC 8414 以当前服务端口拼装 issuer，并据此构造授权、令牌、撤销、内省、JWKS 等端点地址，
     * 以及支持的授权类型、响应类型、PKCE 挑战方法与作用域清单，供客户端自动发现服务器配置。
     *
     * <p>该端点不查询数据库，仅依据配置返回静态元数据；响应直接以 {@code application/json} 输出，
     * 不包装为 {@code Result}。
     *
     * @param request HTTP 请求（内容未被使用，仅保持端点签名一致）
     * @return 以 JSON 返回的 OAuth2 服务器元数据
     */
    public Mono<ServerResponse> discovery(ServerRequest request) {
        String issuer = String.format("http://localhost:%d", serverPort);

        Map<String, Object> metadata = new HashMap<>(16);
        metadata.put("issuer", issuer);
        metadata.put("authorization_endpoint", issuer + "/oauth2/authorize");
        metadata.put("token_endpoint", issuer + "/oauth2/token");
        metadata.put("token_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post"));
        metadata.put("jwks_uri", issuer + "/oauth2/jwks");
        metadata.put("response_types_supported", List.of("code"));
        metadata.put(
                "grant_types_supported",
                List.of("authorization_code", "client_credentials", "password", "refresh_token"));
        metadata.put("revocation_endpoint", issuer + "/oauth2/revoke");
        metadata.put(
                "revocation_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post"));
        metadata.put("introspection_endpoint", issuer + "/oauth2/introspect");
        metadata.put(
                "introspection_endpoint_auth_methods_supported", List.of("client_secret_basic", "client_secret_post"));
        metadata.put("code_challenge_methods_supported", List.of("plain", "S256"));
        metadata.put(
                "scopes_supported",
                List.of(
                        "read",
                        "write",
                        "user:profile",
                        "user:update",
                        "user:avatar",
                        "admin:users",
                        "admin:roles",
                        "admin:system",
                        "api:read",
                        "api:write",
                        "file:upload",
                        "file:download"));

        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).bodyValue(metadata);
    }
}
