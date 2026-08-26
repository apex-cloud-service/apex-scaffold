package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.RolePermissionEntity;
import com.apex.scaffold.infrastructure.persistence.po.RolePermissionPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link RolePermissionEntity} 与 {@link RolePermissionPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RolePermissionPoConverter {

    /**
     * 将角色权限实体转换为PO
     *
     * @param entity 角色权限实体
     * @return 角色权限PO
     */
    RolePermissionPO toPo(RolePermissionEntity entity);

    /**
     * 将角色权限PO转换为实体
     *
     * @param po 角色权限PO
     * @return 角色权限实体
     */
    RolePermissionEntity toEntity(RolePermissionPO po);

    /**
     * 将角色权限实体列表转换为PO列表
     *
     * @param entities 角色权限实体列表
     * @return 角色权限PO列表
     */
    List<RolePermissionPO> toPoList(List<RolePermissionEntity> entities);

    /**
     * 将角色权限PO列表转换为实体列表
     *
     * @param pos 角色权限PO列表
     * @return 角色权限实体列表
     */
    List<RolePermissionEntity> toEntityList(List<RolePermissionPO> pos);
}
