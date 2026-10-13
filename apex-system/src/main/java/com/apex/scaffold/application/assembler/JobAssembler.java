package com.apex.scaffold.application.assembler;

import com.apex.scaffold.application.dto.admin.JobDTO;
import com.apex.scaffold.application.dto.admin.JobLogDTO;
import com.apex.scaffold.application.dto.admin.JobRequest;
import com.apex.scaffold.domain.model.entity.JobEntity;
import com.apex.scaffold.domain.model.entity.JobLogEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

/**
 * 定时任务对象转换器
 *
 * <p>用于在定时任务相关的 DTO/Request 与领域实体之间进行转换。
 * 基于 MapStruct 生成实现类。
 *
 * @author Apex Team
 * @version 2.0.0
 * @since 1.0.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface JobAssembler {
    /**
     * 将 JobEntity 转换为 JobDTO
     *
     * @param entity 任务实体
     * @return 任务 DTO
     */
    JobDTO toDTO(JobEntity entity);

    /**
     * 将 JobRequest 转换为 JobEntity
     *
     * @param request 任务请求
     * @return 任务实体
     */
    JobEntity fromRequest(JobRequest request);

    /**
     * 将 JobEntity 列表转换为 JobDTO 列表
     *
     * @param entities 任务实体列表
     * @return 任务 DTO 列表
     */
    List<JobDTO> toDTOList(List<JobEntity> entities);

    /**
     * 将 JobLogEntity 转换为 JobLogDTO
     *
     * @param entity 任务日志实体
     * @return 任务日志 DTO
     */
    JobLogDTO toLogDTO(JobLogEntity entity);

    /**
     * 将 JobLogEntity 列表转换为 JobLogDTO 列表
     *
     * @param entities 任务日志实体列表
     * @return 任务日志 DTO 列表
     */
    List<JobLogDTO> toLogDTOList(List<JobLogEntity> entities);
}
