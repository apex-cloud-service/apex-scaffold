package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.OffDepartmentEntity;
import com.apex.scaffold.infrastructure.persistence.po.OffDepartmentPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 部门PO转换器
 *
 * @author Apex Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OffDepartmentPoConverter {

    /**
     * 将PO转换为领域实体
     *
     * @param po 持久化对象
     * @return 领域实体
     */
    OffDepartmentEntity toDomain(OffDepartmentPO po);

    /**
     * 将领域实体转换为PO
     *
     * @param entity 领域实体
     * @return 持久化对象
     */
    OffDepartmentPO toPo(OffDepartmentEntity entity);
}
