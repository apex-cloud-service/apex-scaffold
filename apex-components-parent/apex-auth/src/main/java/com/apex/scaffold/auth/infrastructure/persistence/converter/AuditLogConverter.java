package com.apex.scaffold.auth.infrastructure.persistence.converter;

import com.apex.scaffold.auth.domain.model.entity.AuditLogEntity;
import com.apex.scaffold.auth.infrastructure.persistence.po.AuditLogPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 认证领域与持久化对象转换。
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuditLogConverter {
    /**
     * 将领域对象转换为数据库记录。
     * @param entity 领域对象
     * @return 持久化对象
     */
    AuditLogPO toPo(AuditLogEntity entity);

    /**
     * 将数据库记录还原为领域对象。
     * @param po 持久化对象
     * @return 领域对象
     */
    AuditLogEntity toEntity(AuditLogPO po);
}
