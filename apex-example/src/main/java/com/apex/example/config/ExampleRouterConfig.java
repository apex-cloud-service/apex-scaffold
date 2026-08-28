package com.apex.example.config;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;

import com.apex.example.handler.ExampleCapabilityHandler;
import com.apex.example.handler.ExampleUserHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 示例模块路由配置
 *
 * <p>展示如何定义WebFlux函数式路由
 *
 * @author Apex Team
 * @version 1.1.4
 */
@Configuration
@RequiredArgsConstructor
public class ExampleRouterConfig {

    private final ExampleUserHandler userHandler;

    private final ExampleCapabilityHandler capabilityHandler;

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

    /**
     * 脚手架核心能力演示路由（/api/example/capabilities）。
     *
     * <p>覆盖：缓存读穿/失效、写接口幂等、操作审计、SSE 推送、
     * 数据脱敏、Excel 导出。</p>
     *
     * @return 路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> exampleCapabilityRoutes() {
        return route()
                // GET /api/example/capabilities/cache/{key} - 多级缓存读穿
                .GET("/api/example/capabilities/cache/{key}", capabilityHandler::getCacheValue)

                // DELETE /api/example/capabilities/cache/{key} - 缓存主动失效
                .DELETE("/api/example/capabilities/cache/{key}", capabilityHandler::evictCache)

                // POST /api/example/capabilities/idempotent - 写接口幂等
                .POST(
                        "/api/example/capabilities/idempotent",
                        accept(MediaType.APPLICATION_JSON),
                        capabilityHandler::idempotentDemo)

                // POST /api/example/capabilities/audit - 操作审计
                .POST(
                        "/api/example/capabilities/audit",
                        accept(MediaType.APPLICATION_JSON),
                        capabilityHandler::auditDemo)

                // GET /api/example/capabilities/stream - SSE 实时推送
                .GET("/api/example/capabilities/stream", capabilityHandler::streamDemo)

                // GET /api/example/capabilities/mask - 敏感数据脱敏
                .GET("/api/example/capabilities/mask", capabilityHandler::maskDemo)

                // GET /api/example/capabilities/export/users - Excel 导出
                .GET("/api/example/capabilities/export/users", capabilityHandler::exportUsers)
                .build();
    }
}
