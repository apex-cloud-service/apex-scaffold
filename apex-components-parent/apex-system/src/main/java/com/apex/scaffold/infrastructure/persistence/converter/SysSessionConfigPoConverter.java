package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.SysSessionConfigEntity;
import com.apex.scaffold.infrastructure.persistence.po.SysSessionConfigPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 会话配置实体与持久化对象转换器
 *
 * @author Apex Scaffold
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysSessionConfigPoConverter {

    /**
     * 对象转换方法
     *
     * @param entity 领域实体
     * @return 目标对象
     */
    SysSessionConfigPO toPo(SysSessionConfigEntity entity);

    /**
     * 对象转换方法
     *
     * @param po 持久化对象
     * @return 目标对象
     */
    SysSessionConfigEntity toEntity(SysSessionConfigPO po);
}
