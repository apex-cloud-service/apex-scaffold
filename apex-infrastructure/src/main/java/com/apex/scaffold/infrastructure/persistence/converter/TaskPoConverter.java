package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.workflow.Task;
import com.apex.scaffold.infrastructure.persistence.po.TaskPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 任务 PO 转换器。
 *
 * <p>负责任务领域对象与持久化对象之间的双向转换，
 * 使用 MapStruct 自动生成转换实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface TaskPoConverter {

    /**
     * TODO: Add method description
     *
     * @param entity TODO
     * @return TODO
     */
    TaskPO toPo(Task entity);

    /**
     * TODO: Add method description
     *
     * @param po TODO
     * @return TODO
     */
    Task toEntity(TaskPO po);

    /**
     * TODO: Add method description
     *
     * @param entities TODO
     * @return TODO
     */
    List<TaskPO> toPoList(List<Task> entities);

    /**
     * TODO: Add method description
     *
     * @param pos TODO
     * @return TODO
     */
    List<Task> toEntityList(List<TaskPO> pos);
}
