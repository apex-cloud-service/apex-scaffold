package com.apex.scaffold.officeservice.interfaces.router;

import com.apex.scaffold.officeservice.interfaces.handler.OpenMessageHandler;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 开放消息路由：独立于管理端消息路由，仅暴露单条文本投递。
 *
 * <p>{@code @RouterOperation} 只声明路由绑定；操作说明、请求体与响应模型统一由
 * {@link OpenMessageHandler} 方法上的 SpringDoc 注解决定。
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
    @RouterOperations({
        @RouterOperation(
                path = "/open-api/messages",
                method = RequestMethod.POST,
                beanClass = OpenMessageHandler.class,
                beanMethod = "send")
    })
    public RouterFunction<ServerResponse> openMessageRoutes(OpenMessageHandler handler) {
        return RouterFunctions.route()
                .POST("/open-api/messages", RequestPredicates.contentType(MediaType.APPLICATION_JSON), handler::send)
                .build();
    }
}
