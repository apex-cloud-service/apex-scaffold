package com.apex.scaffold.infrastructure.office.persistence.converter;

import com.apex.scaffold.domain.office.message.MessageStatus;
import com.apex.scaffold.domain.office.message.MessageType;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.domain.office.message.RecipientType;
import com.apex.scaffold.infrastructure.office.persistence.po.OfficeMessagePO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

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
    @Mapping(target = "recipientType", expression = "java(recipientTypeToString(entity.getRecipientType()))")
    @Mapping(target = "recipientIds", expression = "java(listToJson(entity.getRecipientIds()))")
    @Mapping(target = "status", expression = "java(statusToString(entity.getStatus()))")
    public abstract OfficeMessagePO toPO(OfficeMessage entity);
    
    /**
     * 持久化对象转领域实体
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    @Mapping(target = "messageType", expression = "java(stringToMessageType(po.getMessageType()))")
    @Mapping(target = "recipientType", expression = "java(stringToRecipientType(po.getRecipientType()))")
    @Mapping(target = "recipientIds", expression = "java(jsonToList(po.getRecipientIds()))")
    @Mapping(target = "status", expression = "java(stringToStatus(po.getStatus()))")
    public abstract OfficeMessage toEntity(OfficeMessagePO po);
    
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
     * RecipientType 转字符串
     */
    protected String recipientTypeToString(RecipientType recipientType) {
        return recipientType != null ? recipientType.name() : null;
    }
    
    /**
     * 字符串转 RecipientType
     */
    protected RecipientType stringToRecipientType(String recipientType) {
        try {
            return recipientType != null ? RecipientType.valueOf(recipientType) : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
    
    /**
     * MessageStatus 转字符串
     */
    protected String statusToString(MessageStatus status) {
        return status != null ? status.name() : null;
    }
    
    /**
     * 字符串转 MessageStatus
     */
    protected MessageStatus stringToStatus(String status) {
        try {
            return status != null ? MessageStatus.valueOf(status) : null;
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
