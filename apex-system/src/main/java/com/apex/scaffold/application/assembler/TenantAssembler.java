package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.TenantDTO;
import com.apex.scaffold.domain.model.entity.TenantEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 租户对象转换器
 *
 * <p>用于在租户相关的 DTO 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TenantAssembler {
    /**
     * 将 TenantEntity 转换为 TenantDTO
     *
     * @param entity 租户实体
     * @return 租户 DTO
     */
    @Mapping(target = "version", source = "lockVersion")
    TenantDTO toDTO(TenantEntity entity);

    /**
     * 将 TenantEntity 列表转换为 TenantDTO 列表
     *
     * @param entities 租户实体列表
     * @return 租户 DTO 列表
     */
    List<TenantDTO> toDTOList(List<TenantEntity> entities);
}
