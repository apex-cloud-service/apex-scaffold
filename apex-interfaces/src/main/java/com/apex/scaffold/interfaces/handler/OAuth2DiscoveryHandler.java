package com.apex.scaffold.interfaces.handler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * OAuth2服务发现端点Handler
 *
 * <p>处理OAuth2授权服务器元数据发现请求，符合RFC 8414规范。
 * 此端点公开OAuth2授权服务器的配置信息，包括支持的端点、授权类型、Scope等。
 *
 * <h2>端点信息</h2>
 * <ul>
 *   <li>路径: {@code GET /.well-known/oauth-authorization-server}</li>
 *   <li>标准: RFC 8414 - OAuth 2.0 Authorization Server Metadata</li>
 *   <li>响应类型: application/json</li>
 *   <li>无需认证</li>
 * </ul>
 *
 * <h2>返回的元数据包含</h2>
 * <ul>
 *   <li>issuer - 授权服务器标识符</li>
 *   <li>authorization_endpoint - 授权端点URL</li>
 *   <li>token_endpoint - Token端点URL</li>
 *   <li>jwks_uri - JWKS公钥端点URL</li>
 *   <li>grant_types_supported - 支持的授权类型列表</li>
 *   <li>response_types_supported - 支持的响应类型</li>
 *   <li>scopes_supported - 支持的Scope列表</li>
 *   <li>code_challenge_methods_supported - 支持的PKCE方法</li>
 * </ul>
 *
 * <h2>使用场景</h2>
 * <ul>
 *   <li>客户端自动发现授权服务器配置</li>
 *   <li>动态客户端注册</li>
 *   <li>OAuth2服务器能力声明</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 * @see <a href="https://tools.ietf.org/html/rfc8414">RFC 8414</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2DiscoveryHandler {

    /**
     * 服务器端口配置
     *
     * <p>从配置文件读取，默认8080
     */
    @Value("${server.port:8080}")
    private int serverPort;

    /**
     * 处理OAuth2服务发现请求
     *
     * <p>返回符合RFC 8414标准的OAuth2授权服务器元数据。
     * 客户端可以通过此元数据自动配置OAuth2连接参数。
     *
     * <h3>响应示例</h3>
     * <pre>{@code
     * {
     *   "issuer": "http://localhost:8080",
     *   "authorization_endpoint": "http://localhost:8080/oauth2/authorize",
     *   "token_endpoint": "http://localhost:8080/oauth2/token",
     *   "jwks_uri": "http://localhost:8080/oauth2/jwks",
     *   "grant_types_supported": [
     *     "authorization_code",
     *     "client_credentials",
     *     "password",
     *     "refresh_token"
     *   ],
     *   "response_types_supported": ["code"],
     *   "scopes_supported": ["read", "write", ...],
     *   "code_challenge_methods_supported": ["plain", "S256"]
     * }
     * }</pre>
     *
     * @param request ServerRequest对象
     * @return 包含OAuth2服务器元数据的响应
     */
    public Mono<ServerResponse> discovery(ServerRequest request) {
        String issuer = String.format("http://localhost:%d", serverPort);

        Map<String, Object> metadata = new HashMap<>();
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
