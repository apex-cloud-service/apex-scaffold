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
 * 图表管理与渲染数据查询的函数式路由，OpenAPI 契约通过处理方法绑定发现。
 *
 * <p>路径前缀 {@code /admin-api/v1/charts} 由 file 服务提供，非 system 服务；
 * 网关按 {@code file-domain} 规则转发，路径调整时需同步网关配置。
 *
 * <p>所有端点均声明 {@code Accept: application/json} 谓词，客户端未声明可接受 JSON 时
 * 不会匹配到本路由，而是继续落入后续路由或返回 404/406，属于有意约束。
 * {@code /{id}/data} 用于读取图表渲染数据，与 {@code /{id}} 的元数据查询分离。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.0.0
 */
@Configuration
public class ChartRouter {
    /**
     * 注册图表管理与渲染数据查询路由。
     *
     * <p>包含列表/详情查询、创建、更新、删除、状态切换以及渲染数据读取；
     * 路径参数 {@code id} 为图表主键，具体校验与权限由 {@link ChartHandler} 完成。
     *
     * @param handler 图表处理器
     * @return 图表路由函数，全部端点要求接受 JSON 响应
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
