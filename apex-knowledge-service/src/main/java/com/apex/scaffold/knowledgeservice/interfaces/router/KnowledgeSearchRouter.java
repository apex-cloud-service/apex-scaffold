package com.apex.scaffold.knowledgeservice.interfaces.router;

import com.apex.scaffold.knowledgeservice.interfaces.handler.KnowledgeSearchHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 知识检索路由（业务端 {@code /api/v1/kb/search} 与空间列表）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class KnowledgeSearchRouter {

    /**
     * 注册检索路由。
     *
     * @param handler 检索处理器
     * @return 检索路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/kb/search",
                method = RequestMethod.GET,
                beanClass = KnowledgeSearchHandler.class,
                beanMethod = "search"),
        @RouterOperation(
                path = "/api/v1/kb/spaces",
                method = RequestMethod.GET,
                beanClass = KnowledgeSearchHandler.class,
                beanMethod = "spaces")
    })
    public RouterFunction<ServerResponse> knowledgeSearchRoutes(KnowledgeSearchHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/kb/search", handler::search)
                .GET("/api/v1/kb/spaces", handler::spaces)
                .build();
    }
}
