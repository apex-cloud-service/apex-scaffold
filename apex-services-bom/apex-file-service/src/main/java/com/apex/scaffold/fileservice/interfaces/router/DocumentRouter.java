package com.apex.scaffold.fileservice.interfaces.router;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import com.apex.scaffold.fileservice.interfaces.handler.DocumentHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 文档管理路由，OpenAPI 契约通过处理方法绑定读取。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Configuration
public class DocumentRouter {
    /**
     * 注册文档增删改查、状态管理及附件地址查询路由。
     * @param handler 文档处理器
     * @return 要求接受 JSON 响应的文档路由
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/documents",
                method = RequestMethod.GET,
                beanClass = DocumentHandler.class,
                beanMethod = "query"),
        @RouterOperation(
                path = "/admin-api/v1/documents",
                method = RequestMethod.POST,
                beanClass = DocumentHandler.class,
                beanMethod = "create"),
        @RouterOperation(
                path = "/admin-api/v1/documents/{id}",
                method = RequestMethod.GET,
                beanClass = DocumentHandler.class,
                beanMethod = "getById"),
        @RouterOperation(
                path = "/admin-api/v1/documents/{id}",
                method = RequestMethod.PUT,
                beanClass = DocumentHandler.class,
                beanMethod = "update"),
        @RouterOperation(
                path = "/admin-api/v1/documents/{id}",
                method = RequestMethod.DELETE,
                beanClass = DocumentHandler.class,
                beanMethod = "delete"),
        @RouterOperation(
                path = "/admin-api/v1/documents/{id}/status",
                method = RequestMethod.PUT,
                beanClass = DocumentHandler.class,
                beanMethod = "updateStatus"),
        @RouterOperation(
                path = "/admin-api/v1/documents/{id}/download",
                method = RequestMethod.GET,
                beanClass = DocumentHandler.class,
                beanMethod = "download")
    })
    @Bean
    public RouterFunction<ServerResponse> documentRoutes(DocumentHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/documents", builder -> builder.GET("", accept(APPLICATION_JSON), handler::query)
                        .POST("", accept(APPLICATION_JSON), handler::create)
                        .GET("/{id}", accept(APPLICATION_JSON), handler::getById)
                        .PUT("/{id}", accept(APPLICATION_JSON), handler::update)
                        .DELETE("/{id}", accept(APPLICATION_JSON), handler::delete)
                        .PUT("/{id}/status", accept(APPLICATION_JSON), handler::updateStatus)
                        .GET("/{id}/download", accept(APPLICATION_JSON), handler::download))
                .build();
    }
}
