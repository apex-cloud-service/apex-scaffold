package com.apex.example.config;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.apex.example.handler.ExampleUserHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 示例模块路由配置
 *
 * <p>展示如何定义WebFlux函数式路由
 *
 * @author Apex Team
 * @version 1.0.0
 */
@Configuration
@RequiredArgsConstructor
public class ExampleRouterConfig {

    private final ExampleUserHandler userHandler;

    /**
     * 用户管理路由
     *
     * @return 路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> exampleUserRoutes() {
        return route()
                // POST /api/example/users - 创建用户
                .POST("/api/example/users", userHandler::createUser)

                // GET /api/example/users - 查询用户列表
                .GET("/api/example/users", userHandler::listUsers)

                // GET /api/example/users/{id} - 查询用户详情
                .GET("/api/example/users/{id}", userHandler::getUser)

                // PUT /api/example/users/{id} - 更新用户
                .PUT("/api/example/users/{id}", userHandler::updateUser)

                // DELETE /api/example/users/{id} - 删除用户
                .DELETE("/api/example/users/{id}", userHandler::deleteUser)

                // GET /api/example/reports/premium - 高级运营报表(@FeatureGate商业版示例)
                .GET("/api/example/reports/premium", userHandler::premiumReport)
                .build();
    }
}
