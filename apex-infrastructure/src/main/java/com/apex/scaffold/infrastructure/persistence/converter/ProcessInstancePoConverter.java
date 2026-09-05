package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.workflow.ProcessInstance;
import com.apex.scaffold.infrastructure.persistence.po.ProcessInstancePO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 流程实例 PO 转换器。
 *
 * <p>负责流程实例领域对象与持久化对象之间的双向转换，
 * 使用 MapStruct 自动生成转换实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProcessInstancePoConverter {

    ProcessInstancePO toPo(ProcessInstance entity);

    ProcessInstance toEntity(ProcessInstancePO po);

    List<ProcessInstancePO> toPoList(List<ProcessInstance> entities);

    List<ProcessInstance> toEntityList(List<ProcessInstancePO> pos);
}
