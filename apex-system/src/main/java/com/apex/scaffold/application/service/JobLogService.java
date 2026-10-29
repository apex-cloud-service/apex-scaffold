package com.apex.scaffold.application.service;

import com.apex.scaffold.application.assembler.JobLogAssembler;
import com.apex.scaffold.application.dto.admin.JobLogDTO;
import com.apex.scaffold.domain.model.entity.JobLogEntity;
import com.apex.scaffold.domain.repository.JobLogRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * 定时任务执行日志应用服务。
 *
 * <p>提供任务执行日志的记录与回溯查询能力。日志由调度器在每次任务
 * 执行完成后写入，管理界面按任务 ID 回溯最近执行历史。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLogService {

    /**
     * 执行日志仓储。
     */
    private final JobLogRepository jobLogRepository;

    /**
     * 执行日志对象转换器。
     */
    private final JobLogAssembler jobLogAssembler;

    /**
     * 记录一条执行日志。
     *
     * @param entity 执行日志实体
     */
    public Mono<Void> record(JobLogEntity entity) {
        return jobLogRepository
                .insert(entity)
                .doOnNext(rows -> log.debug("任务执行日志已记录，jobId: {}, status: {}", entity.getJobId(), entity.getStatus()))
                .then();
    }

    /**
     * 查询指定任务的最近执行日志。
     *
     * @param jobId 任务 ID
     * @param limit 返回条数上限，限制在 1-100
     * @return 执行日志 DTO 列表
     */
    public Mono<List<JobLogDTO>> findLatestByJobId(Long jobId, int limit) {
        int bounded = Math.min(Math.max(limit, 1), 100);
        return jobLogRepository.findLatestByJobId(jobId, bounded).map(jobLogAssembler::toDTOList);
    }
}
