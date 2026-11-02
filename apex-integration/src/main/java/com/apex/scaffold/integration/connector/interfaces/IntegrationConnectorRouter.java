package com.apex.scaffold.integration.connector.interfaces;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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
 * <p>springdoc 元数据（{@code @RouterOperation}）暂未声明：本模块 POM 无
 * springdoc 传递依赖，待依赖补齐后补充。
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
