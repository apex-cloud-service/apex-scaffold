package com.apex.scaffold.officeservice.interfaces.router;

import com.apex.scaffold.officeservice.interfaces.handler.OpenMessageHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 开放消息路由：独立于管理端消息路由，仅暴露单条文本投递。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class OpenMessageRouter {

    /**
     * 注册开放消息路由。
     *
     * @param handler 开放消息处理器
     * @return 路由函数
     */
    @Bean
    public RouterFunction<ServerResponse> openMessageRoutes(OpenMessageHandler handler) {
        return RouterFunctions.route()
                .POST("/open-api/messages", RequestPredicates.contentType(MediaType.APPLICATION_JSON), handler::send)
                .build();
    }
}
