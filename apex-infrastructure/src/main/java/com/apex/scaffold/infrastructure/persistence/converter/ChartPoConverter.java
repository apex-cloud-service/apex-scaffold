package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.ChartEntity;
import com.apex.scaffold.infrastructure.persistence.po.ChartPO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

/**
 * 图表持久化对象转换器。
 *
 * <p>基于 MapStruct 实现 {@code ChartPO} 与领域实体 {@code ChartEntity} 的双向转换，
 * 供 {@code ChartRepositoryImpl} 在领域仓储实现中完成 PO 与领域模型的对象映射。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ChartPoConverter {

    /** 将图表 PO 转换为领域实体。 */
    ChartEntity toEntity(ChartPO po);

    /** 将图表领域实体转换为 PO。 */
    ChartPO toPo(ChartEntity entity);
}
