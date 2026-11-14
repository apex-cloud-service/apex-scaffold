package com.apex.scaffold.workflow.infrastructure.persistence.converter;

import com.apex.scaffold.workflow.domain.model.WorkflowDefinition;
import com.apex.scaffold.workflow.infrastructure.persistence.po.WorkflowDefinitionPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 工作流定义 PO 转换器。
 *
 * <p>负责工作流定义领域对象 {@link WorkflowDefinition} 与持久化对象
 * {@link WorkflowDefinitionPO} 之间的双向转换，使用 MapStruct 在编译期生成实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface WorkflowDefinitionPoConverter {

    /**
     * 将工作流定义领域对象转换为持久化对象。
     *
     * @param entity 工作流定义领域对象
     * @return 工作流定义持久化对象
     */
    @Mapping(source = "dagJson", target = "nodeDefinition")
    WorkflowDefinitionPO toPo(WorkflowDefinition entity);

    /**
     * 将工作流定义持久化对象转换为领域对象。
     *
     * @param po 工作流定义持久化对象
     * @return 工作流定义领域对象
     */
    @Mapping(source = "nodeDefinition", target = "dagJson")
    WorkflowDefinition toEntity(WorkflowDefinitionPO po);

    /**
     * 批量将工作流定义领域对象转换为持久化对象。
     *
     * @param entities 工作流定义领域对象列表
     * @return 工作流定义持久化对象列表
     */
    List<WorkflowDefinitionPO> toPoList(List<WorkflowDefinition> entities);

    /**
     * 批量将工作流定义持久化对象转换为领域对象。
     *
     * @param pos 工作流定义持久化对象列表
     * @return 工作流定义领域对象列表
     */
    List<WorkflowDefinition> toEntityList(List<WorkflowDefinitionPO> pos);
}
