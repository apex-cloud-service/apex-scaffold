package com.apex.scaffold.office.off.application.assembler;

import com.apex.scaffold.office.off.application.dto.MessageDTO;
import com.apex.scaffold.office.off.application.dto.SendMessageRequest;
import com.apex.scaffold.office.off.domain.entity.OffMessageEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 办公消息装配器。
 *
 * <p>基于 MapStruct 实现 {@code OffMessageEntity} 与 {@code MessageDTO} 之间的双向转换
 * （含列表批量转换），并将 {@code SendMessageRequest} 请求转换为消息实体，
 * 供 {@code OffMessageService} 在消息发送与查询用例编排中完成对象映射。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OffMessageAssembler {

    /**
     *
     *
     * 将消息实体转换为DTO。
     *
     *
     *
     *
     *
     * @param entity 消息实体
     *
     *
     * @return 消息DTO
     *
     *
     */
    MessageDTO toDTO(OffMessageEntity entity);

    /**
     *
     *
     * 将消息实体列表转换为DTO列表。
     *
     *
     *
     *
     *
     * @param entities 消息实体列表
     *
     *
     * @return 消息DTO列表
     *
     *
     */
    List<MessageDTO> toDTOList(List<OffMessageEntity> entities);

    /**
     * 将发送消息请求转换为消息实体。
     *
     * @param request 发送消息请求
     * @return 消息实体
     */
    OffMessageEntity toEntity(SendMessageRequest request);
}
