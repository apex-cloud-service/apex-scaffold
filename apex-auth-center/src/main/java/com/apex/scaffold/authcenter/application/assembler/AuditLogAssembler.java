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
 * <p>说明（2026-09-30）：本接口不再显式指定 {@code implementationName}。此前为规避
 * {@code apex-auth-center} 与 {@code apex-application} 组件扫描交叉可能产生的
 * MapStruct 生成类同名冲突，曾临时加上 {@code implementationName = "AuthCenterAuditLogAssemblerImpl"}
 * 过渡性属性；随着 {@code apex-interfaces} 主应用已通过 {@code @ComponentScan} 排除
 * {@code authcenter} 包（见 {@code Application.java}），两个应用的 Bean/生成类互不干扰，
 * 该过渡性属性已回退，恢复 MapStruct 默认命名规则（{@code AuditLogAssemblerImpl}）。
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
