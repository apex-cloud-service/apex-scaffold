package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.SyncHistoryDTO;
import com.apex.scaffold.domain.model.entity.OffSyncHistoryEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 同步历史装配器。
 *
 * <p>基于 MapStruct 实现 {@code OffSyncHistoryEntity} 与 {@code SyncHistoryDTO}
 * 之间的双向转换，供 {@code OrgSyncService} 在记录与查询组织同步历史用例编排中完成对象映射。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SyncHistoryAssembler {

    SyncHistoryDTO toDto(OffSyncHistoryEntity entity);

    OffSyncHistoryEntity toEntity(SyncHistoryDTO dto);
}
