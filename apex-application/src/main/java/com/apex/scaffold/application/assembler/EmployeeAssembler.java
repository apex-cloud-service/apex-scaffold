package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.EmployeeDTO;
import com.apex.scaffold.domain.model.entity.OffEmployeeEntity;
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

    EmployeeDTO toDto(OffEmployeeEntity entity);

    OffEmployeeEntity toEntity(EmployeeDTO dto);
}
