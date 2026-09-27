package com.apex.scaffold.authcenter.application.assembler;

import com.apex.scaffold.authcenter.application.dto.AuditLogDTO;
import com.apex.scaffold.authcenter.domain.model.entity.AuditLogEntity;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;
import reactor.core.publisher.Mono;

/**
 * 审计日志Assembler
 *
 * <p>使用MapStruct实现AuditLogEntity与DTO之间的转换。
 *
 * @author Apex Scaffold Team
 * @since 1.2.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AuditLogAssembler {

    /**
     * 实体转DTO
     *
     * @param entity 审计日志实体
     * @return 审计日志DTO
     */
    AuditLogDTO toDto(AuditLogEntity entity);

    /**
     * DTO转实体
     *
     * @param dto 审计日志DTO
     * @return 审计日志实体
     */
    AuditLogEntity toEntity(AuditLogDTO dto);

    /**
     * 实体转DTO（响应式）
     *
     * @param entity 审计日志实体
     * @return 审计日志DTO的Mono包装
     */
    default Mono<AuditLogDTO> toDto(Mono<AuditLogEntity> entity) {
        return entity.map(this::toDto);
    }
}
