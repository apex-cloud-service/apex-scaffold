package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.monitor.HealthExportTaskEntity;
import com.apex.scaffold.infrastructure.persistence.po.HealthExportTaskPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link HealthExportTaskEntity} 与 {@link HealthExportTaskPO} 之间的 MapStruct 双向转换器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.4.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface HealthExportTaskPoConverter {

    /**
     * 实体转持久化对象。
     *
     * @param entity 导出任务实体
     * @return 导出任务持久化对象
     */
    HealthExportTaskPO toPo(HealthExportTaskEntity entity);

    /**
     * 持久化对象转实体。
     *
     * @param po 导出任务持久化对象
     * @return 导出任务实体
     */
    HealthExportTaskEntity toEntity(HealthExportTaskPO po);

    /**
     * 持久化对象列表转实体列表。
     *
     * @param pos 导出任务持久化对象列表
     * @return 导出任务实体列表
     */
    List<HealthExportTaskEntity> toEntityList(List<HealthExportTaskPO> pos);
}
