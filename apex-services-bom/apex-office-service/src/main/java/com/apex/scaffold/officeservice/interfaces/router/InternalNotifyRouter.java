package com.apex.scaffold.officeservice.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.accept;

import com.apex.scaffold.officeservice.interfaces.handler.InternalNotifyHandler;
import com.apex.scaffold.officeservice.interfaces.handler.InternalNotifyHandler.InternalNotifySendResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * 统一通知内部契约路由（统一消息门面 / 批次 D1）。
 *
 * <p>注册 {@code POST /internal/v1/notify}，供集群内服务直连统一消息门面按模板投递。
 * 该路由不经网关（网关层对 {@code /internal/**} 强制 403），仅允许集群内服务通过
 * 服务发现直连调用；安全配置对 {@code /internal/v1/**} 放行 Spring Security 认证，
 * 与 {@code OfficeSecurityConfig} 既有内部契约口径一致。
 *
 * @author Apex Scaffold Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Configuration
public class InternalNotifyRouter {

    /**
     * 注册内部通知投递路由.
     *
     * <p>仅注册 {@code POST /internal/v1/notify}，请求体为模板编码与模板参数；该路径不经网关，
     * 仅集群内服务可直连，响应统一为 {@code Result} 包装。
     *
     * @param handler 内部通知处理器，负责请求校验、模板解析与投递
     * @return 内部通知路由函数，挂载于 {@code /internal/v1/notify}
     */
    @Bean
    @RouterOperation(
            path = "/internal/v1/notify",
            method = RequestMethod.POST,
            beanClass = InternalNotifyHandler.class,
            beanMethod = "sendByTemplate",
            operation =
                    @Operation(
                            operationId = "internalSendNotifyByTemplate",
                            summary = "按模板投递通知（内部）",
                            description = "内部契约接口，不经网关。按（租户，模板编码）加载统一通知模板并渲染投递，"
                                    + "channel 缺省取模板默认通道；投递结果按次落库留痕（成功与失败均记录）。"
                                    + "HTTP 恒为 200，业务成败看 data.delivered；模板不存在/已停用/参数缺失"
                                    + "返回 400 业务错误。",
                            responses = {
                                @ApiResponse(
                                        responseCode = "200",
                                        description = "Result 封装的投递结果（含通道、成败、消息 ID、缺失参数）",
                                        content =
                                                @Content(
                                                        mediaType = "application/json",
                                                        schema =
                                                                @Schema(
                                                                        implementation =
                                                                                InternalNotifySendResult.class))),
                                @ApiResponse(
                                        responseCode = "400",
                                        description = "请求体缺失、参数非法或模板不可用",
                                        content =
                                                @Content(
                                                        mediaType = "application/json",
                                                        schema =
                                                                @Schema(
                                                                        implementation =
                                                                                com.apex.scaffold.common.result.Result
                                                                                        .class)))
                            }))
    public RouterFunction<ServerResponse> internalNotifyRoutes(InternalNotifyHandler handler) {
        return RouterFunctions.route()
                .POST("/internal/v1/notify", accept(MediaType.APPLICATION_JSON), handler::sendByTemplate)
                .build();
    }
}
