package com.apex.scaffold.interfaces.router;

import com.apex.scaffold.interfaces.handler.InternalOpenAppHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 开放应用限流档内部查询路由（方案 D2 按应用分档限流）。
 *
 * <p>{@code GET /internal/v1/open-apps/{clientId}/rate-limit-tier}：集群内服务直连
 * （网关开放接口限流过滤器消费），不经网关；安全链路对
 * {@code /internal/v1/open-apps/**} permitAll。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class InternalOpenAppRouter {

    /**
     * 注册开放应用限流档内部查询路由。
     *
     * @param handler 开放应用内部 Handler
     * @return 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/internal/v1/open-apps/{clientId}/rate-limit-tier",
                method = org.springframework.web.bind.annotation.RequestMethod.GET,
                beanClass = InternalOpenAppHandler.class,
                beanMethod = "getRateLimitTier",
                operation =
                        @Operation(
                                operationId = "internalOpenAppRateLimitTier",
                                summary = "查询开放应用限流档（内部）",
                                description = "集群内服务直连接口，不经网关。返回开放应用注册时声明的限流档编码"
                                        + "（openPlatform.rateLimitTier）；非开放应用、未声明档位或未知客户端"
                                        + "以 default 档返回，供网关 /open-api/** 按应用分档限流消费（D2）。",
                                tags = {"内部契约"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Result 封装的限流档快照",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    public RouterFunction<ServerResponse> internalOpenAppRoutes(InternalOpenAppHandler handler) {
        return RouterFunctions.route()
                .GET("/internal/v1/open-apps/{clientId}/rate-limit-tier", handler::getRateLimitTier)
                .build();
    }
}
