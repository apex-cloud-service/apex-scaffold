package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.DepartmentDTO;
import com.apex.scaffold.domain.model.entity.OffDepartmentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 部门装配器。
 *
 * <p>基于 MapStruct 实现 {@code OffDepartmentEntity} 与 {@code DepartmentDTO}
 * 之间的双向转换，供 {@code OrgSyncService} 在组织架构同步用例编排中完成对象映射。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DepartmentAssembler {

    /**
     *
     *
     * 将部门实体转换为DTO。
     *
     *
     *
     *
     *
     * @param entity 部门实体
     *
     *
     * @return 部门DTO
     *
     *
     */
    DepartmentDTO toDto(OffDepartmentEntity entity);

    /**
     *
     *
     * 将部门DTO转换为实体。
     *
     *
     *
     *
     *
     * @param dto 部门DTO
     *
     *
     * @return 部门实体
     *
     *
     */
    OffDepartmentEntity toEntity(DepartmentDTO dto);
}
