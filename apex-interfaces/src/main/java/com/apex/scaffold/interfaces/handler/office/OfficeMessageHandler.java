package com.apex.scaffold.interfaces.handler.office;

import com.apex.scaffold.application.office.OfficeMessageService;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.domain.office.message.*;
import com.apex.scaffold.interfaces.dto.office.message.MessageStatusResponse;
import com.apex.scaffold.interfaces.dto.office.message.SendMessageRequest;
import com.apex.scaffold.interfaces.dto.office.message.SendMessageResponse;
import com.apex.scaffold.interfaces.util.RequestHandlerUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 办公平台消息发送 Handler
 *
 * <p>处理消息发送相关的 HTTP 请求，负责参数验证、DTO 转换和响应构建。
 * 采用 WebFlux 函数式编程风格，所有方法返回 {@link Mono<ServerResponse>}。
 *
 * <p>提供的接口：
 * <ul>
 *   <li>发送消息 - 支持文本、Markdown、卡片等多种消息类型</li>
 *   <li>查询消息状态 - 根据消息ID查询发送状态</li>
 *   <li>重新发送消息 - 重新发送失败的消息</li>
 * </ul>
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Tag(name = "办公平台消息管理", description = "办公平台消息发送、状态查询和重发等相关接口")
public class OfficeMessageHandler {

    private final OfficeMessageService messageService;
    private final RequestHandlerUtil requestHandlerUtil;

    /**
     * 发送消息
     *
     * <p>接收客户端的消息发送请求，验证参数后调用应用服务发送消息。
     * 支持三大办公平台和多种消息类型。
     *
     * <p>业务流程：
     * <ol>
     *   <li>验证请求参数（平台、目标、消息类型、内容）</li>
     *   <li>构建领域对象（MessageTarget、MessageContent）</li>
     *   <li>调用应用服务发送消息</li>
     *   <li>返回消息ID和发送状态</li>
     * </ol>
     *
     * @param request HTTP 请求
     * @return 发送结果响应
     */
    @Operation(summary = "发送办公平台消息", description = "向飞书、钉钉或企业微信发送消息，支持文本、Markdown、卡片等多种消息类型")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "消息发送请求信息",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SendMessageRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "发送成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = SendMessageResponse.class))),
        @ApiResponse(
                responseCode = "400",
                description = "请求参数错误",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "500",
                description = "消息发送失败",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> sendMessage(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, SendMessageRequest.class)
                .flatMap(req -> {
                    log.info(
                            "接收到发送消息请求，平台：{}，目标类型：{}，消息类型：{}",
                            req.getPlatform(),
                            req.getTargetType(),
                            req.getMessageType());

                    // 转换平台类型
                    OfficePlatform platform = OfficePlatform.valueOf(req.getPlatform());

                    // 构建消息目标
                    MessageTarget target = MessageTarget.builder()
                            .targetType(req.getTargetType())
                            .userIds(req.getUserIds())
                            .groupIds(req.getGroupIds())
                            .build();

                    // 构建消息内容
                    MessageContent content = buildMessageContent(req);

                    // 发送消息
                    return messageService.sendMessage(platform, target, content).flatMap(messageId -> messageService
                            .getMessageStatus(messageId)
                            .map(message -> SendMessageResponse.builder()
                                    .messageId(message.getId())
                                    .sendStatus(message.getSendStatus())
                                    .platformMessageId(message.getPlatformMessageId())
                                    .failureReason(message.getFailureReason())
                                    .build()));
                })
                .flatMap(response -> requestHandlerUtil.ok(Result.success(response)))
                .onErrorResume(e -> requestHandlerUtil.handleError(e));
    }

    /**
     * 查询消息状态
     *
     * <p>根据消息ID查询消息的详细状态信息，包括发送状态、平台消息ID、
     * 重试次数、发送时间等。
     *
     * @param request HTTP 请求
     * @return 消息状态响应
     */
    @Operation(summary = "查询消息发送状态", description = "根据消息ID查询消息的详细状态信息，包括发送状态、平台消息ID、重试次数、发送时间等")
    @Parameters({
        @Parameter(
                name = "messageId",
                in = ParameterIn.PATH,
                required = true,
                description = "消息ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "查询成功",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = MessageStatusResponse.class))),
        @ApiResponse(
                responseCode = "404",
                description = "消息不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> getMessageStatus(ServerRequest request) {
        Long messageId = Long.parseLong(request.pathVariable("messageId"));
        log.info("查询消息状态，ID：{}", messageId);

        return messageService
                .getMessageStatus(messageId)
                .map(message -> MessageStatusResponse.builder()
                        .messageId(message.getId())
                        .platform(message.getPlatform().name())
                        .messageType(message.getMessageType().name())
                        .sendStatus(message.getSendStatus())
                        .platformMessageId(message.getPlatformMessageId())
                        .failureReason(message.getFailureReason())
                        .retryCount(message.getRetryCount())
                        .createdAt(message.getCreatedAt())
                        .sentAt(message.getSentAt())
                        .build())
                .map(Result::success)
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    /**
     * 重新发送消息
     *
     * <p>重新发送失败的消息。只能重新发送状态为 FAILED 的消息，
     * 成功后更新消息状态和平台消息ID。
     *
     * @param request HTTP 请求
     * @return 重新发送结果
     */
    @Operation(summary = "重新发送失败的消息", description = "重新发送状态为FAILED的消息，成功后更新消息状态和平台消息ID")
    @Parameters({
        @Parameter(
                name = "messageId",
                in = ParameterIn.PATH,
                required = true,
                description = "消息ID",
                schema = @Schema(type = "integer", format = "int64"))
    })
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "重新发送成功",
                content = @Content(mediaType = "application/json", schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "400",
                description = "消息状态不允许重发",
                content = @Content(schema = @Schema(implementation = Result.class))),
        @ApiResponse(
                responseCode = "404",
                description = "消息不存在",
                content = @Content(schema = @Schema(implementation = Result.class)))
    })
    public Mono<ServerResponse> resendMessage(ServerRequest request) {
        Long messageId = Long.parseLong(request.pathVariable("messageId"));
        log.info("重新发送消息，ID：{}", messageId);

        return messageService
                .resendMessage(messageId)
                .map(success -> Result.success(success))
                .flatMap(result -> ServerResponse.ok().bodyValue(result))
                .onErrorResume(error -> {
                    log.error("重新发送消息失败，ID：{}，原因：{}", messageId, error.getMessage());
                    return ServerResponse.ok().bodyValue(Result.error(error.getMessage()));
                });
    }

    /**
     * 根据请求构建消息内容
     *
     * @param request 发送消息请求
     * @return 消息内容对象
     */
    private MessageContent buildMessageContent(SendMessageRequest request) {
        return switch (request.getMessageType()) {
            case "TEXT" -> TextMessageContent.builder()
                    .text(request.getContent())
                    .build();
            case "MARKDOWN" -> MarkdownMessageContent.builder()
                    .markdown(request.getContent())
                    .title(request.getTitle())
                    .build();
            case "CARD" -> CardMessageContent.builder()
                    .title(request.getTitle())
                    .cardJson(request.getCardJson() != null ? request.getCardJson() : request.getContent())
                    .cardType("interactive")
                    .build();
            default -> throw new IllegalArgumentException("不支持的消息类型：" + request.getMessageType());
        };
    }
}
