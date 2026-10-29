package com.apex.scaffold.officeservice.interfaces.off.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.officeservice.interfaces.off.handler.MessageHandler;
import com.apex.scaffold.officeservice.interfaces.off.handler.MessageTemplateHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 站内消息与消息模板路由，OpenAPI 契约由关联的 Handler 方法提供。
 *
 * @author Apex Team
 * @since 1.3.1
 */
@Configuration
public class MessageRouter {
    /**
     * 注册当前登录用户的站内消息接口。
     * @param handler 消息处理器
     * @return 消息路由
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/messages",
                method = RequestMethod.POST,
                beanClass = MessageHandler.class,
                beanMethod = "sendMessage"),
        @RouterOperation(
                path = "/api/v1/messages/inbox",
                method = RequestMethod.GET,
                beanClass = MessageHandler.class,
                beanMethod = "getInbox"),
        @RouterOperation(
                path = "/api/v1/messages/sent",
                method = RequestMethod.GET,
                beanClass = MessageHandler.class,
                beanMethod = "getSentMessages"),
        @RouterOperation(
                path = "/api/v1/messages/inbox/cursor",
                method = RequestMethod.GET,
                beanClass = MessageHandler.class,
                beanMethod = "getInboxCursor"),
        @RouterOperation(
                path = "/api/v1/messages/{messageId}",
                method = RequestMethod.GET,
                beanClass = MessageHandler.class,
                beanMethod = "getMessageDetail"),
        @RouterOperation(
                path = "/api/v1/messages/{messageId}/deliveries",
                method = RequestMethod.GET,
                beanClass = MessageHandler.class,
                beanMethod = "getMessageDeliveries"),
        @RouterOperation(
                path = "/api/v1/messages/{messageId}/read",
                method = RequestMethod.PUT,
                beanClass = MessageHandler.class,
                beanMethod = "markAsRead"),
        @RouterOperation(
                path = "/api/v1/messages/read-all",
                method = RequestMethod.PUT,
                beanClass = MessageHandler.class,
                beanMethod = "markAllAsRead"),
        @RouterOperation(
                path = "/api/v1/messages/unread/count",
                method = RequestMethod.GET,
                beanClass = MessageHandler.class,
                beanMethod = "getUnreadCount")
    })
    @Bean(name = "internalMessageRoutes")
    public RouterFunction<ServerResponse> messageRoutes(MessageHandler handler) {
        return RouterFunctions.route()
                .path("/api/v1/messages", routes -> routes.POST(
                                "", contentType(MediaType.APPLICATION_JSON), handler::sendMessage)
                        .GET("/inbox", handler::getInbox)
                        .GET("/inbox/cursor", handler::getInboxCursor)
                        .GET("/sent", handler::getSentMessages)
                        .GET("/{messageId}", handler::getMessageDetail)
                        .GET("/{messageId}/deliveries", handler::getMessageDeliveries)
                        .PUT("/{messageId}/read", handler::markAsRead)
                        .PUT("/read-all", handler::markAllAsRead)
                        .GET("/unread/count", handler::getUnreadCount))
                .build();
    }

    /**
     * 注册消息模板配置接口。
     * @param handler 模板处理器
     * @return 模板路由
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/message-templates/{templateCode}",
                method = RequestMethod.GET,
                beanClass = MessageTemplateHandler.class,
                beanMethod = "getTemplateByCode"),
        @RouterOperation(
                path = "/admin-api/v1/message-templates",
                method = RequestMethod.GET,
                beanClass = MessageTemplateHandler.class,
                beanMethod = "getEnabledTemplates"),
        @RouterOperation(
                path = "/admin-api/v1/message-templates",
                method = RequestMethod.POST,
                beanClass = MessageTemplateHandler.class,
                beanMethod = "createTemplate"),
        @RouterOperation(
                path = "/admin-api/v1/message-templates/{templateId}",
                method = RequestMethod.PUT,
                beanClass = MessageTemplateHandler.class,
                beanMethod = "updateTemplate"),
        @RouterOperation(
                path = "/admin-api/v1/message-templates/{templateId}",
                method = RequestMethod.DELETE,
                beanClass = MessageTemplateHandler.class,
                beanMethod = "deleteTemplate")
    })
    @Bean
    public RouterFunction<ServerResponse> messageTemplateRoutes(MessageTemplateHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/message-templates", routes -> routes.GET("", handler::getEnabledTemplates)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::createTemplate)
                        .GET("/{templateCode}", handler::getTemplateByCode)
                        .PUT("/{templateId}", contentType(MediaType.APPLICATION_JSON), handler::updateTemplate)
                        .DELETE("/{templateId}", handler::deleteTemplate))
                .build();
    }
}
