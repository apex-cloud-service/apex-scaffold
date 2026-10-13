package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.SsoProviderDTO;
import com.apex.scaffold.application.dto.admin.SsoProviderRequest;
import com.apex.scaffold.domain.model.entity.SysSsoProviderEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * SSO 提供商装配器。
 *
 * <p>基于 MapStruct 实现 {@code SsoProviderRequest} 到 {@code SysSsoProviderEntity} 的创建转换，
 * 以及 {@code SysSsoProviderEntity} 到 {@code SsoProviderDTO} 的查询响应转换（含列表批量转换），
 * 供 {@code SysSsoProviderService} 在 SSO 提供商管理用例编排中完成对象映射。
 *
 * <p>针对启用/禁用标志提供了 Boolean 与 Integer 的默认映射方法，完成 1/0 与布尔值的互转。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysSsoProviderAssembler {
    /**
     * 将SSO提供商请求转换为实体。
     *
     * @param request SSO提供商请求
     * @return SSO提供商实体
     */
    SysSsoProviderEntity toEntity(SsoProviderRequest request);

    /**
     *
     *
     * 将SSO提供商实体转换为DTO。
     *
     *
     *
     *
     *
     * @param entity SSO提供商实体
     *
     *
     * @return SSO提供商DTO
     *
     *
     */
    SsoProviderDTO toDTO(SysSsoProviderEntity entity);

    /**
     *
     *
     * 将SSO提供商实体列表转换为DTO列表。
     *
     *
     *
     *
     *
     * @param entities SSO提供商实体列表
     *
     *
     * @return SSO提供商DTO列表
     *
     *
     */
    List<SsoProviderDTO> toDTOList(List<SysSsoProviderEntity> entities);

    /**
     * 将布尔值映射为整数（true -> 1, false/null -> 0）。
     *
     * @param value 布尔值
     * @return 整数值（1 或 0）
     */
    default Integer map(Boolean value) {
        return value != null && value ? 1 : 0;
    }

    /**
     * 将整数映射为布尔值（1 -> true, 其他 -> false）。
     *
     * @param value 整数值
     * @return 布尔值
     */
    default Boolean map(Integer value) {
        return value != null && value == 1;
    }
}
