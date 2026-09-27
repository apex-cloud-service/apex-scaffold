package com.apex.scaffold.authcenter.infrastructure.persistence.converter;

import com.apex.scaffold.authcenter.domain.model.entity.UserEntity;
import com.apex.scaffold.authcenter.infrastructure.persistence.po.UserPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 认证领域与持久化对象转换。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserConverter {
    /**
     * 将领域对象转换为数据库记录。
     * @param entity 领域对象
     * @return 持久化对象
     */
    UserPO toPo(UserEntity entity);

    /**
     * 将数据库记录还原为领域对象。
     * @param po 持久化对象
     * @return 领域对象
     */
    UserEntity toEntity(UserPO po);
}
