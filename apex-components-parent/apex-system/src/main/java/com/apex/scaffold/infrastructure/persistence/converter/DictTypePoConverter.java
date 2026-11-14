package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.DictTypeEntity;
import com.apex.scaffold.infrastructure.persistence.po.DictTypePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link DictTypeEntity} 与 {@link DictTypePO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface DictTypePoConverter {

    /**
     * 将字典类型实体转换为持久化对象。
     *
     * @param entity 字典类型实体
     * @return 字典类型持久化对象
     */
    DictTypePO toPo(DictTypeEntity entity);

    /**
     * 将字典类型持久化对象转换为实体。
     *
     * @param po 字典类型持久化对象
     * @return 字典类型实体
     */
    DictTypeEntity toEntity(DictTypePO po);

    /**
     * 将字典类型实体列表转换为持久化对象列表。
     *
     * @param entities 字典类型实体列表
     * @return 字典类型持久化对象列表
     */
    List<DictTypePO> toPoList(List<DictTypeEntity> entities);

    /**
     * 将字典类型持久化对象列表转换为实体列表。
     *
     * @param pos 字典类型持久化对象列表
     * @return 字典类型实体列表
     */
    List<DictTypeEntity> toEntityList(List<DictTypePO> pos);
}
