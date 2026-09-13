package com.apex.scaffold.infrastructure.persistence.repository;

import com.apex.scaffold.common.scheduler.BlockingIoSchedulers;
import com.apex.scaffold.domain.model.entity.JobEntity;
import com.apex.scaffold.domain.repository.JobRepository;
import com.apex.scaffold.infrastructure.persistence.converter.JobPoConverter;
import com.apex.scaffold.infrastructure.persistence.mapper.JobMapper;
import com.apex.scaffold.infrastructure.persistence.po.JobPO;
import com.apex.scaffold.infrastructure.persistence.util.ConditionUtil;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * {@link JobRepository} 的 MyBatis-Plus 实现（基础设施层）。
 *
 * <p>负责 {@link JobEntity} 的持久化读写与启用任务恢复查询，通过
 * {@link JobPoConverter} 在领域实体与 {@link JobPO} 之间转换，
 * 阻塞操作调度到 JDBC 专用线程池执行。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see JobRepository
 */
@Repository
@RequiredArgsConstructor
public class JobRepositoryImpl implements JobRepository {

    /**
     * 定时任务 Mapper。
     */
    private final JobMapper jobMapper;

    /**
     * 任务实体与持久化对象转换器。
     */
    private final JobPoConverter jobPoConverter;

    /**
     * 根据 ID 查询任务。
     *
     * @param id 任务 ID
     * @return 任务实体，未找到时返回 {@link Mono#empty()}
     */
    @Override
    public Mono<JobEntity> findById(Long id) {
        return Mono.fromCallable(() -> jobMapper.selectById(id))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .flatMap(po -> po == null ? Mono.empty() : Mono.just(jobPoConverter.toEntity(po)));
    }

    /**
     * 查询全部任务。
     *
     * @return 任务实体列表
     */
    @Override
    public Mono<List<JobEntity>> findAll() {
        return Mono.fromCallable(() ->
                        jobMapper.selectList(ConditionUtil.lambda(JobPO.class).orderByDesc(JobPO::getCreateTime)))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(jobPoConverter::toEntityList);
    }

    /**
     * 查询全部启用状态的任务。
     *
     * @return 启用状态的任务列表
     */
    @Override
    public Mono<List<JobEntity>> findEnabled() {
        return Mono.fromCallable(() -> jobMapper.selectList(
                        ConditionUtil.lambda(JobPO.class).eq(JobPO::getStatus, JobEntity.JobState.ENABLED.getCode())))
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(jobPoConverter::toEntityList);
    }

    /**
     * 新增任务。
     *
     * @param entity 任务实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> insert(JobEntity entity) {
        return Mono.fromCallable(() -> jobMapper.insert(jobPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 更新任务。
     *
     * @param entity 任务实体
     * @return 影响行数
     */
    @Override
    public Mono<Integer> updateById(JobEntity entity) {
        return Mono.fromCallable(() -> jobMapper.updateById(jobPoConverter.toPo(entity)))
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 删除任务。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id 任务 ID
     * @return 影响行数
     */
    @Override
    public Mono<Integer> deleteById(Long id) {
        return Mono.fromCallable(() -> jobMapper.deleteById(id)).subscribeOn(BlockingIoSchedulers.jdbc());
    }

    /**
     * 分页查询任务列表（支持条件筛选）。
     *
     * <p>根据任务名称、任务分组、状态等条件进行分页查询，支持模糊匹配和精确过滤。
     * 查询结果按创建时间降序排列。</p>
     *
     * @param jobName 任务名称（模糊匹配），可为 null
     * @param jobGroup 任务分组（精确匹配），可为 null
     * @param status 任务状态（精确匹配），可为 null
     * @param offset 分页偏移量
     * @param pageSize 每页大小
     * @return 任务实体列表
     */
    @Override
    public Mono<List<JobEntity>> findByPage(
            String jobName, String jobGroup, Integer status, Integer offset, Integer pageSize) {
        return Mono.fromCallable(() -> {
                    var wrapper = ConditionUtil.lambda(JobPO.class);
                    if (jobName != null && !jobName.isBlank()) {
                        wrapper.like(JobPO::getJobName, jobName);
                    }
                    if (jobGroup != null && !jobGroup.isBlank()) {
                        wrapper.eq(JobPO::getJobGroup, jobGroup);
                    }
                    if (status != null) {
                        wrapper.eq(JobPO::getStatus, status);
                    }
                    wrapper.orderByDesc(JobPO::getCreateTime).last("LIMIT " + offset + ", " + pageSize);
                    return jobMapper.selectList(wrapper);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc())
                .map(jobPoConverter::toEntityList);
    }

    /**
     * 统计任务总数（支持条件筛选）。
     *
     * <p>统计符合条件的任务总数，用于分页查询的总记录数计算。</p>
     *
     * @param jobName 任务名称（模糊匹配），可为 null
     * @param jobGroup 任务分组（精确匹配），可为 null
     * @param status 任务状态（精确匹配），可为 null
     * @return 符合条件的任务总数
     */
    @Override
    public Mono<Long> countByCondition(String jobName, String jobGroup, Integer status) {
        return Mono.fromCallable(() -> {
                    var wrapper = ConditionUtil.lambda(JobPO.class);
                    if (jobName != null && !jobName.isBlank()) {
                        wrapper.like(JobPO::getJobName, jobName);
                    }
                    if (jobGroup != null && !jobGroup.isBlank()) {
                        wrapper.eq(JobPO::getJobGroup, jobGroup);
                    }
                    if (status != null) {
                        wrapper.eq(JobPO::getStatus, status);
                    }
                    return jobMapper.selectCount(wrapper);
                })
                .subscribeOn(BlockingIoSchedulers.jdbc());
    }
}
