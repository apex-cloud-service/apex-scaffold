package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.OffSyncHistoryEntity;
import com.apex.scaffold.infrastructure.persistence.po.OffSyncHistoryPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 同步历史PO转换器
 * @since 1.2.0
 *
 * @author Apex Team
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OffSyncHistoryPoConverter {

    /**
     * 对象转换方法
     *
     * @param po 持久化对象
     * @return 目标对象
     */
    OffSyncHistoryEntity toDomain(OffSyncHistoryPO po);

    /**
     * 对象转换方法
     *
     * @param entity 领域实体
     * @return 目标对象
     */
    OffSyncHistoryPO toPo(OffSyncHistoryEntity entity);
}
