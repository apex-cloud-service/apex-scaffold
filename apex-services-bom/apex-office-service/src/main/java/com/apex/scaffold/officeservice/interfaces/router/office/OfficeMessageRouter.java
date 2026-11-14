package com.apex.scaffold.officeservice.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.officeservice.interfaces.handler.office.OfficeMessageHandler;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 办公平台消息路由；请求和响应契约由关联的 {@link OfficeMessageHandler} 方法声明。
 *
 * <p>接口统一挂载在 {@code /api/office/messages} 下，覆盖消息发送、状态查询、重发和撤回；
 * 发送操作要求 JSON 请求体，消息状态转换和权限规则由处理器及应用服务负责。
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.3.0
 */
@Configuration
@RequiredArgsConstructor
public class OfficeMessageRouter {

    private final OfficeMessageHandler messageHandler;

    /**
     * 注册消息发送、状态查询、重发和撤回路由。
     *
     * <p>发送路由限定 JSON 请求体；消息状态查询、重发和撤回均通过消息标识定位目标消息，
     * 具体的权限和状态校验由处理器及应用服务负责。
     *
     * @return 消息路由函数，挂载于 {@code /api/office/messages}
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/office/messages",
                method = RequestMethod.POST,
                beanClass = OfficeMessageHandler.class,
                beanMethod = "sendMessage"),
        @RouterOperation(
                path = "/api/office/messages/{messageId}",
                method = RequestMethod.GET,
                beanClass = OfficeMessageHandler.class,
                beanMethod = "getMessageStatus"),
        @RouterOperation(
                path = "/api/office/messages/{messageId}/resend",
                method = RequestMethod.POST,
                beanClass = OfficeMessageHandler.class,
                beanMethod = "resendMessage"),
        @RouterOperation(
                path = "/api/office/messages/{messageId}",
                method = RequestMethod.DELETE,
                beanClass = OfficeMessageHandler.class,
                beanMethod = "recallMessage")
    })
    @Bean
    public RouterFunction<ServerResponse> messageRoutes() {
        return RouterFunctions.route()
                .path("/api/office/messages", builder -> builder.POST(
                                "", accept(MediaType.APPLICATION_JSON), messageHandler::sendMessage)
                        .GET("/{messageId}", messageHandler::getMessageStatus)
                        .POST("/{messageId}/resend", messageHandler::resendMessage)
                        .DELETE("/{messageId}", messageHandler::recallMessage))
                .build();
    }
}
