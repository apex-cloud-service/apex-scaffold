package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.MenuDTO;
import com.apex.scaffold.application.dto.admin.MenuRequest;
import com.apex.scaffold.domain.model.entity.MenuEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 菜单对象转换器
 *
 * <p>用于在菜单相关的 DTO/Request 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MenuAssembler {
    /**
     * 将 MenuEntity 转换为 MenuDTO
     *
     * @param entity 菜单实体
     * @return 菜单 DTO
     */
    MenuDTO toDTO(MenuEntity entity);

    /**
     * 将 MenuRequest 转换为 MenuEntity
     *
     * @param request 菜单请求
     * @return 菜单实体
     */
    MenuEntity fromRequest(MenuRequest request);

    /**
     * 将 MenuEntity 列表转换为 MenuDTO 列表
     *
     * @param entities 菜单实体列表
     * @return 菜单 DTO 列表
     */
    List<MenuDTO> toDTOList(List<MenuEntity> entities);
}
