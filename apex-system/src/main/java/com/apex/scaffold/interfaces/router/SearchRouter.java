package com.apex.scaffold.interfaces.router;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * 统一搜索路由配置（/admin-api/v1/search）。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class SearchRouter {

    /**
     * 统一搜索路由（/admin-api/v1/search）。
     *
     * <p>提供全文搜索和索引管理功能。</p>
     *
     * @param handler 统一搜索处理器
     * @return 搜索路由函数
     * @since 1.3.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/search",
                method = RequestMethod.GET,
                beanClass = com.apex.scaffold.interfaces.handler.UnifiedSearchHandler.class,
                beanMethod = "search",
                operation =
                        @Operation(
                                summary = "统一全文搜索",
                                description = "按关键词执行统一全文搜索",
                                tags = {"统一搜索"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/search/rebuild",
                method = RequestMethod.POST,
                beanClass = com.apex.scaffold.interfaces.handler.UnifiedSearchHandler.class,
                beanMethod = "rebuildIndex",
                operation =
                        @Operation(
                                summary = "重建搜索索引",
                                description = "触发统一搜索索引重建",
                                tags = {"统一搜索"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "操作成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> unifiedSearchRoutes(
            com.apex.scaffold.interfaces.handler.UnifiedSearchHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/search", handler::search)
                .POST("/admin-api/v1/search/rebuild", handler::rebuildIndex)
                .build();
    }
}
