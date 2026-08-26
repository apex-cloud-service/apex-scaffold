package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.PermissionDTO;
import com.apex.scaffold.application.dto.PermissionRequest;
import com.apex.scaffold.domain.model.entity.PermissionEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 权限对象转换器
 *
 * <p>用于在权限相关的 DTO/Request 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。约定遵循 {@link CrudAssembler} 泛型接口规范
 * （{@code toDTO / toEntity / fromRequest / toDTOList}）。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionAssembler {
    /**
     * 将 PermissionEntity 转换为 PermissionDTO
     *
     * @param entity 权限实体
     * @return 权限 DTO
     */
    PermissionDTO toDTO(PermissionEntity entity);

    /**
     * 将 PermissionDTO 转换为 PermissionEntity
     *
     * @param dto 权限 DTO
     * @return 权限实体
     */
    PermissionEntity toEntity(PermissionDTO dto);

    /**
     * 将 PermissionRequest 转换为 PermissionEntity
     *
     * @param request 权限请求
     * @return 权限实体
     */
    PermissionEntity fromRequest(PermissionRequest request);

    /**
     * 将 PermissionEntity 列表转换为 PermissionDTO 列表
     *
     * @param entities 权限实体列表
     * @return 权限 DTO 列表
     */
    List<PermissionDTO> toDTOList(List<PermissionEntity> entities);
}
