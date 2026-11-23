package com.apex.scaffold.officeservice.interfaces.handler;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.application.OfficeMessageService;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.message.MessageTarget;
import com.apex.scaffold.office.domain.message.TextMessageContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * 开放消息接口（{@code POST /open-api/messages}）：只做单条文本消息投递。
 *
 * <p>租户来自开放应用令牌的可信声明；目标用户/群组仍由消息服务按租户隔离校验。
 * 不开放模板管理、重发、撤回等管理能力，避免以应用身份获得管理员权限。
 *
 * @author Apex Team
 * @version 1.3.2
 * @since 1.3.2
 */
@Component
@Tag(name = "开放平台-站内信", description = "开放应用级单条文本消息投递；不开放模板管理、重发与撤回")
public class OpenMessageHandler {

    /** 开放消息投递结果的统一响应结构，仅用于接口文档。 */
    @Schema(name = "OpenMessageSendResult")
    public static class SendResult extends Result<Map<String, String>> {}

    private final OfficeMessageService messages;

    /**
     * 构造处理器。
     *
     * @param messages 办公消息服务
     */
    public OpenMessageHandler(OfficeMessageService messages) {
        this.messages = messages;
    }

    /**
     * 以开放应用身份投递单条文本消息。
     *
     * @param request 服务器请求，请求体为 {@link SendRequest}
     * @return 统一响应，{@code data.messageId} 为平台返回的消息标识
     * @throws BusinessException 请求体缺失、platform/text 为空或目标不合法时抛出
     */
    @Operation(
            operationId = "openSendMessage",
            summary = "开放平台投递单条文本消息",
            description = "以开放应用身份投递单条文本消息；租户来自开放应用令牌的可信声明，" + "目标用户/群组仍由消息服务按租户隔离校验。仅支持文本消息，不开放模板管理、重发与撤回")
    @RequestBody(
            required = true,
            description = "开放消息请求；platform 与 text 必填，目标须提供 userIds 或 groupIds 之一",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SendRequest.class)))
    @ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "投递成功，data.messageId 为平台返回的消息标识",
                content =
                        @Content(mediaType = "application/json", schema = @Schema(implementation = SendResult.class))),
        @ApiResponse(responseCode = "400", description = "请求体缺失、platform/text 为空、目标不合法或平台不支持"),
        @ApiResponse(responseCode = "401", description = "未通过网关 scope 断言或能力门禁"),
        @ApiResponse(responseCode = "403", description = "开放应用无消息投递能力")
    })
    public Mono<ServerResponse> send(ServerRequest request) {
        return request.bodyToMono(SendRequest.class)
                .switchIfEmpty(Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "请求体缺失")))
                .flatMap(body -> {
                    if (body.platform() == null
                            || body.text() == null
                            || body.text().isBlank()) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "platform/text 必填"));
                    }
                    MessageTarget target = MessageTarget.builder()
                            .targetType(body.targetType() == null ? "USER" : body.targetType())
                            .userIds(body.userIds())
                            .groupIds(body.groupIds())
                            .build();
                    if (!target.isValid()) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "消息目标不合法"));
                    }
                    OfficePlatform platform;
                    try {
                        platform = OfficePlatform.valueOf(body.platform().trim().toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException error) {
                        return Mono.error(new BusinessException(ResultCode.PARAM_ERROR, "不支持的平台"));
                    }
                    return messages.sendMessage(
                                    platform,
                                    target,
                                    TextMessageContent.builder()
                                            .text(body.text())
                                            .build())
                            .flatMap(id -> ServerResponse.ok().bodyValue(Result.success(Map.of("messageId", id))));
                });
    }

    /**
     * 开放消息请求体；字段收敛到文本消息所需最小集。
     *
     * @param platform 平台编码
     * @param targetType USER/GROUP
     * @param userIds 目标用户
     * @param groupIds 目标群组
     * @param text 文本内容
     */
    @Schema(name = "OpenSendMessageRequest", description = "开放消息请求体；字段收敛到文本消息所需最小集")
    public record SendRequest(
            @Schema(
                            description = "平台编码，取值见 OfficePlatform（大小写不敏感）",
                            example = "DINGTALK",
                            allowableValues = {"DINGTALK", "WECHAT_WORK", "FEISHU", "WECOM"},
                            requiredMode = Schema.RequiredMode.REQUIRED)
                    String platform,
            @Schema(
                            description = "目标类型：USER=按用户投递，GROUP=按群组投递；缺省 USER",
                            example = "USER",
                            allowableValues = {"USER", "GROUP"},
                            defaultValue = "USER")
                    String targetType,
            @ArraySchema(
                            arraySchema = @Schema(description = "目标用户 ID 列表；targetType=USER 时必填"),
                            schema = @Schema(type = "string", example = "u1001"))
                    List<String> userIds,
            @ArraySchema(
                            arraySchema = @Schema(description = "目标群组 ID 列表；targetType=GROUP 时必填"),
                            schema = @Schema(type = "string", example = "g2001"))
                    List<String> groupIds,
            @Schema(description = "文本内容，不能为空白", example = "您的审批单已通过", requiredMode = Schema.RequiredMode.REQUIRED)
                    String text) {}
}
