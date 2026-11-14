package com.apex.scaffold.workflow.infrastructure.persistence.converter;

import com.apex.scaffold.workflow.domain.model.ProcessInstance;
import com.apex.scaffold.workflow.infrastructure.persistence.po.ProcessInstancePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 流程实例 PO 转换器。
 *
 * <p>负责流程实例领域对象 {@link ProcessInstance} 与持久化对象
 * {@link ProcessInstancePO} 之间的双向转换，使用 MapStruct 在编译期生成实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProcessInstancePoConverter {

    /**
     * 将流程实例领域对象转换为持久化对象。
     *
     * @param entity 流程实例领域对象
     * @return 流程实例持久化对象
     */
    @Mapping(source = "contextJson", target = "formData")
    @Mapping(source = "currentNodeKey", target = "currentNodeCode")
    ProcessInstancePO toPo(ProcessInstance entity);

    /**
     * 将流程实例持久化对象转换为领域对象。
     *
     * @param po 流程实例持久化对象
     * @return 流程实例领域对象
     */
    @Mapping(source = "formData", target = "contextJson")
    @Mapping(source = "currentNodeCode", target = "currentNodeKey")
    ProcessInstance toEntity(ProcessInstancePO po);

    /**
     * 批量将流程实例领域对象转换为持久化对象。
     *
     * @param entities 流程实例领域对象列表
     * @return 流程实例持久化对象列表
     */
    List<ProcessInstancePO> toPoList(List<ProcessInstance> entities);

    /**
     * 批量将流程实例持久化对象转换为领域对象。
     *
     * @param pos 流程实例持久化对象列表
     * @return 流程实例领域对象列表
     */
    List<ProcessInstance> toEntityList(List<ProcessInstancePO> pos);
}
