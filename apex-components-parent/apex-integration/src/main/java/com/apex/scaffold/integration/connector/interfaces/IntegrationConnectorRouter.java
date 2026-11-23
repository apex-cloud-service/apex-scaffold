package com.apex.scaffold.integration.connector.interfaces;

import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 集成域路由（方案 M5 / 批次 E2，WebFlux 函数式路由）。
 *
 * <p>管理接口（受权限切面保护）与入站 webhook（免认证，HMAC 验签）：
 * <ul>
 *   <li>GET/POST {@code /integration/v1/connectors}、GET/PUT/DELETE
 *       {@code /integration/v1/connectors/{id}}</li>
 *   <li>GET/POST {@code /integration/v1/credentials}、GET/DELETE
 *       {@code /integration/v1/credentials/{id}}、PUT
 *       {@code /integration/v1/credentials/{id}/rotate}</li>
 *   <li>POST {@code /integration/v1/webhook/{connector}}</li>
 * </ul>
 *
 * <p>{@code @RouterOperation} 只声明路由绑定；操作说明、参数、请求体与响应模型统一由
 * {@link ConnectorConfigHandler}、{@link CredentialHandler}、{@link WebhookInboundHandler}
 * 方法上的 SpringDoc 注解决定。本模块 POM 已声明 springdoc-webflux-api（optional），
 * 无该依赖时不注册文档元数据，路由行为不变。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration(proxyBeanMethods = false)
public class IntegrationConnectorRouter {

    /**
     * 注册集成域路由。
     *
     * @param connectorHandler 连接器配置 Handler
     * @param credentialHandler 凭据 Handler
     * @param webhookHandler 入站 webhook Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/integration/v1/connectors",
                method = RequestMethod.GET,
                beanClass = ConnectorConfigHandler.class,
                beanMethod = "page"),
        @RouterOperation(
                path = "/integration/v1/connectors",
                method = RequestMethod.POST,
                beanClass = ConnectorConfigHandler.class,
                beanMethod = "create"),
        @RouterOperation(
                path = "/integration/v1/connectors/{id}",
                method = RequestMethod.GET,
                beanClass = ConnectorConfigHandler.class,
                beanMethod = "get"),
        @RouterOperation(
                path = "/integration/v1/connectors/{id}",
                method = RequestMethod.PUT,
                beanClass = ConnectorConfigHandler.class,
                beanMethod = "update"),
        @RouterOperation(
                path = "/integration/v1/connectors/{id}",
                method = RequestMethod.DELETE,
                beanClass = ConnectorConfigHandler.class,
                beanMethod = "delete"),
        @RouterOperation(
                path = "/integration/v1/credentials",
                method = RequestMethod.GET,
                beanClass = CredentialHandler.class,
                beanMethod = "list"),
        @RouterOperation(
                path = "/integration/v1/credentials",
                method = RequestMethod.POST,
                beanClass = CredentialHandler.class,
                beanMethod = "register"),
        @RouterOperation(
                path = "/integration/v1/credentials/{id}",
                method = RequestMethod.GET,
                beanClass = CredentialHandler.class,
                beanMethod = "get"),
        @RouterOperation(
                path = "/integration/v1/credentials/{id}/rotate",
                method = RequestMethod.PUT,
                beanClass = CredentialHandler.class,
                beanMethod = "rotate"),
        @RouterOperation(
                path = "/integration/v1/credentials/{id}",
                method = RequestMethod.DELETE,
                beanClass = CredentialHandler.class,
                beanMethod = "delete"),
        @RouterOperation(
                path = "/integration/v1/webhook/{connector}",
                method = RequestMethod.POST,
                beanClass = WebhookInboundHandler.class,
                beanMethod = "accept")
    })
    public RouterFunction<ServerResponse> integrationConnectorRoutes(
            ConnectorConfigHandler connectorHandler,
            CredentialHandler credentialHandler,
            WebhookInboundHandler webhookHandler) {
        return RouterFunctions.route()
                .GET(
                        "/integration/v1/connectors",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        connectorHandler::page)
                .POST(
                        "/integration/v1/connectors",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        connectorHandler::create)
                .GET("/integration/v1/connectors/{id}", connectorHandler::get)
                .PUT(
                        "/integration/v1/connectors/{id}",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        connectorHandler::update)
                .DELETE("/integration/v1/connectors/{id}", connectorHandler::delete)
                .GET(
                        "/integration/v1/credentials",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        credentialHandler::list)
                .POST(
                        "/integration/v1/credentials",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        credentialHandler::register)
                .GET("/integration/v1/credentials/{id}", credentialHandler::get)
                .PUT(
                        "/integration/v1/credentials/{id}/rotate",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        credentialHandler::rotate)
                .DELETE("/integration/v1/credentials/{id}", credentialHandler::delete)
                .POST(
                        "/integration/v1/webhook/{connector}",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        webhookHandler::accept)
                .build();
    }
}
