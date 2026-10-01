package com.apex.scaffold.interfaces.router;

import static org.springframework.web.reactive.function.server.RequestPredicates.contentType;

import com.apex.scaffold.interfaces.handler.MessageHandler;
import com.apex.scaffold.interfaces.handler.MessageTemplateHandler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
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
 * 消息管理路由配置。
 *
 * <p>基于 WebFlux 函数式编程模型的路由配置，
 * 将 HTTP 请求路由到对应的 Handler 方法。
 *
 * <h2>路由规则</h2>
 * <ul>
 *   <li>POST /api/v1/messages → MessageHandler.sendMessage</li>
 *   <li>GET /api/v1/messages/inbox → MessageHandler.getInbox</li>
 *   <li>GET /api/v1/messages/sent → MessageHandler.getSentMessages</li>
 *   <li>GET /api/v1/messages/{messageId} → MessageHandler.getMessageDetail</li>
 *   <li>PUT /api/v1/messages/{messageId}/read → MessageHandler.markAsRead</li>
 *   <li>PUT /api/v1/messages/read-all → MessageHandler.markAllAsRead</li>
 *   <li>GET /api/v1/messages/unread/count → MessageHandler.getUnreadCount</li>
 *   <li>GET /admin-api/v1/message-templates/{templateCode} → MessageTemplateHandler.getTemplateByCode</li>
 *   <li>GET /admin-api/v1/message-templates → MessageTemplateHandler.getEnabledTemplates</li>
 *   <li>POST /admin-api/v1/message-templates → MessageTemplateHandler.createTemplate</li>
 *   <li>PUT /admin-api/v1/message-templates/{templateId} → MessageTemplateHandler.updateTemplate</li>
 *   <li>DELETE /admin-api/v1/message-templates/{templateId} → MessageTemplateHandler.deleteTemplate</li>
 * </ul>
 *
 * <h2>特点</h2>
 * <ul>
 *   <li><strong>函数式风格</strong>：使用 RouterFunction 替代传统的 @RequestMapping</li>
 *   <li><strong>类型安全</strong>：编译期检查路由配置</li>
 *   <li><strong>响应式</strong>：完全基于 Reactor 的非阻塞处理</li>
 *   <li><strong>统一前缀</strong>：用户接口以 /api/v1/messages 开头，管理接口以 /admin-api/v1/message-templates 开头</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 * @see MessageHandler
 * @see MessageTemplateHandler
 */
@Configuration
public class MessageRouter {

