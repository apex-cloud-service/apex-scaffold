package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.MenuEntity;
import com.apex.scaffold.infrastructure.persistence.po.MenuPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link MenuEntity} 与 {@link MenuPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MenuPoConverter {

    /**
     * 将菜单实体转换为持久化对象。
     *
     * @param entity 菜单实体
     * @return 菜单持久化对象
     */
    MenuPO toPo(MenuEntity entity);

    /**
     * 将菜单持久化对象转换为实体。
     *
     * @param po 菜单持久化对象
     * @return 菜单实体
     */
    MenuEntity toEntity(MenuPO po);

    /**
     * 将菜单实体列表转换为持久化对象列表。
     *
     * @param entities 菜单实体列表
     * @return 菜单持久化对象列表
     */
    List<MenuPO> toPoList(List<MenuEntity> entities);

    /**
     * 将菜单持久化对象列表转换为实体列表。
     *
     * @param pos 菜单持久化对象列表
     * @return 菜单实体列表
     */
    List<MenuEntity> toEntityList(List<MenuPO> pos);
}
