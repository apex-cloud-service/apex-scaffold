package com.apex.scaffold.knowledgeservice.interfaces.router;

import com.apex.scaffold.knowledgeservice.interfaces.handler.KnowledgeDocumentHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 知识文档管理路由（管理端 {@code /admin-api/v1/kb/documents} 与上传入口）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class KnowledgeDocumentRouter {

    /**
     * 注册文档管理路由。
     *
     * @param handler 文档处理器
     * @return 文档路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/kb/spaces/{spaceId}/documents",
                method = RequestMethod.POST,
                beanClass = KnowledgeDocumentHandler.class,
                beanMethod = "upload"),
        @RouterOperation(
                path = "/admin-api/v1/kb/manual-documents",
                method = RequestMethod.POST,
                beanClass = KnowledgeDocumentHandler.class,
                beanMethod = "registerManual"),
        @RouterOperation(
                path = "/admin-api/v1/kb/documents",
                method = RequestMethod.GET,
                beanClass = KnowledgeDocumentHandler.class,
                beanMethod = "page"),
        @RouterOperation(
                path = "/admin-api/v1/kb/documents/{docId}",
                method = RequestMethod.GET,
                beanClass = KnowledgeDocumentHandler.class,
                beanMethod = "detail"),
        @RouterOperation(
                path = "/admin-api/v1/kb/documents/{docId}",
                method = RequestMethod.DELETE,
                beanClass = KnowledgeDocumentHandler.class,
                beanMethod = "delete"),
        @RouterOperation(
                path = "/admin-api/v1/kb/documents/{docId}/reindex",
                method = RequestMethod.POST,
                beanClass = KnowledgeDocumentHandler.class,
                beanMethod = "reindex")
    })
    public RouterFunction<ServerResponse> knowledgeDocumentRoutes(KnowledgeDocumentHandler handler) {
        return RouterFunctions.route()
                .POST("/admin-api/v1/kb/spaces/{spaceId}/documents", handler::upload)
                .POST("/admin-api/v1/kb/manual-documents", handler::registerManual)
                .GET("/admin-api/v1/kb/documents/{docId}", handler::detail)
                .GET("/admin-api/v1/kb/documents", handler::page)
                .DELETE("/admin-api/v1/kb/documents/{docId}", handler::delete)
                .POST("/admin-api/v1/kb/documents/{docId}/reindex", handler::reindex)
                .build();
    }
}
