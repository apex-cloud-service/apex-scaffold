package com.apex.scaffold.officeservice.interfaces.handler;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.Result;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.application.OfficeMessageService;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.message.MessageTarget;
import com.apex.scaffold.office.domain.message.TextMessageContent;
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
public class OpenMessageHandler {

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
    public record SendRequest(
            String platform, String targetType, List<String> userIds, List<String> groupIds, String text) {}
}
