package com.apex.scaffold.knowledgeservice.interfaces.router;

import com.apex.scaffold.knowledgeservice.interfaces.handler.KnowledgeSpaceHandler;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 知识空间管理路由（管理端 {@code /admin-api/v1/kb/spaces}）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class KnowledgeSpaceRouter {

    /**
     * 注册空间管理路由。
     *
     * @param handler 空间处理器
     * @return 空间路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/kb/spaces",
                method = RequestMethod.GET,
                beanClass = KnowledgeSpaceHandler.class,
                beanMethod = "page"),
        @RouterOperation(
                path = "/admin-api/v1/kb/spaces/{spaceId}",
                method = RequestMethod.GET,
                beanClass = KnowledgeSpaceHandler.class,
                beanMethod = "detail"),
        @RouterOperation(
                path = "/admin-api/v1/kb/spaces",
                method = RequestMethod.POST,
                beanClass = KnowledgeSpaceHandler.class,
                beanMethod = "create"),
        @RouterOperation(
                path = "/admin-api/v1/kb/spaces/{spaceId}",
                method = RequestMethod.PUT,
                beanClass = KnowledgeSpaceHandler.class,
                beanMethod = "update"),
        @RouterOperation(
                path = "/admin-api/v1/kb/spaces/{spaceId}",
                method = RequestMethod.DELETE,
                beanClass = KnowledgeSpaceHandler.class,
                beanMethod = "delete")
    })
    public RouterFunction<ServerResponse> knowledgeSpaceRoutes(KnowledgeSpaceHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/kb/spaces/{spaceId}", handler::detail)
                .GET("/admin-api/v1/kb/spaces", handler::page)
                .POST("/admin-api/v1/kb/spaces", handler::create)
                .PUT("/admin-api/v1/kb/spaces/{spaceId}", handler::update)
                .DELETE("/admin-api/v1/kb/spaces/{spaceId}", handler::delete)
                .build();
    }

    /**
     * 注册 bearer 鉴权方案（OpenAPI 文档描述）。
     *
     * @return 包含安全方案的 OpenAPI 定义
     */
    @Bean
    public OpenAPI knowledgeOpenApi() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
