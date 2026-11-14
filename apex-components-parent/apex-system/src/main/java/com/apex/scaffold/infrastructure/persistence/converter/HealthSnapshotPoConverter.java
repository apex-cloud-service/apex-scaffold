package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.monitor.HealthSnapshotEntity;
import com.apex.scaffold.infrastructure.persistence.po.HealthSnapshotPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link HealthSnapshotEntity} 与 {@link HealthSnapshotPO} 之间的 MapStruct 双向转换器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface HealthSnapshotPoConverter {

    /**
     * 实体转持久化对象。
     *
     * @param entity 快照实体
     * @return 快照持久化对象
     */
    HealthSnapshotPO toPo(HealthSnapshotEntity entity);

    /**
     * 持久化对象转实体。
     *
     * @param po 快照持久化对象
     * @return 快照实体
     */
    HealthSnapshotEntity toEntity(HealthSnapshotPO po);

    /**
     * 持久化对象列表转实体列表。
     *
     * @param pos 快照持久化对象列表
     * @return 快照实体列表
     */
    List<HealthSnapshotEntity> toEntityList(List<HealthSnapshotPO> pos);
}
