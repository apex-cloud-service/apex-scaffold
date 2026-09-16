package com.apex.scaffold.infrastructure.office.persistence.converter;

import com.apex.scaffold.domain.office.message.MessageType;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.infrastructure.office.persistence.po.OfficeMessagePO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 办公消息转换器
 *
 * <p>负责领域实体（OfficeMessage）和持久化对象（OfficeMessagePO）之间的转换。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public abstract class OfficeMessageConverter {

    @Autowired
    protected ObjectMapper objectMapper;

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
    public abstract OfficeMessagePO toPO(OfficeMessage entity);

    /**
     * 持久化对象转领域实体
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    @Mapping(target = "messageType", expression = "java(stringToMessageType(po.getMessageType()))")
    @Mapping(target = "target", expression = "java(targetFromPo(po))")
    @Mapping(target = "content", ignore = true)
    @Mapping(target = "sendStatus", source = "status")
    public abstract OfficeMessage toEntity(OfficeMessagePO po);

    /**
     * 从 MessageTarget 提取目标类型
     */
    protected String targetTypeFromEntity(com.apex.scaffold.domain.office.message.MessageTarget target) {
        return target != null ? target.getTargetType() : null;
    }

    /**
     * 从 MessageTarget 提取接收者ID列表
     */
    protected String recipientIdsFromEntity(com.apex.scaffold.domain.office.message.MessageTarget target) {
        if (target == null) {
            return null;
        }

        if ("USER".equals(target.getTargetType()) && target.getUserIds() != null) {
            return listToJson(target.getUserIds());
        } else if ("GROUP".equals(target.getTargetType()) && target.getGroupIds() != null) {
            return listToJson(target.getGroupIds());
        }

        return null;
    }

    /**
     * 从 PO 构建 MessageTarget
     */
    protected com.apex.scaffold.domain.office.message.MessageTarget targetFromPo(OfficeMessagePO po) {
        if (po.getRecipientType() == null) {
            return null;
        }

        List<String> ids = jsonToList(po.getRecipientIds());

        if ("USER".equals(po.getRecipientType())) {
            return com.apex.scaffold.domain.office.message.MessageTarget.builder()
                    .targetType("USER")
                    .userIds(ids)
                    .build();
        } else if ("GROUP".equals(po.getRecipientType())) {
            return com.apex.scaffold.domain.office.message.MessageTarget.builder()
                    .targetType("GROUP")
                    .groupIds(ids)
                    .build();
        }

        return null;
    }

    /**
     * 将 MessageContent 序列化为 JSON
     */
    protected String contentToJson(com.apex.scaffold.domain.office.message.MessageContent content) {
        if (content == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(content.toPlatformContent());
        } catch (Exception e) {
            return null;
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
