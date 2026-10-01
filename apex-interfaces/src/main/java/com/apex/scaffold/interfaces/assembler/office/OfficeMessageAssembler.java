package com.apex.scaffold.interfaces.assembler.office;

import com.apex.scaffold.domain.office.message.MessageContent;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.interfaces.dto.office.message.MessageResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 办公消息装配器
 *
 * <p>负责领域实体与接口DTO之间的转换。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OfficeMessageAssembler {

    /**
     * 实体转响应
     *
     * @param message 消息实体
     * @return 消息响应
     */
    @Mapping(target = "content", expression = "java(mapContent(message.getContent()))")
    MessageResponse toResponse(OfficeMessage message);

    /**
     * 将MessageContent对象映射为字符串
     *
     * @param content 消息内容对象
     * @return 消息内容字符串
     */
    default String mapContent(MessageContent content) {
        return content != null ? content.getText() : null;
    }
}
