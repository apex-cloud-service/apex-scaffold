package com.apex.scaffold.interfaces.router.office;

import static org.springframework.web.reactive.function.server.RequestPredicates.*;

import com.apex.scaffold.interfaces.handler.office.OfficeMessageHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
 * 办公平台消息路由配置
 *
 * <p>定义消息发送相关的 HTTP 路由规则，采用 WebFlux 函数式路由风格。
 * 所有路由以 /api/office/messages 为基础路径。
 *
 * <p>提供的路由：
 * <ul>
 *   <li>POST /api/office/messages - 发送消息</li>
 *   <li>GET /api/office/messages/{messageId} - 查询消息状态</li>
 *   <li>POST /api/office/messages/{messageId}/resend - 重新发送消息</li>
 *   <li>DELETE /api/office/messages/{messageId} - 撤回消息</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Configuration
@RequiredArgsConstructor
public class OfficeMessageRouter {

    private final OfficeMessageHandler messageHandler;

    /**
     * 配置消息相关的路由
     *
     * @return 路由函数
     */
    @RouterOperations({
        @RouterOperation(
                path = "/api/office/messages",
                method = RequestMethod.POST,
                beanClass = OfficeMessageHandler.class,
                beanMethod = "sendMessage",
                operation =
                        @Operation(
                                operationId = "sendMessage",
                                summary = "发送消息",
                                description = "发送消息到指定的办公平台，支持文本、Markdown、卡片等多种消息类型",
                                tags = {"办公平台-消息发送"},
                                requestBody =
                                        @RequestBody(
                                                description = "消息发送请求参数",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                implementation =
                                                                                        com.apex.scaffold.interfaces.dto
                                                                                                .office.message
                                                                                                .SendMessageRequest
                                                                                                .class))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "发送成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.message
                                                                                            .SendMessageResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "400", description = "请求参数错误"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = "/api/office/messages/{messageId}",
                method = RequestMethod.GET,
                beanClass = OfficeMessageHandler.class,
                beanMethod = "getMessageStatus",
                operation =
                        @Operation(
                                operationId = "getMessageStatus",
                                summary = "查询消息状态",
                                description = "根据消息ID查询消息的详细状态信息，包括发送状态、平台消息ID、重试次数等",
                                tags = {"办公平台-消息发送"},
                                parameters = {
                                    @Parameter(
                                            name = "messageId",
                                            description = "消息ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "123456"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "查询成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            implementation =
                                                                                    com.apex.scaffold.interfaces.dto
                                                                                            .office.message
                                                                                            .MessageStatusResponse
                                                                                            .class))),
                                    @ApiResponse(responseCode = "404", description = "消息不存在"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = "/api/office/messages/{messageId}/resend",
                method = RequestMethod.POST,
                beanClass = OfficeMessageHandler.class,
                beanMethod = "resendMessage",
                operation =
                        @Operation(
                                operationId = "resendMessage",
                                summary = "重新发送消息",
                                description = "重新发送失败的消息。只能重新发送状态为 FAILED 的消息",
                                tags = {"办公平台-消息发送"},
                                parameters = {
                                    @Parameter(
                                            name = "messageId",
                                            description = "消息ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "123456"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "重新发送成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = Boolean.class))),
                                    @ApiResponse(responseCode = "400", description = "消息状态不允许重新发送"),
                                    @ApiResponse(responseCode = "404", description = "消息不存在"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                })),
        @RouterOperation(
                path = "/api/office/messages/{messageId}",
                method = RequestMethod.DELETE,
                beanClass = OfficeMessageHandler.class,
                beanMethod = "recallMessage",
                operation =
                        @Operation(
                                operationId = "recallMessage",
                                summary = "撤回消息",
                                description = "撤回发送成功的消息。只能撤回状态为 SUCCESS 且已获得平台消息ID的消息",
                                tags = {"办公平台-消息发送"},
                                parameters = {
                                    @Parameter(
                                            name = "messageId",
                                            description = "消息ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "123456"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "撤回成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema = @Schema(implementation = Boolean.class))),
                                    @ApiResponse(responseCode = "400", description = "消息状态不允许撤回"),
                                    @ApiResponse(responseCode = "404", description = "消息不存在"),
                                    @ApiResponse(responseCode = "500", description = "服务器内部错误")
                                }))
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
