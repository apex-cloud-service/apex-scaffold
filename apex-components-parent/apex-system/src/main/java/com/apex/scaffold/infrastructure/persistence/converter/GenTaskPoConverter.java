package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.generator.GenTaskEntity;
import com.apex.scaffold.infrastructure.persistence.po.GenTaskPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link GenTaskEntity} 与 {@link GenTaskPO} 之间的 MapStruct 双向转换器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.5.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface GenTaskPoConverter {

    /**
     * 将任务实体转换为持久化对象。
     *
     * @param entity 任务实体
     * @return 任务持久化对象
     */
    GenTaskPO toPo(GenTaskEntity entity);

    /**
     * 将任务持久化对象转换为实体。
     *
     * @param po 任务持久化对象
     * @return 任务实体
     */
    GenTaskEntity toEntity(GenTaskPO po);

    /**
     * 将任务实体列表转换为持久化对象列表。
     *
     * @param entities 任务实体列表
     * @return 任务持久化对象列表
     */
    List<GenTaskPO> toPoList(List<GenTaskEntity> entities);

    /**
     * 将任务持久化对象列表转换为实体列表。
     *
     * @param pos 任务持久化对象列表
     * @return 任务实体列表
     */
    List<GenTaskEntity> toEntityList(List<GenTaskPO> pos);
}
