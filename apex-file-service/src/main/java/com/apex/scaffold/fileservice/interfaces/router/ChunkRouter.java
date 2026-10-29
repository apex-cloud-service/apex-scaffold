package com.apex.scaffold.fileservice.interfaces.router;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;
import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.fileservice.interfaces.handler.ChunkHandler;
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
 * 分片上传路由，通过处理方法绑定发现 OpenAPI 契约。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Configuration
public class ChunkRouter {
    /**
     * 注册 JSON 初始化与合并、二进制分片上传和清理进度接口。
     * @param handler 分片处理器
     * @return 分片上传路由
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/file/chunk/init",
                method = RequestMethod.POST,
                beanClass = ChunkHandler.class,
                beanMethod = "init"),
        @RouterOperation(
                path = "/api/v1/file/chunk/upload",
                method = RequestMethod.POST,
                beanClass = ChunkHandler.class,
                beanMethod = "uploadChunk"),
        @RouterOperation(
                path = "/api/v1/file/chunk/merge",
                method = RequestMethod.POST,
                beanClass = ChunkHandler.class,
                beanMethod = "merge"),
        @RouterOperation(
                path = "/api/v1/file/chunk/cleanup/{fileId}/retry",
                method = RequestMethod.POST,
                beanClass = ChunkHandler.class,
                beanMethod = "retryCleanup"),
        @RouterOperation(
                path = "/api/v1/file/chunk/status/{fileId}",
                method = RequestMethod.GET,
                beanClass = ChunkHandler.class,
                beanMethod = "status")
    })
    @Bean
    public RouterFunction<ServerResponse> chunkRoutes(ChunkHandler handler) {
        return RouterFunctions.route()
                .path("/api/v1/file/chunk", builder -> builder.POST(
                                "/init", contentType(APPLICATION_JSON).and(accept(APPLICATION_JSON)), handler::init)
                        .POST(
                                "/upload",
                                contentType(MediaType.APPLICATION_OCTET_STREAM).and(accept(APPLICATION_JSON)),
                                handler::uploadChunk)
                        .POST("/merge", contentType(APPLICATION_JSON).and(accept(APPLICATION_JSON)), handler::merge)
                        .POST("/cleanup/{fileId}/retry", accept(APPLICATION_JSON), handler::retryCleanup)
                        .GET("/status/{fileId}", accept(APPLICATION_JSON), handler::status))
                .build();
    }
}
