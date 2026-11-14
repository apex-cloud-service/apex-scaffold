package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.OpenAppMigrationHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 存量开放应用迁移路由：平台管理员专用，导入为 JSON 数组。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class OpenAppMigrationRouter {

    @Bean
    /**
     * 注册存量开放应用迁移路由。
     *
     * @param handler 迁移处理器
     * @return 路由函数
     */
    public RouterFunction<ServerResponse> openAppMigrationRoutes(OpenAppMigrationHandler handler) {
        return RouterFunctions.route()
                .GET("/admin-api/v1/open-apps/migration/export", handler::export)
                .POST(
                        "/admin-api/v1/open-apps/migration/import",
                        RequestPredicates.contentType(MediaType.APPLICATION_JSON),
                        handler::importApps)
                .build();
    }
}
