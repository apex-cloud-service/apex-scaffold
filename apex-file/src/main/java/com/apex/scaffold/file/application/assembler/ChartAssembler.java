package com.apex.scaffold.file.application.assembler;

import com.apex.scaffold.file.application.dto.data.ChartResponse;
import com.apex.scaffold.file.domain.model.entity.ChartEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 图表实体与响应DTO转换器。
 *
 * @author Apex Team
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ChartAssembler {

    /**
     * 将图表实体转换为响应DTO。
     *
     * @param entity 图表实体
     * @return 图表响应DTO
     */
    ChartResponse toResponse(ChartEntity entity);

    /**
     * 将图表实体列表转换为响应DTO列表。
     *
     * @param entities 图表实体列表
     * @return 图表响应DTO列表
     */
    List<ChartResponse> toResponseList(List<ChartEntity> entities);
}
