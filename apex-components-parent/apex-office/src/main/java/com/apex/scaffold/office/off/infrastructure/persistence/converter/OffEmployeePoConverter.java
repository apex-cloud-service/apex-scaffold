package com.apex.scaffold.office.off.infrastructure.persistence.converter;

import com.apex.scaffold.office.off.domain.entity.OffEmployeeEntity;
import com.apex.scaffold.office.off.infrastructure.persistence.po.OffEmployeePO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 员工PO转换器
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OffEmployeePoConverter {

    /**
     * 将PO转换为领域实体
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    OffEmployeeEntity toDomain(OffEmployeePO po);

    /**
     * 将领域实体转换为PO
     *
     * @param entity 领域实体
     * @return 持久化对象
     */
    OffEmployeePO toPo(OffEmployeeEntity entity);
}
