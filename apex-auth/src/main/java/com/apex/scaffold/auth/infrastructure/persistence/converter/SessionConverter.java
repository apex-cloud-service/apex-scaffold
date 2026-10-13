package com.apex.scaffold.auth.infrastructure.persistence.converter;

import com.apex.scaffold.auth.domain.model.entity.SessionEntity;
import com.apex.scaffold.auth.infrastructure.persistence.po.SessionPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 认证领域与持久化对象转换。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = NumericFlagConverter.class)
public interface SessionConverter {
    /**
     * 将领域对象转换为数据库记录。
     * @param entity 领域对象
     * @return 持久化对象
     */
    SessionPO toPo(SessionEntity entity);

    /**
     * 将数据库记录还原为领域对象。
     * @param po 持久化对象
     * @return 领域对象
     */
    SessionEntity toEntity(SessionPO po);
}
