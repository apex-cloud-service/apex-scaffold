package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.UserRoleEntity;
import com.apex.scaffold.infrastructure.persistence.po.UserRolePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link UserRoleEntity} 与 {@link UserRolePO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserRolePoConverter {

    /**
     * 将用户角色实体转换为PO
     *
     * @param entity 用户角色实体
     * @return 用户角色PO
     */
    UserRolePO toPo(UserRoleEntity entity);

    /**
     * 将用户角色PO转换为实体
     *
     * @param po 用户角色PO
     * @return 用户角色实体
     */
    UserRoleEntity toEntity(UserRolePO po);

    /**
     * 将用户角色实体列表转换为PO列表
     *
     * @param entities 用户角色实体列表
     * @return 用户角色PO列表
     */
    List<UserRolePO> toPoList(List<UserRoleEntity> entities);

    /**
     * 将用户角色PO列表转换为实体列表
     *
     * @param pos 用户角色PO列表
     * @return 用户角色实体列表
     */
    List<UserRoleEntity> toEntityList(List<UserRolePO> pos);
}
