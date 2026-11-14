package com.apex.scaffold.workflow.infrastructure.persistence.converter;

import com.apex.scaffold.workflow.domain.model.ApprovalRecord;
import com.apex.scaffold.workflow.infrastructure.persistence.po.ApprovalRecordPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 审批记录 PO 转换器。
 *
 * <p>负责审批记录领域对象 {@link ApprovalRecord} 与持久化对象
 * {@link ApprovalRecordPO} 之间的双向转换，使用 MapStruct 在编译期生成实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApprovalRecordPoConverter {

    /**
     * 将审批记录领域对象转换为持久化对象。
     *
     * @param entity 审批记录领域对象
     * @return 审批记录持久化对象
     */
    ApprovalRecordPO toPo(ApprovalRecord entity);

    /**
     * 将审批记录持久化对象转换为领域对象。
     *
     * @param po 审批记录持久化对象
     * @return 审批记录领域对象
     */
    ApprovalRecord toEntity(ApprovalRecordPO po);

    /**
     * 批量将审批记录领域对象转换为持久化对象。
     *
     * @param entities 审批记录领域对象列表
     * @return 审批记录持久化对象列表
     */
    List<ApprovalRecordPO> toPoList(List<ApprovalRecord> entities);

    /**
     * 批量将审批记录持久化对象转换为领域对象。
     *
     * @param pos 审批记录持久化对象列表
     * @return 审批记录领域对象列表
     */
    List<ApprovalRecord> toEntityList(List<ApprovalRecordPO> pos);
}
