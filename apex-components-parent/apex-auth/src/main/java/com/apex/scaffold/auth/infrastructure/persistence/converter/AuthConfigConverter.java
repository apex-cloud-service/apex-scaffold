package com.apex.scaffold.auth.infrastructure.persistence.converter;

import com.apex.scaffold.auth.domain.model.entity.AuthConfigEntity;
import com.apex.scaffold.auth.infrastructure.persistence.po.AuthConfigPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 认证配置领域模型与持久化对象的转换器。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuthConfigConverter {
    /**
     * 将领域配置转换为持久化对象。
     * @param entity 领域配置
     * @return 持久化对象
     */
    AuthConfigPO toPo(AuthConfigEntity entity);

    /**
     * 将持久化对象转换为领域配置。
     * @param po 持久化对象
     * @return 领域配置
     */
    AuthConfigEntity toEntity(AuthConfigPO po);
}
