package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.OpenUserHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 开放用户组织只读路由（/open-api/users）。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class OpenUserRouter {

    @Bean
    public RouterFunction<ServerResponse> openUserRoutes(OpenUserHandler handler) {
        return RouterFunctions.route().GET("/open-api/users", handler::list).build();
    }
}
