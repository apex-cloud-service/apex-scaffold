package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.workflow.WorkflowDefinition;
import com.apex.scaffold.infrastructure.persistence.po.WorkflowDefinitionPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 工作流定义 PO 转换器。
 *
 * <p>负责工作流定义领域对象与持久化对象之间的双向转换，
 * 使用 MapStruct 自动生成转换实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WorkflowDefinitionPoConverter {

    /**
     * TODO: Add method description
     *
     * @param entity TODO
     * @return TODO
     */
    WorkflowDefinitionPO toPo(WorkflowDefinition entity);

    /**
     * TODO: Add method description
     *
     * @param po TODO
     * @return TODO
     */
    WorkflowDefinition toEntity(WorkflowDefinitionPO po);

    /**
     * TODO: Add method description
     *
     * @param entities TODO
     * @return TODO
     */
    List<WorkflowDefinitionPO> toPoList(List<WorkflowDefinition> entities);

    /**
     * TODO: Add method description
     *
     * @param pos TODO
     * @return TODO
     */
    List<WorkflowDefinition> toEntityList(List<WorkflowDefinitionPO> pos);
}
