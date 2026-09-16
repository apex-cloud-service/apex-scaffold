package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.office.message.*;
import com.apex.scaffold.infrastructure.persistence.po.OfficeMessagePO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 办公平台消息转换器
 *
 * <p>负责领域对象 {@link OfficeMessage} 和持久化对象 {@link OfficeMessagePO} 之间的转换。
 * 使用 MapStruct 自动生成转换代码，处理复杂对象的序列化和反序列化。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class OfficeMessageConverter {

    @Autowired
    protected ObjectMapper objectMapper;

    /**
     * 领域对象转换为持久化对象
     *
     * @param domain 领域对象
     * @return 持久化对象
     */
    @Mapping(target = "platform", expression = "java(domain.getPlatform().name())")
    @Mapping(target = "messageType", expression = "java(domain.getMessageType().name())")
    @Mapping(target = "targetType", source = "target.targetType")
    @Mapping(target = "targetUserIds", source = "target.userIds", qualifiedByName = "listToJson")
    @Mapping(target = "targetGroupIds", source = "target.groupIds", qualifiedByName = "listToJson")
    @Mapping(target = "content", source = "content", qualifiedByName = "contentToJson")
    public abstract OfficeMessagePO toMybatisPO(OfficeMessage domain);

    /**
     * 持久化对象转换为领域对象
     *
     * @param po 持久化对象
     * @return 领域对象
     */
    @Mapping(
            target = "platform",
            expression = "java(com.apex.scaffold.domain.office.OfficePlatform.valueOf(po.getPlatform()))")
    @Mapping(
            target = "messageType",
            expression = "java(com.apex.scaffold.domain.office.message.MessageType.valueOf(po.getMessageType()))")
    @Mapping(target = "target", expression = "java(buildTarget(po))")
    @Mapping(target = "content", expression = "java(buildContent(po))")
    public abstract OfficeMessage toDomain(OfficeMessagePO po);

    /**
     * 列表转换为 JSON 字符串
     *
     * @param list 列表
     * @return JSON 字符串
     */
    @Named("listToJson")
    protected String listToJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("列表转JSON失败", e);
        }
    }

    /**
     * JSON 字符串转换为列表
     *
     * @param json JSON 字符串
     * @return 列表
     */
    protected List<String> jsonToList(String json) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(
                    json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON转列表失败", e);
        }
    }

    /**
     * 消息内容转换为 JSON 字符串
     *
     * @param content 消息内容
     * @return JSON 字符串
     */
    @Named("contentToJson")
    protected String contentToJson(MessageContent content) {
        if (content == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("消息内容转JSON失败", e);
        }
    }

    /**
     * 构建消息目标对象
     *
     * @param po 持久化对象
     * @return 消息目标
     */
    protected MessageTarget buildTarget(OfficeMessagePO po) {
        return MessageTarget.builder()
                .targetType(po.getTargetType())
                .userIds(jsonToList(po.getTargetUserIds()))
                .groupIds(jsonToList(po.getTargetGroupIds()))
                .build();
    }

    /**
     * 构建消息内容对象
     *
     * @param po 持久化对象
     * @return 消息内容
     */
    protected MessageContent buildContent(OfficeMessagePO po) {
        if (po.getContent() == null) {
            return null;
        }
        try {
            MessageType messageType = MessageType.valueOf(po.getMessageType());
            return switch (messageType) {
                case TEXT -> objectMapper.readValue(po.getContent(), TextMessageContent.class);
                case MARKDOWN -> objectMapper.readValue(po.getContent(), MarkdownMessageContent.class);
                case CARD -> objectMapper.readValue(po.getContent(), CardMessageContent.class);
            };
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON转消息内容失败", e);
        }
    }
}
