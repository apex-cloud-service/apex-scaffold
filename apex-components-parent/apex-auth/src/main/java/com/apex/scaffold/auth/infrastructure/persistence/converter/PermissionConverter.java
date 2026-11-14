package com.apex.scaffold.auth.infrastructure.persistence.converter;

import com.apex.scaffold.auth.domain.model.entity.PermissionEntity;
import com.apex.scaffold.auth.infrastructure.persistence.po.PermissionPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 权限实体与持久化对象转换器
 *
 * <p>负责PermissionEntity与PermissionPO之间的双向转换，使用MapStruct自动生成实现代码。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = NumericFlagConverter.class)
public interface PermissionConverter {

    /**
     * 实体转持久化对象
     *
     * @param entity 权限实体
     * @return 权限持久化对象
     */
    PermissionPO toPo(PermissionEntity entity);

    /**
     * 持久化对象转实体
     *
     * @param po 权限持久化对象
     * @return 权限实体
     */
    PermissionEntity toEntity(PermissionPO po);
}
