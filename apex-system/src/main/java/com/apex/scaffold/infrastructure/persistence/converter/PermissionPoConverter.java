package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.PermissionEntity;
import com.apex.scaffold.infrastructure.persistence.po.PermissionPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link PermissionEntity} 与 {@link PermissionPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PermissionPoConverter {

    /**
     * 将权限实体转换为PO
     *
     * @param entity 权限实体
     * @return 权限PO
     */
    PermissionPO toPo(PermissionEntity entity);

    /**
     * 将权限PO转换为实体
     *
     * @param po 权限PO
     * @return 权限实体
     */
    PermissionEntity toEntity(PermissionPO po);

    /**
     * 将权限实体列表转换为PO列表
     *
     * @param entities 权限实体列表
     * @return 权限PO列表
     */
    List<PermissionPO> toPoList(List<PermissionEntity> entities);

    /**
     * 将权限PO列表转换为实体列表
     *
     * @param pos 权限PO列表
     * @return 权限实体列表
     */
    List<PermissionEntity> toEntityList(List<PermissionPO> pos);
}
