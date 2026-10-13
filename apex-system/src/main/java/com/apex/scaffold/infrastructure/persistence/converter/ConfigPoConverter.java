package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.ConfigEntity;
import com.apex.scaffold.infrastructure.persistence.po.ConfigPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link ConfigEntity} 与 {@link ConfigPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ConfigPoConverter {

    /**
     * 将参数配置实体转换为持久化对象。
     *
     * @param entity 参数配置实体
     * @return 参数配置持久化对象
     */
    ConfigPO toPo(ConfigEntity entity);

    /**
     * 将参数配置持久化对象转换为实体。
     *
     * @param po 参数配置持久化对象
     * @return 参数配置实体
     */
    ConfigEntity toEntity(ConfigPO po);

    /**
     * 将参数配置实体列表转换为持久化对象列表。
     *
     * @param entities 参数配置实体列表
     * @return 参数配置持久化对象列表
     */
    List<ConfigPO> toPoList(List<ConfigEntity> entities);

    /**
     * 将参数配置持久化对象列表转换为实体列表。
     *
     * @param pos 参数配置持久化对象列表
     * @return 参数配置实体列表
     */
    List<ConfigEntity> toEntityList(List<ConfigPO> pos);
}
