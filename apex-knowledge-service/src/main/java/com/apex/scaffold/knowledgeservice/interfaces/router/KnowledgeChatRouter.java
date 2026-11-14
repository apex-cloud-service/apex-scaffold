package com.apex.scaffold.knowledgeservice.interfaces.router;

import com.apex.scaffold.knowledgeservice.interfaces.handler.KnowledgeChatHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 知识问答路由（业务端 {@code /api/v1/kb/chat/**}）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class KnowledgeChatRouter {

    /**
     * 注册问答路由。
     *
     * @param handler 问答处理器
     * @return 问答路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/kb/chat/ask",
                method = RequestMethod.POST,
                beanClass = KnowledgeChatHandler.class,
                beanMethod = "ask"),
        @RouterOperation(
                path = "/api/v1/kb/chat/conversations",
                method = RequestMethod.GET,
                beanClass = KnowledgeChatHandler.class,
                beanMethod = "conversations"),
        @RouterOperation(
                path = "/api/v1/kb/chat/conversations/{conversationId}/messages",
                method = RequestMethod.GET,
                beanClass = KnowledgeChatHandler.class,
                beanMethod = "messages"),
        @RouterOperation(
                path = "/api/v1/kb/chat/conversations/{conversationId}",
                method = RequestMethod.DELETE,
                beanClass = KnowledgeChatHandler.class,
                beanMethod = "deleteConversation")
    })
    public RouterFunction<ServerResponse> knowledgeChatRoutes(KnowledgeChatHandler handler) {
        return RouterFunctions.route()
                .POST("/api/v1/kb/chat/ask", handler::ask)
                .GET("/api/v1/kb/chat/conversations/{conversationId}/messages", handler::messages)
                .GET("/api/v1/kb/chat/conversations", handler::conversations)
                .DELETE("/api/v1/kb/chat/conversations/{conversationId}", handler::deleteConversation)
                .build();
    }
}
