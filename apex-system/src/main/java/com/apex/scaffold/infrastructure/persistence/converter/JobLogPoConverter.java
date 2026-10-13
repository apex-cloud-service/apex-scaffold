package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.entity.JobLogEntity;
import com.apex.scaffold.infrastructure.persistence.po.JobLogPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * {@link JobLogEntity} 与 {@link JobLogPO} 之间的 MapStruct 双向转换器。
 *
 * <p>使用 MapStruct（componentModel = "spring"）完成领域实体与持久化对象
 * 之间的双向映射，忽略未匹配目标属性，供 Repository 实现层使用。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface JobLogPoConverter {

    /**
     * 将执行日志实体转换为持久化对象。
     *
     * @param entity 执行日志实体
     * @return 执行日志持久化对象
     */
    JobLogPO toPo(JobLogEntity entity);

    /**
     * 将执行日志持久化对象转换为实体。
     *
     * @param po 执行日志持久化对象
     * @return 执行日志实体
     */
    JobLogEntity toEntity(JobLogPO po);

    /**
     * 将执行日志实体列表转换为持久化对象列表。
     *
     * @param entities 执行日志实体列表
     * @return 执行日志持久化对象列表
     */
    List<JobLogPO> toPoList(List<JobLogEntity> entities);

    /**
     * 将执行日志持久化对象列表转换为实体列表。
     *
     * @param pos 执行日志持久化对象列表
     * @return 执行日志实体列表
     */
    List<JobLogEntity> toEntityList(List<JobLogPO> pos);
}
