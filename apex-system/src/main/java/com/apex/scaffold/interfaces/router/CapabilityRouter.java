package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.CapabilityHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * 能力目录路由（方案 M4 / 批次 D4）。
 *
 * <p>{@code /api/v2/capabilities}（平台目录）与
 * {@code /api/v2/capabilities/subscribed}（当前租户已订阅能力）。
 *
 * @author Apex Team
 * @version 1.3.1
 * @since 1.3.1
 */
@Configuration
public class CapabilityRouter {

    /**
     * 注册能力目录路由。
     *
     * @param handler 能力目录 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/api/v2/capabilities",
                method = RequestMethod.GET,
                beanClass = CapabilityHandler.class,
                beanMethod = "listCapabilities",
                operation =
                        @Operation(
                                operationId = "listCapabilities",
                                summary = "能力目录",
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Result 封装的能力列表",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v2/capabilities/subscribed",
                method = RequestMethod.GET,
                beanClass = CapabilityHandler.class,
                beanMethod = "listSubscribed",
                operation =
                        @Operation(
                                operationId = "listSubscribedCapabilities",
                                summary = "当前租户已订阅能力",
                                description = "复用套餐 features 判定：为空表示全量订阅。",
                                responses = {@ApiResponse(responseCode = "200", description = "已订阅能力列表")}))
    })
    public RouterFunction<ServerResponse> capabilityRoutes(CapabilityHandler handler) {
        return RouterFunctions.route()
                .GET(
                        "/api/v2/capabilities",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        handler::listCapabilities)
                .GET(
                        "/api/v2/capabilities/subscribed",
                        RequestPredicates.accept(MediaType.APPLICATION_JSON),
                        handler::listSubscribed)
                .build();
    }
}
