package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.DeptDTO;
import com.apex.scaffold.domain.model.entity.DeptEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 部门对象转换器
 *
 * <p>用于在部门相关的 DTO 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DeptAssembler {
    /**
     * 将 DeptEntity 转换为 DeptDTO
     *
     * @param entity 部门实体
     * @return 部门 DTO
     */
    DeptDTO toDTO(DeptEntity entity);

    /**
     * 将 DeptEntity 列表转换为 DeptDTO 列表
     *
     * @param entities 部门实体列表
     * @return 部门 DTO 列表
     */
    List<DeptDTO> toDTOList(List<DeptEntity> entities);
}
