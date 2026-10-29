package com.apex.scaffold.auth.infrastructure.persistence.converter;

import com.apex.scaffold.auth.domain.model.entity.RoleEntity;
import com.apex.scaffold.auth.infrastructure.persistence.po.RolePO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 角色实体与持久化对象转换器
 *
 * <p>负责RoleEntity与RolePO之间的双向转换，使用MapStruct自动生成实现代码。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.1
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = NumericFlagConverter.class)
public interface RoleConverter {

    /**
     * 实体转持久化对象
     *
     * @param entity 角色实体
     * @return 角色持久化对象
     */
    RolePO toPo(RoleEntity entity);

    /**
     * 持久化对象转实体
     *
     * @param po 角色持久化对象
     * @return 角色实体
     */
    RoleEntity toEntity(RolePO po);
}
