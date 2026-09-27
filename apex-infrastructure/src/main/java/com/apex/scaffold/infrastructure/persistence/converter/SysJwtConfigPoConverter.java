package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.SysJwtConfigEntity;
import com.apex.scaffold.infrastructure.persistence.po.SysJwtConfigPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * JWT配置实体与持久化对象转换器
 *
 * @author Apex Scaffold
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysJwtConfigPoConverter {

    /**
     * 对象转换方法
     *
     * @param entity 领域实体
     * @return 目标对象
     */
    SysJwtConfigPO toPo(SysJwtConfigEntity entity);

    /**
     * 对象转换方法
     *
     * @param po 持久化对象
     * @return 目标对象
     */
    SysJwtConfigEntity toEntity(SysJwtConfigPO po);
}
