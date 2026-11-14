package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.ApiCallLogDTO;
import com.apex.scaffold.domain.model.entity.ApiCallLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * API调用日志对象转换器
 *
 * <p>用于在API调用日志相关的 DTO 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ApiCallLogAssembler {
    /**
     * 将 ApiCallLogEntity 转换为 ApiCallLogDTO
     *
     * @param entity API调用日志实体
     * @return API调用日志 DTO
     */
    ApiCallLogDTO toDTO(ApiCallLogEntity entity);

    /**
     * 将 ApiCallLogEntity 列表转换为 ApiCallLogDTO 列表
     *
     * @param entities API调用日志实体列表
     * @return API调用日志 DTO 列表
     */
    List<ApiCallLogDTO> toDTOList(List<ApiCallLogEntity> entities);
}
