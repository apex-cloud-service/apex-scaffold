package com.apex.scaffold.officeservice.interfaces.router;

import com.apex.scaffold.officeservice.interfaces.handler.OfficeInternalHandler;
import io.swagger.v3.oas.annotations.Operation;
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
 * office 域内部端点路由（方案 D4 能力计量 / D5 只读统计 / S4-a 统一待办）。
 *
 * <p>{@code GET /internal/v1/office/messages/stats} 与 {@code GET /internal/v1/office/todo}：
 * 集群内服务直连，不经网关；安全链路对 {@code /internal/v1/**} permitAll
 * （见 {@code OfficeSecurityConfig}），待办端点在 Handler 内校验身份中继认证上下文。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class OfficeInternalRouter {

    /** 消息量统计路径（契约固定勿改） */
    public static final String MESSAGE_STATS_PATH = "/internal/v1/office/messages/stats";

    /** 统一待办路径。 */
    public static final String TODO_PATH = "/internal/v1/office/todo";

    /**
     * 注册 office 内部统计与待办路由。
     *
     * <p>注册 {@code /internal/v1/office/messages/stats} 与 {@code /internal/v1/office/todo} 两个
     * 只读端点，路径取自 {@link #MESSAGE_STATS_PATH} 与 {@link #TODO_PATH} 常量（契约固定勿改）；
     * 集群内直连、不经网关，待办端点的身份校验在处理器内完成。
     *
     * @param handler office 内部 Handler，负责统计查询与待办聚合
     * @return 内部统计与待办路由函数，挂载于 {@code /internal/v1/office}
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = MESSAGE_STATS_PATH,
                method = RequestMethod.GET,
                beanClass = OfficeInternalHandler.class,
                beanMethod = "messageStats",
                operation =
                        @Operation(
                                operationId = "internalOfficeMessageStats",
                                summary = "租户消息量统计（内部）",
                                description = "集群内服务直连接口，不经网关。返回租户站内信总量，供 system 能力计量"
                                        + "（消息量维度，D4）与聚合门面消费（D5）；租户标识取 tenantId 参数，缺失时回退可信租户上下文。",
                                tags = {"内部契约"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "Result 封装的消息量统计",
                                            content =
                                                    @io.swagger.v3.oas.annotations.media.Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = TODO_PATH,
                method = RequestMethod.GET,
                beanClass = OfficeInternalHandler.class,
                beanMethod = "todo")
    })
    public RouterFunction<ServerResponse> officeInternalRoutes(OfficeInternalHandler handler) {
        return RouterFunctions.route()
                .GET(MESSAGE_STATS_PATH, handler::messageStats)
                .GET(TODO_PATH, handler::todo)
                .build();
    }
}
