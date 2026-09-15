package com.apex.scaffold.interfaces.assembler.office;

import com.apex.scaffold.application.office.command.SendMessageCommand;
import com.apex.scaffold.domain.office.message.OfficeMessage;
import com.apex.scaffold.interfaces.dto.office.message.MessageResponse;
import com.apex.scaffold.interfaces.dto.office.message.SendMessageRequest;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 办公消息装配器
 *
 * <p>负责DTO与Command、实体之间的转换。
 *
 * @author Apex Team
 * @since 1.3.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OfficeMessageAssembler {
    
    /**
     * 请求转命令
     *
     * @param request 发送消息请求
     * @return 发送消息命令
     */
    SendMessageCommand toCommand(SendMessageRequest request);
    
    /**
     * 实体转响应
     *
     * @param message 消息实体
     * @return 消息响应
     */
    MessageResponse toResponse(OfficeMessage message);
}
