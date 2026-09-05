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

    WorkflowDefinitionPO toPo(WorkflowDefinition entity);

    WorkflowDefinition toEntity(WorkflowDefinitionPO po);

    List<WorkflowDefinitionPO> toPoList(List<WorkflowDefinition> entities);

    List<WorkflowDefinition> toEntityList(List<WorkflowDefinitionPO> pos);
}
