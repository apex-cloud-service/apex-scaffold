package com.apex.scaffold.maskingservice.interfaces;

import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 脱敏函数式路由，通过处理方法绑定发现 OpenAPI 契约。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class MaskingRouter {
    /**
     * 注册批量接口和只读规则接口。
     * @param handler 处理器
     * @return 路由
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/masking/batch",
                method = RequestMethod.POST,
                beanClass = MaskingHandler.class,
                beanMethod = "batch"),
        @RouterOperation(
                path = "/api/v1/masking/rules",
                method = RequestMethod.GET,
                beanClass = MaskingHandler.class,
                beanMethod = "rules")
    })
    public RouterFunction<ServerResponse> maskingRoutes(MaskingHandler handler) {
        return RouterFunctions.route()
                .POST("/api/v1/masking/batch", handler::batch)
                .GET("/api/v1/masking/rules", handler::rules)
                .build();
    }

    /**
     * 声明 bearer 鉴权文档。
     * @return OpenAPI
     */
    @Bean
    public io.swagger.v3.oas.models.OpenAPI maskingOpenApi() {
        return new io.swagger.v3.oas.models.OpenAPI()
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes(
                                "bearerAuth",
                                new io.swagger.v3.oas.models.security.SecurityScheme()
                                        .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")));
    }
}
