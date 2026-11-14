package com.apex.scaffold.officeservice.interfaces.handler.office;

import com.apex.scaffold.common.annotation.IdempotentApi;
import com.apex.scaffold.common.annotation.OperationLog;
import com.apex.scaffold.common.annotation.RequirePermission;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.office.application.OfficeMessageService;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.message.*;
import com.apex.scaffold.officeservice.interfaces.dto.office.message.MessageStatusResponse;
import com.apex.scaffold.officeservice.interfaces.dto.office.message.SendMessageRequest;
import com.apex.scaffold.officeservice.interfaces.dto.office.message.SendMessageResponse;
import com.apex.scaffold.officeservice.interfaces.util.RequestHandlerUtil;
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
 * 采用 WebFlux 函数式编程风格，所有方法返回 {@code Mono<ServerResponse>}，成功数据使用 {@link Result} 包装。
 *
 * <p>提供的接口：
 * <ul>
 *   <li>发送消息 - 支持文本、Markdown、卡片等多种消息类型</li>
 *   <li>查询消息状态 - 根据消息ID查询发送状态</li>
 *   <li>重新发送消息 - 重新发送失败的消息</li>
 *   <li>撤回消息 - 撤回已发送成功的消息</li>
 * </ul>
 *
 * <p>安全保障：
 * <ul>
 *   <li>权限校验：所有接口均配置 {@code @RequirePermission} 注解，权限编码遵循
 *       {@code office:message:<action>} 规则，已在 Flyway 迁移脚本
 *       {@code V33__add_office_platform_menus.sql} 中登记</li>
 *   <li>操作审计：发送、重发、撤回等写操作配置 {@code @OperationLog} 注解，
 *       记录操作模块、类型与描述，不保存请求/响应体（消息内容可能包含业务敏感信息）</li>
 *   <li>幂等保护：发送、重发、撤回等写操作配置 {@code @IdempotentApi} 注解，
 *       防止客户端网络重试导致的消息重复发送/重复撤回</li>
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
    @Operation(
            operationId = "sendOfficeMessage",
            summary = "发送办公平台消息",
            description = "向飞书、钉钉或企业微信发送消息，支持文本、Markdown、卡片等多种消息类型")
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
                                schema = @Schema(implementation = OfficeSendMessageResult.class))),
        @ApiResponse(
                responseCode = "400",
                description = "参数校验或消息发送失败",
                content = @Content(schema = @Schema(implementation = OfficeMessageErrorResult.class)))
    })
    @RequirePermission("office:message:send")
    @OperationLog(
            module = "办公平台",
            type = OperationLog.OperationType.INSERT,
            description = "发送办公平台消息",
            saveRequest = false,
            saveResponse = false)
    @IdempotentApi
    public Mono<ServerResponse> sendMessage(ServerRequest request) {
        return requestHandlerUtil
                .validateAndMap(request, SendMessageRequest.class)
                .flatMap(req -> {
                    log.info(
                            "接收到发送消息请求，平台：{}，目标类型：{}，消息类型：{}",
                            req.getPlatform(),
                            req.getTargetType(),
                            req.getMessageType());

                    // 解析平台枚举（非法平台名会抛 IllegalArgumentException）
                    OfficePlatform platform = OfficePlatform.valueOf(req.getPlatform());

                    MessageTarget target = MessageTarget.builder()
                            .targetType(req.getTargetType())
                            .userIds(req.getUserIds())
                            .groupIds(req.getGroupIds())
                            .build();

                    MessageContent content = buildMessageContent(req);

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
    @Operation(
            operationId = "getOfficeMessageStatus",
            summary = "查询消息发送状态",
            description = "根据消息ID查询消息的详细状态信息，包括发送状态、平台消息ID、重试次数、发送时间等")
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
                                schema = @Schema(implementation = OfficeMessageStatusResult.class)))
    })
    @RequirePermission("office:message:query")
    @OperationLog(
            module = "OfficeMessage",
            type = OperationLog.OperationType.SELECT,
            description = "getMessageStatus",
            saveRequest = false,
            saveResponse = false)
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
    @Operation(
            operationId = "resendOfficeMessage",
            summary = "重新发送失败的消息",
            description = "重新发送状态为FAILED的消息；服务失败仍返回HTTP 200，通过code和msg表示失败")
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
                description = "重发结果；业务失败时data为空，code和msg表示失败",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OfficeMessageBooleanResult.class)))
    })
    @RequirePermission("office:message:resend")
    @OperationLog(
            module = "办公平台",
            type = OperationLog.OperationType.UPDATE,
            description = "重新发送失败的办公平台消息",
            saveRequest = false,
            saveResponse = false)
    @IdempotentApi
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
     * 撤回消息
     *
     * <p>撤回发送成功的消息。只能撤回状态为 SUCCESS 且已获得平台消息ID的消息。
     *
     * @param request HTTP 请求
     * @return 撤回结果
     */
    @Operation(
            operationId = "recallOfficeMessage",
            summary = "撤回消息",
            description = "撤回状态为SUCCESS且已获得平台消息ID的消息；服务失败仍返回HTTP 200，通过code和msg表示失败")
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
                description = "撤回结果；业务失败时data为空，code和msg表示失败",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = OfficeMessageBooleanResult.class)))
    })
    @RequirePermission("office:message:recall")
    @OperationLog(
            module = "办公平台",
            type = OperationLog.OperationType.UPDATE,
            description = "撤回已发送成功的办公平台消息",
            saveRequest = false,
            saveResponse = false)
    @IdempotentApi
    public Mono<ServerResponse> recallMessage(ServerRequest request) {
        Long messageId = Long.parseLong(request.pathVariable("messageId"));
        log.info("撤回消息，ID：{}", messageId);

        return messageService
                .recallMessage(messageId)
                .map(Result::success)
                .flatMap(result -> ServerResponse.ok().bodyValue(result))
                .onErrorResume(error -> {
                    log.error("撤回消息失败，ID：{}，原因：{}", messageId, error.getMessage());
                    return ServerResponse.ok().bodyValue(Result.error(error.getMessage()));
                });
    }

    /** OfficeSendMessageResult SendMessage 统一响应包装。 */
    @Schema(name = "OfficeSendMessageResult")
    public static class OfficeSendMessageResult extends Result<SendMessageResponse> {}

    /** OfficeMessageStatusResult MessageStatus 统一响应包装。 */
    @Schema(name = "OfficeMessageStatusResult")
    public static class OfficeMessageStatusResult extends Result<MessageStatusResponse> {}

    /** OfficeMessageBooleanResult 布尔结果统一响应包装。 */
    @Schema(name = "OfficeMessageBooleanResult")
    public static class OfficeMessageBooleanResult extends Result<Boolean> {}

    /** OfficeMessageErrorResult 无数据统一响应包装。 */
    @Schema(name = "OfficeMessageErrorResult")
    public static class OfficeMessageErrorResult extends Result<Void> {}

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
