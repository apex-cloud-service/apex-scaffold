package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.ChartEntity;
import com.apex.scaffold.infrastructure.persistence.po.ChartPO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ChartPoConverter {

    ChartEntity toEntity(ChartPO po);

    ChartPO toPo(ChartEntity entity);
}
