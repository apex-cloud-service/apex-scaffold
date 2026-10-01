package com.apex.scaffold.infrastructure.persistence.converter;

import com.apex.scaffold.domain.model.workflow.ApprovalRecord;
import com.apex.scaffold.infrastructure.persistence.po.ApprovalRecordPO;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 审批记录 PO 转换器。
 *
 * <p>负责审批记录领域对象与持久化对象之间的双向转换，
 * 使用 MapStruct 自动生成转换实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApprovalRecordPoConverter {

    /**
     * TODO: Add method description
     *
     * @param entity TODO
     * @return TODO
     */
    ApprovalRecordPO toPo(ApprovalRecord entity);

    /**
     * TODO: Add method description
     *
     * @param po TODO
     * @return TODO
     */
    ApprovalRecord toEntity(ApprovalRecordPO po);

    /**
     * TODO: Add method description
     *
     * @param entities TODO
     * @return TODO
     */
    List<ApprovalRecordPO> toPoList(List<ApprovalRecord> entities);

    /**
     * TODO: Add method description
     *
     * @param pos TODO
     * @return TODO
     */
    List<ApprovalRecord> toEntityList(List<ApprovalRecordPO> pos);
}
