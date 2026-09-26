package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.ApprovalProcessDTO;
import com.apex.scaffold.domain.model.entity.OfficeApprovalProcessEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 审批流程装配器。
 *
 * <p>基于 MapStruct 实现 {@code OfficeApprovalProcessEntity} 与 {@code ApprovalProcessDTO}
 * 之间的双向转换，供 {@code OfficeIntegrationService} 在审批流程管理用例编排中完成对象映射。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        implementationName = "ApprovalProcessDtoAssemblerImpl")
public interface ApprovalProcessAssembler {

    ApprovalProcessDTO toDto(OfficeApprovalProcessEntity entity);

    OfficeApprovalProcessEntity toEntity(ApprovalProcessDTO dto);
}
