package com.apex.scaffold.interfaces.handler;

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
 * OAuth2 JWKS端点Handler
 *
 * <p>处理JSON Web Key Set (JWKS)公钥端点请求。
 * JWKS端点用于公开OAuth2授权服务器的公钥信息，
 * 客户端可以通过此端点获取密钥信息以验证JWT Token的签名。
 *
 * <h2>端点信息</h2>
 * <ul>
 *   <li>路径: {@code GET /oauth2/jwks}</li>
 *   <li>响应类型: application/json</li>
 *   <li>无需认证</li>
 * </ul>
 *
 * <h2>响应格式</h2>
 * <pre>{@code
 * {
 *   "keys": [
 *     {
 *       "kty": "oct",
 *       "kid": "apex-oauth2-key-2026",
 *       "alg": "HS256",
 *       "use": "sig"
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h2>密钥类型说明</h2>
 * <ul>
 *   <li>kty: oct - Octet sequence (对称密钥)</li>
 *   <li>kid: 密钥标识符</li>
 *   <li>alg: HS256 - HMAC-SHA256算法</li>
 *   <li>use: sig - 用于签名</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2JwksHandler {
    
    /**
     * 处理JWKS公钥端点请求
     *
     * <p>返回OAuth2授权服务器使用的JWT密钥集信息。
     * 由于当前使用HMAC对称加密，返回的是密钥元数据而非实际密钥值。
     *
     * <h3>注意事项</h3>
     * <ul>
     *   <li>对称密钥(HMAC)不应公开密钥值</li>
     *   <li>仅公开密钥的元数据信息</li>
     *   <li>客户端使用client_secret进行验证</li>
     * </ul>
     *
     * @param request ServerRequest对象
     * @return 包含JWKS信息的响应
     */
    public Mono<ServerResponse> jwks(ServerRequest request) {
        Map<String, Object> jwksResponse = new HashMap<>();
        
        Map<String, Object> key = new HashMap<>();
        key.put("kty", "oct");
        key.put("kid", "apex-oauth2-key-2026");
        key.put("alg", "HS256");
        key.put("use", "sig");
        
        jwksResponse.put("keys", List.of(key));
        
        return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jwksResponse);
    }
}
