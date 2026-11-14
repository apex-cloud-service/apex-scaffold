package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.OperationLogDTO;
import com.apex.scaffold.domain.model.entity.OperationLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 操作日志对象转换器
 *
 * <p>用于在操作日志 DTO 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OperationLogAssembler {
    /**
     * 将操作日志实体转换为 DTO。
     *
     * @param entity 操作日志实体
     * @return 操作日志 DTO
     */
    OperationLogDTO toDTO(OperationLogEntity entity);

    /**
     * 将操作日志 DTO 转换为实体。
     *
     * @param dto 操作日志 DTO
     * @return 操作日志实体
     */
    OperationLogEntity toEntity(OperationLogDTO dto);

    /**
     * 将操作日志实体列表转换为 DTO 列表。
     *
     * @param entities 操作日志实体列表
     * @return 操作日志 DTO 列表
     */
    List<OperationLogDTO> toDTOList(List<OperationLogEntity> entities);
}
