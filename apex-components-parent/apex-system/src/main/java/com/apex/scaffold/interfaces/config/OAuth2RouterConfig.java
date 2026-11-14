package com.apex.scaffold.interfaces.config;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.apex.scaffold.interfaces.handler.OAuth2AuthorizationHandler;
import com.apex.scaffold.interfaces.handler.OAuth2ClientManagementHandler;
import com.apex.scaffold.interfaces.handler.OAuth2DiscoveryHandler;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * OAuth2 路由配置
 *
 * <p>使用 WebFlux 函数式路由注册 OAuth2 授权服务器的端点路由，所有 OAuth2 相关请求通过
 * {@code /oauth2} 前缀访问；客户端管理接口通过 {@code /admin-api/v1/oauth2} 前缀访问。</p>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>注册 OAuth2 授权服务器标准端点：授权、Token、撤销、自省、JWKS、发现文档、同意页</li>
 *   <li>注册 OAuth2 客户端管理接口:创建、更新、删除、查询、重置密钥</li>
 *   <li>通过函数式路由 {@link RouterFunction} 聚合多个 Handler，保持路由与业务解耦</li>
 * </ul>
 *
 * <h2>职责范围</h2>
 * <ul>
 *   <li>GET/POST /oauth2/authorize - 授权端点（授权码模式）</li>
 *   <li>POST /oauth2/token - Token 端点（所有授权模式）</li>
 *   <li>POST /oauth2/revoke - Token 撤销端点</li>
 *   <li>POST /oauth2/introspect - Token 自省端点</li>
 *   <li>POST /oauth2/consent - 用户同意端点</li>
 *   <li>POST /oauth2/consent/revoke - 撤销用户同意</li>
 *   <li>GET /oauth2/jwks - JWKS 公钥端点</li>
 *   <li>GET /.well-known/oauth-authorization-server - OAuth2 发现文档</li>
 *   <li>CRUD /admin-api/v1/oauth2/clients - OAuth2 客户端管理</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.0.0
 * @see OAuth2AuthorizationHandler
 * @see OAuth2ClientManagementHandler
 * @see OAuth2DiscoveryHandler
 */
@SuppressWarnings({"checkstyle:TypeName", "PMD.ClassNamingShouldBeCamelRule"})
@Configuration
@RequiredArgsConstructor
public class OAuth2RouterConfig {

    private final OAuth2AuthorizationHandler oauth2Handler;
    private final OAuth2ClientManagementHandler oauth2ClientHandler;
    private final OAuth2DiscoveryHandler discoveryHandler;

    /**
     * 注册〈OAuth2 协议端点〉路由（统一认证收口 A5：弃用期）。
     *
     * <p>OAuth2 授权服务器已收口到认证中心（RS256 + 聚合 JWKS），本组端点进入弃用周期：
     * {@code apex.features.system-oauth2-endpoints=false} 即下线；存量客户端迁移完成后
     * 默认值将翻转为 false 并移除。注意：system 侧 {@code /oauth2/jwks} 空壳端点
     * （无密钥材料、误导第三方）已随收口直接移除，不再保留过渡实现。
     */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "apex.features",
            name = "system-oauth2-endpoints",
            havingValue = "true",
            matchIfMissing = true)
    @RouterOperations({
        @RouterOperation(
                path = "/.well-known/oauth-authorization-server",
                method = RequestMethod.GET,
                beanClass = OAuth2DiscoveryHandler.class,
                beanMethod = "discovery"),
        @RouterOperation(
                path = "/oauth2/authorize",
                method = {RequestMethod.GET, RequestMethod.POST},
                beanClass = OAuth2AuthorizationHandler.class,
                beanMethod = "authorize"),
        @RouterOperation(
                path = "/oauth2/token",
                method = RequestMethod.POST,
                beanClass = OAuth2AuthorizationHandler.class,
                beanMethod = "token"),
        @RouterOperation(
                path = "/oauth2/revoke",
                method = RequestMethod.POST,
                beanClass = OAuth2AuthorizationHandler.class,
                beanMethod = "revoke"),
        @RouterOperation(
                path = "/oauth2/introspect",
                method = RequestMethod.POST,
                beanClass = OAuth2AuthorizationHandler.class,
                beanMethod = "introspect"),
        @RouterOperation(
                path = "/oauth2/consent",
                method = RequestMethod.POST,
                beanClass = OAuth2AuthorizationHandler.class,
                beanMethod = "consent"),
        @RouterOperation(
                path = "/oauth2/consent/revoke",
                method = RequestMethod.POST,
                beanClass = OAuth2AuthorizationHandler.class,
                beanMethod = "revokeConsent"),
    })
    public RouterFunction<ServerResponse> oauth2Routes() {
        return route().GET("/.well-known/oauth-authorization-server", discoveryHandler::discovery)
                .GET("/oauth2/authorize", oauth2Handler::authorize)
                .POST("/oauth2/authorize", oauth2Handler::authorize)
                .POST("/oauth2/token", oauth2Handler::token)
                .POST("/oauth2/revoke", oauth2Handler::revoke)
                .POST("/oauth2/introspect", oauth2Handler::introspect)
                .POST("/oauth2/consent", oauth2Handler::consent)
                .POST("/oauth2/consent/revoke", oauth2Handler::revokeConsent)
                .build();
    }

    /** 注册〈OAuth2 客户端管理〉管理路由，提供客户端的增删改查与密钥重置端点。 */
    @Bean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
            prefix = "apex.features",
            name = "system-oauth2-endpoints",
            havingValue = "true",
            matchIfMissing = true)
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/oauth2/clients",
                method = RequestMethod.POST,
                beanClass = OAuth2ClientManagementHandler.class,
                beanMethod = "createClient"),
        @RouterOperation(
                path = "/admin-api/v1/oauth2/clients/{clientId}",
                method = RequestMethod.PUT,
                beanClass = OAuth2ClientManagementHandler.class,
                beanMethod = "updateClient"),
        @RouterOperation(
                path = "/admin-api/v1/oauth2/clients/{clientId}",
                method = RequestMethod.DELETE,
                beanClass = OAuth2ClientManagementHandler.class,
                beanMethod = "deleteClient"),
        @RouterOperation(
                path = "/admin-api/v1/oauth2/clients/{clientId}",
                method = RequestMethod.GET,
                beanClass = OAuth2ClientManagementHandler.class,
                beanMethod = "getClient"),
        @RouterOperation(
                path = "/admin-api/v1/oauth2/clients",
                method = RequestMethod.GET,
                beanClass = OAuth2ClientManagementHandler.class,
                beanMethod = "listClients"),
        @RouterOperation(
                path = "/admin-api/v1/oauth2/clients/{clientId}/reset-secret",
                method = RequestMethod.POST,
                beanClass = OAuth2ClientManagementHandler.class,
                beanMethod = "resetClientSecret")
    })
    public RouterFunction<ServerResponse> oauth2ClientManagementRoutes() {
        return route().POST("/admin-api/v1/oauth2/clients", oauth2ClientHandler::createClient)
                .PUT("/admin-api/v1/oauth2/clients/{clientId}", oauth2ClientHandler::updateClient)
                .DELETE("/admin-api/v1/oauth2/clients/{clientId}", oauth2ClientHandler::deleteClient)
                .GET("/admin-api/v1/oauth2/clients/{clientId}", oauth2ClientHandler::getClient)
                .GET("/admin-api/v1/oauth2/clients", oauth2ClientHandler::listClients)
                .POST("/admin-api/v1/oauth2/clients/{clientId}/reset-secret", oauth2ClientHandler::resetClientSecret)
                .build();
    }
}
