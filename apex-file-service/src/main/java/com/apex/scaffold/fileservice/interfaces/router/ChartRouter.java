package com.apex.scaffold.fileservice.interfaces.router;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import com.apex.scaffold.fileservice.interfaces.handler.ChartHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 图表路由，通过处理方法绑定发现 OpenAPI 契约。
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Configuration
public class ChartRouter {
    /**
     * 注册图表管理与渲染数据查询路由。
     * @param handler 图表处理器
     * @return 要求接受 JSON 响应的图表路由
     */
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/charts",
                method = RequestMethod.GET,
                beanClass = ChartHandler.class,
                beanMethod = "query"),
        @RouterOperation(
                path = "/admin-api/v1/charts",
                method = RequestMethod.POST,
                beanClass = ChartHandler.class,
                beanMethod = "create"),
        @RouterOperation(
                path = "/admin-api/v1/charts/{id}",
                method = RequestMethod.GET,
                beanClass = ChartHandler.class,
                beanMethod = "getById"),
        @RouterOperation(
                path = "/admin-api/v1/charts/{id}/data",
                method = RequestMethod.GET,
                beanClass = ChartHandler.class,
                beanMethod = "getChartData"),
        @RouterOperation(
                path = "/admin-api/v1/charts/{id}",
                method = RequestMethod.PUT,
                beanClass = ChartHandler.class,
                beanMethod = "update"),
        @RouterOperation(
                path = "/admin-api/v1/charts/{id}",
                method = RequestMethod.DELETE,
                beanClass = ChartHandler.class,
                beanMethod = "delete"),
        @RouterOperation(
                path = "/admin-api/v1/charts/{id}/status",
                method = RequestMethod.PUT,
                beanClass = ChartHandler.class,
                beanMethod = "updateStatus")
    })
    @Bean
    public RouterFunction<ServerResponse> chartRoutes(ChartHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/charts", builder -> builder.GET("", accept(APPLICATION_JSON), handler::query)
                        .POST("", accept(APPLICATION_JSON), handler::create)
                        .GET("/{id}", accept(APPLICATION_JSON), handler::getById)
                        .GET("/{id}/data", accept(APPLICATION_JSON), handler::getChartData)
                        .PUT("/{id}", accept(APPLICATION_JSON), handler::update)
                        .DELETE("/{id}", accept(APPLICATION_JSON), handler::delete)
                        .PUT("/{id}/status", accept(APPLICATION_JSON), handler::updateStatus))
                .build();
    }
}
