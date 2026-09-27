package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.OfficeApprovalProcessEntity;
import com.apex.scaffold.infrastructure.persistence.po.OfficeApprovalProcessPO;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 审批流程实体与持久化对象转换器
 *
 * @author Apex Scaffold
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OfficeApprovalProcessPoConverter {

    /**
     * 对象转换方法
     *
     * @param po 持久化对象
     * @return 目标对象
     */
    OfficeApprovalProcessEntity toDomain(OfficeApprovalProcessPO po);

    /**
     * 对象转换方法
     *
     * @param entity 领域实体
     * @return 目标对象
     */
    OfficeApprovalProcessPO toPo(OfficeApprovalProcessEntity entity);
}
