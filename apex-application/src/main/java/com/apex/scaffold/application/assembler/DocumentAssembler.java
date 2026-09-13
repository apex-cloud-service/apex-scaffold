package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.data.DocumentResponse;
import com.apex.scaffold.domain.model.entity.DocumentEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 文档实体与响应DTO转换器。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DocumentAssembler {

    /**
     * 将文档实体转换为响应DTO。
     *
     * @param entity 文档实体
     * @return 文档响应DTO
     */
    DocumentResponse toResponse(DocumentEntity entity);

    /**
     * 将文档实体列表转换为响应DTO列表。
     *
     * @param entities 文档实体列表
     * @return 文档响应DTO列表
     */
    List<DocumentResponse> toResponseList(List<DocumentEntity> entities);
}
