package com.apex.scaffold.interfaces.config;

import com.apex.scaffold.interfaces.handler.OAuth2AuthorizationHandler;
import com.apex.scaffold.interfaces.handler.OAuth2ClientManagementHandler;
import com.apex.scaffold.interfaces.handler.OAuth2DiscoveryHandler;
import com.apex.scaffold.interfaces.handler.OAuth2JwksHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * OAuth2路由配置
 *
 * <p>配置OAuth2授权服务器的端点路由,所有OAuth2相关请求通过/oauth2前缀访问。
 *
 * <h2>端点列表</h2>
 * <ul>
 *   <li>GET/POST /oauth2/authorize - 授权端点(授权码模式)</li>
 *   <li>POST /oauth2/token - Token端点(所有授权模式)</li>
 *   <li>POST /oauth2/revoke - Token撤销端点</li>
 *   <li>POST /oauth2/introspect - Token自省端点</li>
 * </ul>
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 2.0.0
 */
@Configuration
@RequiredArgsConstructor
public class OAuth2RouterConfig {
    
    private final OAuth2AuthorizationHandler oauth2Handler;
    private final OAuth2ClientManagementHandler oauth2ClientHandler;
    private final OAuth2JwksHandler jwksHandler;
    private final OAuth2DiscoveryHandler discoveryHandler;
    
    @Bean
    public RouterFunction<ServerResponse> oauth2Routes() {
        return route()
                .GET("/.well-known/oauth-authorization-server", discoveryHandler::discovery)
                .GET("/oauth2/authorize", oauth2Handler::authorize)
                .POST("/oauth2/authorize", oauth2Handler::authorize)
                .POST("/oauth2/token", oauth2Handler::token)
                .POST("/oauth2/revoke", oauth2Handler::revoke)
                .POST("/oauth2/introspect", oauth2Handler::introspect)
                .POST("/oauth2/consent", oauth2Handler::consent)
                .POST("/oauth2/consent/revoke", oauth2Handler::revokeConsent)
                .GET("/oauth2/jwks", jwksHandler::jwks)
                .build();
    }
    
    @Bean
    public RouterFunction<ServerResponse> oauth2ClientManagementRoutes() {
        return route()
                .POST("/admin-api/v1/oauth2/clients", oauth2ClientHandler::createClient)
                .PUT("/admin-api/v1/oauth2/clients/{clientId}", oauth2ClientHandler::updateClient)
                .DELETE("/admin-api/v1/oauth2/clients/{clientId}", oauth2ClientHandler::deleteClient)
                .GET("/admin-api/v1/oauth2/clients/{clientId}", oauth2ClientHandler::getClient)
                .GET("/admin-api/v1/oauth2/clients", oauth2ClientHandler::listClients)
                .POST("/admin-api/v1/oauth2/clients/{clientId}/reset-secret", oauth2ClientHandler::resetClientSecret)
                .build();
    }
}
