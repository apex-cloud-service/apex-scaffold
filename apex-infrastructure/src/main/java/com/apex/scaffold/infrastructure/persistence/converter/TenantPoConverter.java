package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.TenantEntity;
import com.apex.scaffold.domain.model.valueobject.TenantQuota;
import com.apex.scaffold.infrastructure.persistence.po.TenantPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * {@link TenantEntity} 与 {@link TenantPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象的双向映射。
 * 配额值对象 {@link TenantQuota} 在 PO 中拍平为 {@code maxUsers} / {@code maxStorageMb}
 * 两列，通过显式映射在两者间转换。租户状态枚举与字符串列通过枚举名自动转换。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.3.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TenantPoConverter {

    /**
     * 将租户实体转换为持久化对象。
     *
     * @param entity 租户实体
     * @return 租户持久化对象
     */
    @Mapping(target = "maxUsers", source = "quota.maxUsers")
    @Mapping(target = "maxStorageMb", source = "quota.maxStorageMb")
    TenantPO toPo(TenantEntity entity);

    /**
     * 将租户持久化对象转换为实体。
     *
     * @param po 租户持久化对象
     * @return 租户实体
     */
    @Mapping(
            target = "quota",
            expression = "java(com.apex.scaffold.domain.model.valueobject.TenantQuota.of("
                    + "po.getMaxUsers(), po.getMaxStorageMb()))")
    TenantEntity toEntity(TenantPO po);

    /**
     * 将租户实体列表转换为持久化对象列表。
     *
     * @param entities 租户实体列表
     * @return 租户持久化对象列表
     */
    List<TenantPO> toPoList(List<TenantEntity> entities);

    /**
     * 将租户持久化对象列表转换为实体列表。
     *
     * @param pos 租户持久化对象列表
     * @return 租户实体列表
     */
    List<TenantEntity> toEntityList(List<TenantPO> pos);
}
