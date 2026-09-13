package com.apex.scaffold.interfaces.router;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import com.apex.scaffold.interfaces.handler.ChartHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 图表管理路由配置类。
 *
 * <p>基于 Spring WebFlux 函数式编程模型，注册图表资源的 RESTful 路由规则。
 * 路由前缀为 {@code /admin-api/v1/charts}，支持图表的 CRUD 操作、状态变更和图表数据查询。
 *
 * <p>路由规则：
 * <ul>
 *   <li>GET /admin-api/v1/charts - 查询图表列表（支持分页、筛选）</li>
 *   <li>POST /admin-api/v1/charts - 创建新图表</li>
 *   <li>GET /admin-api/v1/charts/{id} - 获取图表元数据</li>
 *   <li>GET /admin-api/v1/charts/{id}/data - 获取图表数据（数据点、时间序列、统计值）</li>
 *   <li>PUT /admin-api/v1/charts/{id} - 更新图表配置</li>
 *   <li>DELETE /admin-api/v1/charts/{id} - 删除图表</li>
 *   <li>PUT /admin-api/v1/charts/{id}/status - 更新图表状态（启用、停用等）</li>
 * </ul>
 *
 * <p>所有路由要求请求头 {@code Accept: application/json}，实际业务处理委托给 {@link ChartHandler}。
 *
 * @see ChartHandler
 * @see org.springframework.web.reactive.function.server.RouterFunction
 */
@Configuration
public class ChartRouter {

    /**
     * 配置图表管理路由函数。
     *
     * <p>注册图表资源的所有 HTTP 端点，统一路由前缀为 {@code /admin-api/v1/charts}。
     * 各路由的业务逻辑由注入的 {@link ChartHandler} 实现。
     *
     * @param handler 图表业务处理器
     * @return 图表路由函数
     */
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