    /**
     * 站内消息路由（用户接口）。
     *
     * <p>定义站内消息相关的所有路由规则，包括消息发送、收件箱、发件箱、消息详情、
     * 标记已读等功能。所有接口需要用户认证。
     *
     * @param handler MessageHandler 实例
     * @return RouterFunction 路由函数
     */
    @Bean(name = "internalMessageRoutes")
    @RouterOperations({
        @RouterOperation(
                path = "/api/v1/messages",
                method = RequestMethod.POST,
                beanClass = MessageHandler.class,
                beanMethod = "sendMessage",
                operation =
                        @Operation(
                                operationId = "sendMessage",
                                summary = "发送消息",
                                description = "发送站内消息给指定用户，支持单发和群发",
                                tags = {"消息管理"},
                                requestBody =
                                        @RequestBody(
                                                description = "发送消息请求",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                example =
                                                                                        "{\"receiverId\": 123, \"title\": \"测试消息\", \"content\": \"这是一条测试消息\"}"))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "发送成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "参数错误",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/messages/inbox",
                method = RequestMethod.GET,
                beanClass = MessageHandler.class,
                beanMethod = "getInbox",
                operation =
                        @Operation(
                                operationId = "getInbox",
                                summary = "获取收件箱",
                                description = "获取当前用户的收件箱消息列表，支持分页查询",
                                tags = {"消息管理"},
                                parameters = {
                                    @Parameter(
                                            name = "pageNum",
                                            description = "页码",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "1", example = "1")),
                                    @Parameter(
                                            name = "pageSize",
                                            description = "每页数量",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "10", example = "10"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "获取成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/messages/sent",
                method = RequestMethod.GET,
                beanClass = MessageHandler.class,
                beanMethod = "getSentMessages",
                operation =
                        @Operation(
                                operationId = "getSentMessages",
                                summary = "获取发件箱",
                                description = "获取当前用户的发件箱消息列表，支持分页查询",
                                tags = {"消息管理"},
                                parameters = {
                                    @Parameter(
                                            name = "pageNum",
                                            description = "页码",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "1", example = "1")),
                                    @Parameter(
                                            name = "pageSize",
                                            description = "每页数量",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "integer", defaultValue = "10", example = "10"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "获取成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/messages/{messageId}",
                method = RequestMethod.GET,
                beanClass = MessageHandler.class,
                beanMethod = "getMessageDetail",
                operation =
                        @Operation(
                                operationId = "getMessageDetail",
                                summary = "获取消息详情",
                                description = "获取指定消息的详细信息，包括发送人、接收人、标题、内容等",
                                tags = {"消息管理"},
                                parameters = {
                                    @Parameter(
                                            name = "messageId",
                                            description = "消息ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "123"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "获取成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(
                                            responseCode = "404",
                                            description = "消息不存在",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/messages/{messageId}/read",
                method = RequestMethod.PUT,
                beanClass = MessageHandler.class,
                beanMethod = "markAsRead",
                operation =
                        @Operation(
                                operationId = "markAsRead",
                                summary = "标记消息为已读",
                                description = "将指定消息标记为已读状态，用户只能标记自己收到的消息",
                                tags = {"消息管理"},
                                parameters = {
                                    @Parameter(
                                            name = "messageId",
                                            description = "消息ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "123"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "标记成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(
                                            responseCode = "404",
                                            description = "消息不存在",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/messages/read-all",
                method = RequestMethod.PUT,
                beanClass = MessageHandler.class,
                beanMethod = "markAllAsRead",
                operation =
                        @Operation(
                                operationId = "markAllAsRead",
                                summary = "全部标记为已读",
                                description = "将当前用户的所有未读消息标记为已读状态",
                                tags = {"消息管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "标记成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/api/v1/messages/unread/count",
                method = RequestMethod.GET,
                beanClass = MessageHandler.class,
                beanMethod = "getUnreadCount",
                operation =
                        @Operation(
                                operationId = "getUnreadCount",
                                summary = "获取未读消息数",
                                description = "获取当前用户的未读消息数量，用于消息提醒功能",
                                tags = {"消息管理"},
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "获取成功",
                                            content =
                                                    @Content(
                                                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                            schema =
                                                                    @Schema(
                                                                            example =
                                                                                    "{\"code\": 200, \"msg\": \"success\", \"data\": 5}")))
                                }))
    })
    public RouterFunction<ServerResponse> messageRoutes(MessageHandler handler) {
        return RouterFunctions.route()
                .path("/api/v1/messages", routes -> routes.POST(
                                "", contentType(MediaType.APPLICATION_JSON), handler::sendMessage)
                        .GET("/inbox", handler::getInbox)
                        .GET("/sent", handler::getSentMessages)
                        .GET("/{messageId}", handler::getMessageDetail)
                        .PUT("/{messageId}/read", handler::markAsRead)
                        .PUT("/read-all", handler::markAllAsRead)
                        .GET("/unread/count", handler::getUnreadCount))
                .build();
    }

    /**
     * 消息模板管理路由（管理后台）。
     *
     * <p>定义消息模板管理相关的所有路由规则，包括模板查询、创建、更新、删除等功能。
     * 所有接口需要管理员权限。
     *
     * @param handler MessageTemplateHandler 实例
     * @return RouterFunction 路由函数
     */
    @Bean
    @RouterOperations({
        @RouterOperation(
                path = "/admin-api/v1/message-templates/{templateCode}",
                method = RequestMethod.GET,
                beanClass = MessageTemplateHandler.class,
                beanMethod = "getTemplateByCode",
                operation =
                        @Operation(
                                operationId = "getTemplateByCode",
                                summary = "根据编码获取模板",
                                description = "通过模板编码获取消息模板详情，用于消息发送时选择模板",
                                tags = {"消息模板管理"},
                                parameters = {
                                    @Parameter(
                                            name = "templateCode",
                                            description = "模板编码",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "string", example = "SYSTEM_NOTICE"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "获取成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(
                                            responseCode = "404",
                                            description = "模板不存在",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/message-templates",
                method = RequestMethod.GET,
                beanClass = MessageTemplateHandler.class,
                beanMethod = "getEnabledTemplates",
                operation =
                        @Operation(
                                operationId = "getEnabledTemplates",
                                summary = "获取所有启用的模板",
                                description = "获取所有已启用的消息模板列表，支持按模板类型过滤",
                                tags = {"消息模板管理"},
                                parameters = {
                                    @Parameter(
                                            name = "templateType",
                                            description = "模板类型（可选）",
                                            in = ParameterIn.QUERY,
                                            schema = @Schema(type = "string", example = "SYSTEM"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "获取成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/message-templates",
                method = RequestMethod.POST,
                beanClass = MessageTemplateHandler.class,
                beanMethod = "createTemplate",
                operation =
                        @Operation(
                                operationId = "createTemplate",
                                summary = "创建消息模板",
                                description = "创建新的消息模板，支持自定义模板内容和变量占位符",
                                tags = {"消息模板管理"},
                                requestBody =
                                        @RequestBody(
                                                description = "消息模板创建请求",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                example =
                                                                                        "{\"templateCode\": \"CUSTOM_NOTICE\", \"templateName\": \"自定义通知\", \"templateContent\": \"您有一条新消息：{content}\"}"))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "创建成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(
                                            responseCode = "400",
                                            description = "参数错误",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/message-templates/{templateId}",
                method = RequestMethod.PUT,
                beanClass = MessageTemplateHandler.class,
                beanMethod = "updateTemplate",
                operation =
                        @Operation(
                                operationId = "updateTemplate",
                                summary = "更新消息模板",
                                description = "更新指定消息模板的内容、名称或状态",
                                tags = {"消息模板管理"},
                                parameters = {
                                    @Parameter(
                                            name = "templateId",
                                            description = "模板ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "1"))
                                },
                                requestBody =
                                        @RequestBody(
                                                description = "消息模板更新请求",
                                                required = true,
                                                content =
                                                        @Content(
                                                                mediaType = MediaType.APPLICATION_JSON_VALUE,
                                                                schema =
                                                                        @Schema(
                                                                                example =
                                                                                        "{\"templateName\": \"更新后的模板名称\", \"templateContent\": \"更新后的模板内容\"}"))),
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "更新成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(
                                            responseCode = "404",
                                            description = "模板不存在",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                })),
        @RouterOperation(
                path = "/admin-api/v1/message-templates/{templateId}",
                method = RequestMethod.DELETE,
                beanClass = MessageTemplateHandler.class,
                beanMethod = "deleteTemplate",
                operation =
                        @Operation(
                                operationId = "deleteTemplate",
                                summary = "删除消息模板",
                                description = "删除指定消息模板，逻辑删除，不影响历史消息",
                                tags = {"消息模板管理"},
                                parameters = {
                                    @Parameter(
                                            name = "templateId",
                                            description = "模板ID",
                                            required = true,
                                            in = ParameterIn.PATH,
                                            schema = @Schema(type = "integer", format = "int64", example = "1"))
                                },
                                responses = {
                                    @ApiResponse(
                                            responseCode = "200",
                                            description = "删除成功",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE)),
                                    @ApiResponse(
                                            responseCode = "404",
                                            description = "模板不存在",
                                            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE))
                                }))
    })
    public RouterFunction<ServerResponse> messageTemplateRoutes(MessageTemplateHandler handler) {
        return RouterFunctions.route()
                .path("/admin-api/v1/message-templates", routes -> routes.GET("", handler::getEnabledTemplates)
                        .POST("", contentType(MediaType.APPLICATION_JSON), handler::createTemplate)
                        .GET("/{templateCode}", handler::getTemplateByCode)
                        .PUT("/{templateId}", contentType(MediaType.APPLICATION_JSON), handler::updateTemplate)
                        .DELETE("/{templateId}", handler::deleteTemplate))
                .build();
    }
}
