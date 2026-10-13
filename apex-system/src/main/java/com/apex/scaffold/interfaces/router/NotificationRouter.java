package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.NotificationSseHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * 通知 SSE 路由配置。
 *
 * <p>由原 {@code ApiRouter} 按子域拆分而来。</p>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
public class NotificationRouter {

    /**
     * 站内通知 SSE 路由（/api/v1/notifications/stream）。
     *
     * <p>提供实时通知推送端点（C6），基于认证 Principal 定向推送。</p>
     *
     * @param handler SSE 通知处理器
     * @return SSE 推送路由函数
     * @since 2.2.0
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/notifications/stream",
                method = RequestMethod.GET,
                beanClass = NotificationSseHandler.class,
                beanMethod = "stream",
                operation =
                        @Operation(
                                operationId = "streamNotifications",
                                summary = "订阅实时通知",
                                description = "建立 SSE 连接，接收站内实时通知推送",
                                tags = {"通知中心"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "SSE连接建立成功，开始推送通知流",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.TEXT_EVENT_STREAM_VALUE,
                                                            schema = @Schema(implementation = String.class)))
                                }))
    })
    @Bean
    public RouterFunction<ServerResponse> notificationSseRoutes(NotificationSseHandler handler) {
        return RouterFunctions.route()
                .GET("/api/v1/notifications/stream", handler::stream)
                .build();
    }
}
