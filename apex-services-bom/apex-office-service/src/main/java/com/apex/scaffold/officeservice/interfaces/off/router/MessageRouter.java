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
 * 站内消息与消息模板的函数式路由，OpenAPI 契约由关联的 Handler 方法提供。
 *
 * <p>本类同时承载两组语义不同的接口：
 *
 * <ul>
 *   <li>{@code /api/v1/messages/**}：面向当前登录用户的站内信，收件人由认证上下文推导，
 *       不接受客户端指定用户标识；
 *   <li>{@code /admin-api/v1/message-templates}：面向管理端模板配置，属于写操作入口。
 * </ul>
 *
 * <p>网关侧 {@code office-domain} 路由以 {@code /api/office/**} 等前缀转发到 office 实例，
 * 模板路径为精确匹配而非通配，新增子路径时需同步网关配置，否则虽在本类注册也不可达。
 *
 * <p>站内信实时推送的 WebSocket 握手路由不在此声明；票据与连接注册表为单实例内存状态，
 * 断线后的消息以收件箱列表与未读数接口补偿，不依赖长连接可达性。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class MessageRouter {
    /**
     * 注册当前登录用户的站内消息接口。
     *
     * <p>只暴露读取与已读标记能力，发送为唯一写入口且要求 JSON 请求体；
     * 分页同时提供页号式 {@code /inbox} 与游标式 {@code /inbox/cursor}，
     * 游标版本供前端增量拉取以避免深翻页。
     *
     * <p>Bean 名显式指定为 {@code internalMessageRoutes}，用于和同模块其它消息相关路由
     * 以及模板路由 Bean 区分，避免按类型注入时产生歧义。POST 仅接受 {@code application/json}，
     * 管理端模板维护接口由 {@link #messageTemplateRoutes} 单独注册。
     *
     * @param handler 消息处理器，负责协议解析、校验与调用应用服务
     * @return 站内消息路由函数，挂载于 {@code /api/v1/messages}，含发送、收件箱、发件箱、详情与已读标记
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
     * 注册消息模板配置的管理端接口。
     *
     * <p>查询列表仅返回启用状态模板，供发送侧选择；创建与更新要求 JSON 请求体。
     * 同一路径段在读取与写入时承载不同标识：{@code GET /{templateCode}} 按业务模板编码查询，
     * {@code PUT}、{@code DELETE /{templateId}} 按主键变更，调用方不可混用两种标识。
     *
     * <p>全部端点位于管理端前缀 {@code /admin-api}，与站内信业务接口 {@link #messageRoutes} 分离；
     * 路径为精确匹配，新增子路径需同步网关配置。
     *
     * @param handler 消息模板处理器，负责协议解析、校验与调用模板服务
     * @return 消息模板路由函数，挂载于 {@code /admin-api/v1/message-templates}，含查询、创建、更新与删除
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
