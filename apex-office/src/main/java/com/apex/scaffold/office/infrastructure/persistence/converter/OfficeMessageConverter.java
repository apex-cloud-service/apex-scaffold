package com.apex.scaffold.office.infrastructure.persistence.converter;

import com.apex.scaffold.office.domain.message.CardMessageContent;
import com.apex.scaffold.office.domain.message.MarkdownMessageContent;
import com.apex.scaffold.office.domain.message.MessageType;
import com.apex.scaffold.office.domain.message.OfficeMessage;
import com.apex.scaffold.office.domain.message.TextMessageContent;
import com.apex.scaffold.office.infrastructure.persistence.po.OfficeMessagePO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 办公消息转换器
 *
 * <p>负责领域实体（OfficeMessage）和持久化对象（OfficeMessagePO）之间的转换。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
@SuppressWarnings({"PMD.AbstractClassShouldStartWithAbstractNamingRule", "PMD.LowerCamelCaseVariableNamingRule"})
public abstract class OfficeMessageConverter {

    /** 消息接收者类型：用户。 */
    private static final String TARGET_TYPE_USER = "USER";
    /** 消息接收者类型：群组。 */
    private static final String TARGET_TYPE_GROUP = "GROUP";

    /**
     * MapStruct 生成的实现类无法声明带参构造器，故采用 JSR-250 {@code @Resource} 字段注入，
     * 以避免使用 Spring {@code @Autowired}。
     */
    @Resource
    private ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 领域实体转持久化对象
     *
     * @param entity 领域实体
     * @return 持久化对象
     */
    @Mapping(target = "messageType", expression = "java(messageTypeToString(entity.getMessageType()))")
    @Mapping(target = "recipientType", expression = "java(targetTypeFromEntity(entity.getTarget()))")
    @Mapping(target = "recipientIds", expression = "java(recipientIdsFromEntity(entity.getTarget()))")
    @Mapping(target = "content", expression = "java(contentToJson(entity.getContent()))")
    @Mapping(target = "status", source = "sendStatus")
    @Mapping(target = "resultMessage", source = "failureReason")
    public abstract OfficeMessagePO toPO(OfficeMessage entity);

    /**
     * 持久化对象转领域实体
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    @Mapping(target = "messageType", expression = "java(stringToMessageType(po.getMessageType()))")
    @Mapping(target = "target", expression = "java(targetFromPo(po))")
    @Mapping(target = "content", expression = "java(contentFromJson(po))")
    @Mapping(target = "sendStatus", source = "status")
    @Mapping(target = "failureReason", source = "resultMessage")
    public abstract OfficeMessage toEntity(OfficeMessagePO po);

    /**
     * 从 MessageTarget 提取目标类型
     */
    protected String targetTypeFromEntity(com.apex.scaffold.office.domain.message.MessageTarget target) {
        return target != null ? target.getTargetType() : null;
    }

    /**
     * 从 MessageTarget 提取接收者ID列表
     */
    protected String recipientIdsFromEntity(com.apex.scaffold.office.domain.message.MessageTarget target) {
        if (target == null) {
            return null;
        }

        if (TARGET_TYPE_USER.equals(target.getTargetType()) && target.getUserIds() != null) {
            return listToJson(target.getUserIds());
        } else if (TARGET_TYPE_GROUP.equals(target.getTargetType()) && target.getGroupIds() != null) {
            return listToJson(target.getGroupIds());
        }

        return null;
    }

    /**
     * 从 PO 构建 MessageTarget
     */
    protected com.apex.scaffold.office.domain.message.MessageTarget targetFromPo(OfficeMessagePO po) {
        if (po.getRecipientType() == null) {
            return null;
        }

        List<String> ids = jsonToList(po.getRecipientIds());

        if (TARGET_TYPE_USER.equals(po.getRecipientType())) {
            return com.apex.scaffold.office.domain.message.MessageTarget.builder()
                    .targetType(TARGET_TYPE_USER)
                    .userIds(ids)
                    .build();
        } else if (TARGET_TYPE_GROUP.equals(po.getRecipientType())) {
            return com.apex.scaffold.office.domain.message.MessageTarget.builder()
                    .targetType(TARGET_TYPE_GROUP)
                    .groupIds(ids)
                    .build();
        }

        return null;
    }

    /**
     * 从持久化对象恢复消息内容
     */
    protected com.apex.scaffold.office.domain.message.MessageContent contentFromJson(OfficeMessagePO po) {
        if (po.getContent() == null || po.getContent().isBlank()) {
            return null;
        }
        try {
            MessageType type = stringToMessageType(po.getMessageType());
            JsonNode node = objectMapper.readTree(po.getContent());
            JsonNode data = node.has("data") ? node.get("data") : node;
            return switch (type) {
                case TEXT -> TextMessageContent.builder()
                        .text(data.has("text") ? data.get("text").asText() : data.asText())
                        .build();
                case MARKDOWN -> MarkdownMessageContent.builder()
                        .markdown(data.has("markdown") ? data.get("markdown").asText() : data.asText())
                        .title(data.has("title") ? data.get("title").asText() : null)
                        .build();
                case CARD -> CardMessageContent.builder()
                        .cardJson(data.has("cardJson") ? data.get("cardJson").asText() : data.asText())
                        .title(data.has("title") ? data.get("title").asText() : null)
                        .cardType(data.has("cardType") ? data.get("cardType").asText() : null)
                        .build();
            };
        } catch (Exception e) {
            throw new IllegalArgumentException("办公消息内容格式无效", e);
        }
    }

    /**
     * 将 MessageContent 序列化为 JSON
     */
    protected String contentToJson(com.apex.scaffold.office.domain.message.MessageContent content) {
        if (content == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new IllegalArgumentException("办公消息内容无法序列化", e);
        }
    }

    /**
     * MessageType 转字符串
     */
    protected String messageTypeToString(MessageType messageType) {
        return messageType != null ? messageType.name() : null;
    }

    /**
     * 字符串转 MessageType
     */
    protected MessageType stringToMessageType(String messageType) {
        try {
            return messageType != null ? MessageType.valueOf(messageType) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * List 转 JSON 字符串
     */
    protected String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * JSON 字符串转 List
     */
    protected List<String> jsonToList(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return null;
        }
    }
}
