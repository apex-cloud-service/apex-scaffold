package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.JobLogEntity;
import com.apex.scaffold.domain.repository.JobLogRepository;
import com.apex.scaffold.infrastructure.persistence.converter.JobLogPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.JobLogMapper;
import com.apex.scaffold.infrastructure.persistence.po.JobLogPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link JobLogRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link JobLogEntity} 的持久化读写与按任务回溯查询，通过
 * {@link JobLogPoConverter} 在领域实体与 {@link JobLogPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see JobLogRepository
 */
@Repository
@RequiredArgsConstructor
public class JobLogRepositoryImpl implements JobLogRepository {

    /**
     * 执行日志 Mapper。
     */
    private final JobLogMapper jobLogMapper;

    /**
     * 执行日志实体与持久化对象转换器。
     */
    private final JobLogPoConverter jobLogPoConverter;

    /**
     * 新增执行日志。
     *
     * @param entity 执行日志实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(JobLogEntity entity) {
        return Mono.fromCallable(() -> jobLogMapper.insert(jobLogPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 查询指定任务的最近执行日志。
     *
     * @param jobId 任务 ID
     * @param limit 返回条数上限
     * @return 执行日志列表，按开始时间倒序
     */
    @Override
    public Mono<List<JobLogEntity>> findLatestByJobId(Long jobId, int limit) {
        return Mono.fromCallable(() -> jobLogMapper.selectList(ConditionUtil.lambda(JobLogPO.class)
                        .eq(JobLogPO::getJobId, jobId)
                        .orderByDesc(JobLogPO::getStartTime)
                        .last("LIMIT " + limit)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(jobLogPoConverter::toEntityList);
    }
}
