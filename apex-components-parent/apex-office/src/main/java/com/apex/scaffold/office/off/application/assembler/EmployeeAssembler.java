package com.apex.scaffold.office.off.application.assembler;

import com.apex.scaffold.office.off.application.dto.EmployeeDTO;
import com.apex.scaffold.office.off.domain.entity.OffEmployeeEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 员工装配器。
 *
 * <p>基于 MapStruct 实现 {@code OffEmployeeEntity} 与 {@code EmployeeDTO}
 * 之间的双向转换，供 {@code OrgSyncService} 在组织架构同步用例编排中完成对象映射。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EmployeeAssembler {

    /**
     *
     *
     * 将员工实体转换为DTO。
     *
     *
     *
     *
     *
     * @param entity 员工实体
     *
     *
     * @return 员工DTO
     *
     *
     */
    EmployeeDTO toDto(OffEmployeeEntity entity);

    /**
     *
     *
     * 将员工DTO转换为实体。
     *
     *
     *
     *
     *
     * @param dto 员工DTO
     *
     *
     * @return 员工实体
     *
     *
     */
    OffEmployeeEntity toEntity(EmployeeDTO dto);
}
