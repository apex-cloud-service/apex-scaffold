package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.RoleDTO;
import com.apex.scaffold.application.dto.RoleRequest;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import java.util.List;
import org.mapstruct.ReportingPolicy;

/**
 * 角色对象转换器
 *
 * <p>用于在角色相关的 DTO/Request 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。约定遵循 {@link CrudAssembler} 泛型接口规范
 * （{@code toDTO / toEntity / fromRequest / toDTOList}）。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@org.mapstruct.Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RoleAssembler {
    /**
     * 将 RoleEntity 转换为 RoleDTO
     *
     * @param entity 角色实体
     * @return 角色 DTO
     */
    RoleDTO toDTO(RoleEntity entity);

    /**
     * 将 RoleDTO 转换为 RoleEntity
     *
     * @param dto 角色 DTO
     * @return 角色实体
     */
    RoleEntity toEntity(RoleDTO dto);

    /**
     * 将 RoleRequest 转换为 RoleEntity
     *
     * @param request 角色请求
     * @return 角色实体
     */
    RoleEntity fromRequest(RoleRequest request);

    /**
     * 将 RoleEntity 列表转换为 RoleDTO 列表
     *
     * @param entities 角色实体列表
     * @return 角色 DTO 列表
     */
    List<RoleDTO> toDTOList(List<RoleEntity> entities);
}
