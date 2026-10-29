package com.apex.scaffold.fileservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.fileservice.interfaces.handler.FileHandler;
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
 * 文件路由，通过处理方法绑定发现 multipart、JSON 与文件流契约。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Configuration
public class FileRouter {
    /**
     * 注册文件上传、访问、删除与清理重试接口。
     * @param handler 文件处理器
     * @return 文件路由
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/file/upload",
                method = RequestMethod.POST,
                beanClass = FileHandler.class,
                beanMethod = "upload"),
        @RouterOperation(
                path = "/api/v1/file/content",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "content"),
        @RouterOperation(
                path = "/api/v1/file/preview",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "preview"),
        @RouterOperation(
                path = "/api/v1/file/url",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "getUrl"),
        @RouterOperation(
                path = "/api/v1/file/delete",
                method = RequestMethod.DELETE,
                beanClass = FileHandler.class,
                beanMethod = "delete"),
        @RouterOperation(
                path = "/api/v1/file/orphan/{fileId}/retry",
                method = RequestMethod.POST,
                beanClass = FileHandler.class,
                beanMethod = "retryOrphanCleanup"),
        @RouterOperation(
                path = "/api/v1/file/exists",
                method = RequestMethod.GET,
                beanClass = FileHandler.class,
                beanMethod = "exists"),
        @RouterOperation(
                path = "/api/v1/files/generic",
                method = RequestMethod.POST,
                beanClass = FileHandler.class,
                beanMethod = "uploadGenericFile")
    })
    @Bean
    public RouterFunction<ServerResponse> fileRoutes(FileHandler handler) {
        return RouterFunctions.route()
                .path("/api/v1/file", builder -> builder.POST("/upload", handler::upload)
                        .GET("/content", handler::content)
                        .GET("/preview", handler::preview)
                        .GET("/url", handler::getUrl)
                        .DELETE("/delete", handler::delete)
                        .POST("/orphan/{fileId}/retry", handler::retryOrphanCleanup)
                        .GET("/exists", handler::exists))
                .path(
                        "/api/v1/files",
                        builder -> builder.POST(
                                "/generic", contentType(MediaType.MULTIPART_FORM_DATA), handler::uploadGenericFile))
                .build();
    }
}
