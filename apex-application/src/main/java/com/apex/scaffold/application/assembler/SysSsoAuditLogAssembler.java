package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.SsoAuditLogDTO;
import com.apex.scaffold.domain.model.entity.SysSsoAuditLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * SSO 审计日志装配器。
 *
 * <p>基于 MapStruct 实现 {@code SysSsoAuditLogEntity} 到 {@code SsoAuditLogDTO}
 * 的单向转换（含列表批量转换），供 {@code SysSsoAuditLogService}
 * 在 SSO 审计日志查询用例中完成对象映射。
 *
 * @author Apex Team
 * @version 1.2.0
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface SysSsoAuditLogAssembler {
    /**
     *
     * 将SSO审计日志实体转换为DTO。
     *
     *
     *
     * @param entity SSO审计日志实体
     *
     * @return SSO审计日志DTO
     *
     */
    SsoAuditLogDTO toDTO(SysSsoAuditLogEntity entity);

    /**
     *
     *
     * 将SSO审计日志实体列表转换为DTO列表。
     *
     *
     *
     *
     *
     * @param entities SSO审计日志实体列表
     *
     *
     * @return SSO审计日志DTO列表
     *
     *
     */
    List<SsoAuditLogDTO> toDTOList(List<SysSsoAuditLogEntity> entities);
}
