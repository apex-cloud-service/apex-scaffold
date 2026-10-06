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
 * 企业微信消息 API 客户端。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Component
public class WecomMessageClient extends AbstractOfficeApiSupport implements MessagePort {
    private static final String USER_TARGET_TYPE = "USER";
    private final ObjectMapper objectMapper;

    /** 构造企业微信消息客户端并初始化 ObjectMapper。 */
    public WecomMessageClient(
            WebClient.Builder builder, OfficePlatformProperties properties, ObjectMapper objectMapper) {
        super(builder, properties, OfficePlatform.WECOM, "https://qyapi.weixin.qq.com");
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<String> sendMessage(MessageTarget target, MessageContent content) {
        validate(target, content);
        return accessToken()
                .flatMap(token -> call(
                        body -> text(body.get("msgid"), "企业微信 msgid"),
                        postJson("/cgi-bin/message/send?access_token=" + token, messageBody(target, content))));
    }

    @Override
    public Mono<String> queryMessageStatus(String messageId) {
        return Mono.error(new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信应用消息不支持通用状态查询"));
    }

    @Override
    public Mono<Boolean> recallMessage(String messageId) {
        required(messageId, "企业微信 msgid");
        return accessToken()
                .flatMap(token -> call(
                        body -> Boolean.TRUE,
                        postJson("/cgi-bin/message/recall?access_token=" + token, Map.of("msgid", messageId))));
    }

    @Override
    public String getPlatformType() {
        return "wecom";
    }

    private Mono<String> accessToken() {
        return call(
                body -> text(body.get("access_token"), "企业微信 access_token"),
                getAccessToken(
                        "/cgi-bin/gettoken",
                        Map.of(
                                "corpid", required(properties.getAppId(), "企业微信 corpId"),
                                "corpsecret", secret("企业微信"))));
    }

    private Map<String, Object> messageBody(MessageTarget target, MessageContent content) {
        Map<String, Object> body = new HashMap<>(4);
        body.put("agentid", required(properties.getAgentId(), "企业微信 agentId"));
        if (USER_TARGET_TYPE.equals(target.getTargetType())) {
            body.put("touser", target.getUserIds().stream().collect(Collectors.joining("|")));
        } else {
            body.put("touser", "@all");
        }
        body.putAll(messageContent(content));
        return body;
    }

    private Map<String, Object> messageContent(MessageContent content) {
        Map<String, Object> value = new HashMap<>(2);
        if (content.getType() == MessageType.TEXT) {
            value.put("msgtype", "text");
            value.put("text", Map.of("content", content.getText()));
        } else if (content.getType() == MessageType.MARKDOWN) {
            value.put("msgtype", "markdown");
            value.put("markdown", Map.of("content", content.getText()));
        } else {
            try {
                value.put("msgtype", "template_card");
                value.put(
                        "template_card",
                        objectMapper.readValue(((CardMessageContent) content).getCardJson(), Map.class));
            } catch (JsonProcessingException error) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信卡片内容不是合法 JSON");
            }
        }
        return value;
    }

    private void validate(MessageTarget target, MessageContent content) {
        if (target == null || !target.isValid()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信消息目标无效");
        }
        if (content == null || !content.isValid()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "企业微信消息内容无效");
        }
    }
}
