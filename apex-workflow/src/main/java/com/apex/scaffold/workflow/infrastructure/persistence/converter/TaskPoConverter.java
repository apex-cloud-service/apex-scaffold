package com.apex.scaffold.workflow.infrastructure.persistence.converter;

import com.apex.scaffold.workflow.domain.model.Task;
import com.apex.scaffold.workflow.infrastructure.persistence.po.TaskPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 流程任务 PO 转换器。
 *
 * <p>负责流程任务领域对象 {@link Task} 与持久化对象 {@link TaskPO}
 * 之间的双向转换，使用 MapStruct 在编译期生成实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskPoConverter {

    /**
     * 将任务领域对象转换为持久化对象。
     *
     * @param entity 任务领域对象
     * @return 任务持久化对象
     */
    TaskPO toPo(Task entity);

    /**
     * 将任务持久化对象转换为领域对象。
     *
     * @param po 任务持久化对象
     * @return 任务领域对象
     */
    Task toEntity(TaskPO po);

    /**
     * 批量将任务领域对象转换为持久化对象。
     *
     * @param entities 任务领域对象列表
     * @return 任务持久化对象列表
     */
    List<TaskPO> toPoList(List<Task> entities);

    /**
     * 批量将任务持久化对象转换为领域对象。
     *
     * @param pos 任务持久化对象列表
     * @return 任务领域对象列表
     */
    List<Task> toEntityList(List<TaskPO> pos);
}