package com.apex.scaffold.office.infrastructure.client;

import com.apex.scaffold.common.exception.BusinessException;
import com.apex.scaffold.common.result.ResultCode;
import com.apex.scaffold.office.domain.OfficePlatform;
import com.apex.scaffold.office.domain.message.CardMessageContent;
import com.apex.scaffold.office.domain.message.MessageContent;
import com.apex.scaffold.office.domain.message.MessageTarget;
import com.apex.scaffold.office.domain.message.MessageType;
import com.apex.scaffold.office.domain.port.MessagePort;
import com.apex.scaffold.office.infrastructure.config.OfficePlatformProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 飞书消息 API 客户端。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Component
public class FeishuMessageClient extends AbstractOfficeApiSupport implements MessagePort {
    private final ObjectMapper objectMapper;

    /**
     * 构造飞书消息客户端并配置平台 API 地址。
     *
     * @param builder 用于创建飞书 HTTP 客户端的构建器
     * @param properties 平台凭证及请求超时配置
     * @param objectMapper 消息内容的 JSON 转换器
     */
    public FeishuMessageClient(
            WebClient.Builder builder, OfficePlatformProperties properties, ObjectMapper objectMapper) {
        super(builder, properties, OfficePlatform.FEISHU, "https://open.feishu.cn");
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<String> sendMessage(MessageTarget target, MessageContent content) {
        validate(target, content);
        return tenantToken()
                .flatMap(token -> call(
                        body -> {
                            Map<?, ?> data = (Map<?, ?>) body.get("data");
                            return text(data == null ? null : data.get("message_id"), "message_id");
                        },
                        postJson(
                                "/open-apis/im/v1/messages?receive_id_type=" + receiveType(target),
                                messageBody(target, content),
                                Map.of("Authorization", "Bearer " + token))));
    }

    @Override
    public Mono<String> queryMessageStatus(String messageId) {
        required(messageId, "飞书 messageId");
        return tenantToken()
                .flatMap(token -> getJsonWithHeaders(
                        "/open-apis/im/v1/messages/" + messageId, Map.of("Authorization", "Bearer " + token)))
                .map(body -> "EXISTS");
    }

    @Override
    public Mono<Boolean> recallMessage(String messageId) {
        required(messageId, "飞书 messageId");
        return tenantToken()
                .flatMap(token -> deleteWithHeaders(
                        "/open-apis/im/v1/messages/" + messageId, Map.of("Authorization", "Bearer " + token)))
                .thenReturn(Boolean.TRUE);
    }

    @Override
    public String getPlatformType() {
        return "feishu";
    }

    private Mono<String> tenantToken() {
        return call(
                body -> text(body.get("tenant_access_token"), "tenant_access_token"),
                postJson(
                        "/open-apis/auth/v3/tenant_access_token/internal",
                        Map.of("app_id", required(properties.getAppId(), "飞书 appId"), "app_secret", secret("飞书"))));
    }

    private Map<String, Object> messageBody(MessageTarget target, MessageContent content) {
        Map<String, Object> body = new HashMap<>(3);
        body.put("receive_id", targetId(target));
        body.put("msg_type", messageType(content));
        body.put("content", contentJson(content));
        return body;
    }

    private String contentJson(MessageContent content) {
        try {
            if (content.getType() == MessageType.TEXT) {
                return objectMapper.writeValueAsString(Map.of("text", content.getText()));
            }
            if (content.getType() == MessageType.MARKDOWN) {
                return objectMapper.writeValueAsString(Map.of("text", content.getText()));
            }
            String cardJson = ((CardMessageContent) content).getCardJson();
            return objectMapper.writeValueAsString(
                    objectMapper.readValue(cardJson, new TypeReference<Map<String, Object>>() {}));
        } catch (Exception error) {
            throw new IllegalArgumentException("飞书消息内容不是合法 JSON", error);
        }
    }

    private String messageType(MessageContent content) {
        return content.getType() == MessageType.CARD ? "interactive" : "text";
    }

    private String receiveType(MessageTarget target) {
        return "USER".equals(target.getTargetType()) ? "open_id" : "chat_id";
    }

    private String targetId(MessageTarget target) {
        return "USER".equals(target.getTargetType())
                ? target.getUserIds().get(0)
                : target.getGroupIds().get(0);
    }

    private void validate(MessageTarget target, MessageContent content) {
        if (target == null || !target.isValid()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "飞书消息目标无效");
        }
        if (content == null || !content.isValid()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "飞书消息内容无效");
        }
    }
}
