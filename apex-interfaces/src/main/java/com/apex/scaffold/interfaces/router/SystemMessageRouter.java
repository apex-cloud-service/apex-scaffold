package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.SystemMessageHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 站内信路由配置。
 *
 * <p>注册站内信读侧闭环接口：收件箱分页查询、未读数统计、单条已读与全部已读。
 * 所有端点均要求 Bearer 认证，业务层以当前登录用户为过滤条件。</p>
 *
 * @author Apex Team
 * @since 2.2.0
 * @see SystemMessageHandler
 */
@Configuration
public class SystemMessageRouter {

    /**
     * 站内信读写路由。
     *
     * <ul>
     *   <li>GET  /api/v1/messages/inbox - 分页查询收件箱</li>
     *   <li>GET  /api/v1/messages/unread-count - 查询未读数</li>
     *   <li>POST /api/v1/messages/{id}/read - 标记单条已读</li>
     *   <li>POST /api/v1/messages/read-all - 全部标记已读</li>
     * </ul>
     *
     * @param handler 站内信处理器
     * @return 站内信路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/messages/inbox",
                method = RequestMethod.GET,
                beanClass = SystemMessageHandler.class,
                beanMethod = "inbox",
                operation =
                        @Operation(
                                operationId = "listInboxMessages",
                                summary = "分页查询站内信收件箱",
                                description = "按创建时间倒序分页返回当前登录用户的站内信，可通过 unreadOnly 过滤未读消息",
                                tags = {"站内信"},
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/api/v1/messages/unread-count",
                method = RequestMethod.GET,
                beanClass = SystemMessageHandler.class,
                beanMethod = "unreadCount",
                operation =
                        @Operation(
                                operationId = "countUnreadMessages",
                                summary = "查询未读站内信数量",
                                description = "统计当前登录用户尚未标记已读的站内信数量，可用于角标展示",
                                tags = {"站内信"},
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/api/v1/messages/{id}/read",
                method = RequestMethod.POST,
                beanClass = SystemMessageHandler.class,
                beanMethod = "markRead",
                operation =
                        @Operation(
                                operationId = "markMessageRead",
                                summary = "标记站内信已读",
                                description = "将当前登录用户的指定站内信标记为已读，重复调用幂等返回成功",
                                tags = {"站内信"},
                                security = @SecurityRequirement(name = "BearerAuth"))),
        @RouterOperation(
                path = "/api/v1/messages/read-all",
                method = RequestMethod.POST,
                beanClass = SystemMessageHandler.class,
                beanMethod = "markAllRead",
                operation =
                        @Operation(
                                operationId = "markAllMessagesRead",
                                summary = "全部标记已读",
                                description = "将当前登录用户的所有未读站内信一次性标记为已读，返回本次实际标记的数量",
                                tags = {"站内信"},
                                security = @SecurityRequirement(name = "BearerAuth")))
    })
    @Bean
    public RouterFunction<ServerResponse> systemMessageRoutes(SystemMessageHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/messages/inbox", handler::inbox)
                .GET("/api/v1/messages/unread-count", handler::unreadCount)
                .POST("/api/v1/messages/{id}/read", handler::markRead)
                .POST("/api/v1/messages/read-all", handler::markAllRead)
                .build();
    }
}
