package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.MessageTemplateDTO;
import com.apex.scaffold.domain.model.entity.OffMessageTemplateEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 办公消息模板装配器。
 *
 * <p>基于 MapStruct 实现 {@code OffMessageTemplateEntity} 与 {@code MessageTemplateDTO}
 * 之间的双向转换（含列表批量转换），供 {@code OffMessageTemplateService}
 * 在消息模板管理用例编排中完成对象映射。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OffMessageTemplateAssembler {

    /**
     *
     *
     * 将消息模板实体转换为DTO。
     *
     *
     *
     *
     *
     * @param entity 消息模板实体
     *
     *
     * @return 消息模板DTO
     *
     *
     */
    MessageTemplateDTO toDTO(OffMessageTemplateEntity entity);

    /**
     *
     *
     * 将消息模板实体列表转换为DTO列表。
     *
     *
     *
     *
     *
     * @param entities 消息模板实体列表
     *
     *
     * @return 消息模板DTO列表
     *
     *
     */
    List<MessageTemplateDTO> toDTOList(List<OffMessageTemplateEntity> entities);

    /**
     *
     *
     * 将消息模板DTO转换为实体。
     *
     *
     *
     *
     *
     * @param dto 消息模板DTO
     *
     *
     * @return 消息模板实体
     *
     *
     */
    OffMessageTemplateEntity toEntity(MessageTemplateDTO dto);
}
