package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.JobLogDTO;
import com.apex.scaffold.domain.model.entity.JobLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 定时任务执行日志对象转换器。
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface JobLogAssembler {

    /**
     * 实体转换为 DTO。
     *
     * @param entity 执行日志实体
     * @return 执行日志 DTO
     */
    JobLogDTO toDTO(JobLogEntity entity);

    /**
     * 实体列表转换为 DTO 列表。
     *
     * @param entities 执行日志实体列表
     * @return 执行日志 DTO 列表
     */
    List<JobLogDTO> toDTOList(List<JobLogEntity> entities);
}
