package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.common.enums.DataScopeType;
import com.apex.scaffold.domain.model.entity.RoleEntity;
import com.apex.scaffold.infrastructure.persistence.po.RolePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link RoleEntity} 与 {@link RolePO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface RolePoConverter {

    /**
     * 将角色实体转换为PO
     *
     * @param entity 角色实体
     * @return 角色PO
     */
    @org.mapstruct.Mapping(target = "dataScope", source = "dataScope")
    RolePO toPo(RoleEntity entity);

    /** 将数据权限类型转换为对应编码。 */
    default Integer map(DataScopeType type) {
        return type == null ? null : type.getCode();
    }

    /** 将编码转换为数据权限类型，不支持的编码抛出 {@link IllegalArgumentException}。 */
    default DataScopeType map(Integer code) {
        if (code == null) {
            return null;
        }
        DataScopeType type = DataScopeType.fromCode(code);
        if (type == null) {
            throw new IllegalArgumentException("Unsupported data scope code: " + code);
        }
        return type;
    }

    /**
     * 将角色PO转换为实体
     *
     * @param po 角色PO
     * @return 角色实体
     */
    RoleEntity toEntity(RolePO po);

    /**
     * 将角色实体列表转换为PO列表
     *
     * @param entities 角色实体列表
     * @return 角色PO列表
     */
    List<RolePO> toPoList(List<RoleEntity> entities);

    /**
     * 将角色PO列表转换为实体列表
     *
     * @param pos 角色PO列表
     * @return 角色实体列表
     */
    List<RoleEntity> toEntityList(List<RolePO> pos);
}
