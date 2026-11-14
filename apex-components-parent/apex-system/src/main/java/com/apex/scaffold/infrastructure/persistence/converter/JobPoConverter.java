package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.JobEntity;
import com.apex.scaffold.infrastructure.persistence.po.JobPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link JobEntity} 与 {@link JobPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface JobPoConverter {

    /**
     * 将任务实体转换为持久化对象。
     *
     * @param entity 任务实体
     * @return 任务持久化对象
     */
    JobPO toPo(JobEntity entity);

    /**
     * 将任务持久化对象转换为实体。
     *
     * @param po 任务持久化对象
     * @return 任务实体
     */
    JobEntity toEntity(JobPO po);

    /**
     * 将任务实体列表转换为持久化对象列表。
     *
     * @param entities 任务实体列表
     * @return 任务持久化对象列表
     */
    List<JobPO> toPoList(List<JobEntity> entities);

    /**
     * 将任务持久化对象列表转换为实体列表。
     *
     * @param pos 任务持久化对象列表
     * @return 任务实体列表
     */
    List<JobEntity> toEntityList(List<JobPO> pos);
}
