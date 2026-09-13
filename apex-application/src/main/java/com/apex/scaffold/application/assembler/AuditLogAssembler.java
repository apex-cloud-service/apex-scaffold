package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.AuditLogDTO;
import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * 审计日志对象转换器
 *
 * <p>用于在审计日志相关的 DTO 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuditLogAssembler {
    /**
     * 将 OperationLogEntity 转换为 AuditLogDTO
     *
     * @param entity 操作日志实体
     * @return 审计日志 DTO
     */
    @Mapping(target = "method", source = "requestMethod")
    @Mapping(target = "requestUrl", source = "requestUri")
    @Mapping(target = "operatorId", source = "userId")
    @Mapping(target = "operatorName", source = "userName")
    @Mapping(target = "operatorIp", source = "ip")
    @Mapping(target = "duration", source = "executionTime")
    @Mapping(target = "createTime", source = "operationTime")
    AuditLogDTO toDTO(OperationLogEntity entity);

    /**
     * 将 OperationLogEntity 列表转换为 AuditLogDTO 列表
     *
     * @param entities 操作日志实体列表
     * @return 审计日志 DTO 列表
     */
    List<AuditLogDTO> toDTOList(List<OperationLogEntity> entities);
}
