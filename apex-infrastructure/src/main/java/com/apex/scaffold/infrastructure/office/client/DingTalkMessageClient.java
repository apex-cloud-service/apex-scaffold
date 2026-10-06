package com.apex.scaffold.infrastructure.office.client;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.domain.office.OfficePlatform;
import com.apex.scaffold.domain.office.message.CardMessageContent;
import com.apex.scaffold.domain.office.message.MessageContent;
import com.apex.scaffold.domain.office.message.MessageTarget;
import com.apex.scaffold.domain.office.message.MessageType;
import com.apex.scaffold.domain.office.port.MessagePort;
import com.apex.scaffold.infrastructure.office.config.OfficePlatformProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 钉钉消息 API 客户端。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Component
public class DingTalkMessageClient extends AbstractOfficeApiSupport implements MessagePort {
    private static final String USER_TARGET_TYPE = "USER";
    private final ObjectMapper objectMapper;

    /** 构造钉钉消息客户端并初始化 ObjectMapper。 */
    public DingTalkMessageClient(
            WebClient.Builder builder, OfficePlatformProperties properties, ObjectMapper objectMapper) {
        super(builder, properties, OfficePlatform.DINGTALK, "https://oapi.dingtalk.com");
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<String> sendMessage(MessageTarget target, MessageContent content) {
        validate(target, content);
        if (!USER_TARGET_TYPE.equals(target.getTargetType())) {
            return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉应用工作通知仅支持用户目标"));
        }
        return accessToken()
                .flatMap(token -> call(
                        body -> text(body.get("task_id"), "task_id"),
                        postJson(
                                "/topapi/message/corpconversation/asyncsend_v2?access_token=" + token,
                                messageBody(target, content))));
    }

    @Override
    public Mono<String> queryMessageStatus(String messageId) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉应用消息不支持通用状态查询"));
    }

    @Override
    public Mono<Boolean> recallMessage(String messageId) {
        required(messageId, "钉钉 task_id");
        return accessToken()
                .flatMap(token -> call(
                        body -> Boolean.TRUE,
                        postJson(
                                "/topapi/message/corpconversation/recall?access_token=" + token,
                                Map.of(
                                        "agent_id",
                                        required(properties.getAgentId(), "钉钉 agentId"),
                                        "msg_task_id",
                                        messageId))));
    }

    @Override
    public String getPlatformType() {
        return "dingtalk";
    }

    private Mono<String> accessToken() {
        return call(
                body -> text(body.get("access_token"), "钉钉 access_token"),
                postJson(
                        "/gettoken",
                        Map.of("appkey", required(properties.getAppId(), "钉钉 appId"), "appsecret", secret("钉钉"))));
    }

    private Map<String, Object> messageBody(MessageTarget target, MessageContent content) {
        Map<String, Object> body = new HashMap<>(4);
        body.put("agent_id", required(properties.getAgentId(), "钉钉 agentId"));
        body.put("userid_list", target.getUserIds().stream().collect(Collectors.joining(",")));
        body.put("msg", messageContent(content));
        return body;
    }

    private Map<String, Object> messageContent(MessageContent content) {
        String type = content.getType() == MessageType.CARD
                ? "action_card"
                : content.getType() == MessageType.MARKDOWN ? "markdown" : "text";
        Map<String, Object> value = new HashMap<>(3);
        value.put("msgtype", type);
        if (content.getType() == MessageType.TEXT) {
            value.put("text", Map.of("content", content.getText()));
        } else if (content.getType() == MessageType.MARKDOWN) {
            value.put("markdown", Map.of("title", content.getText(), "text", content.getText()));
        } else {
            try {
                value.put(
                        "action_card", objectMapper.readValue(((CardMessageContent) content).getCardJson(), Map.class));
            } catch (JsonProcessingException error) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉卡片内容不是合法 JSON");
            }
        }
        return value;
    }

    private void validate(MessageTarget target, MessageContent content) {
        if (target == null || !target.isValid()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉消息目标无效");
        }
        if (content == null || !content.isValid()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "钉钉消息内容无效");
        }
    }
}
