package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.JobEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 定时任务仓储接口。
 *
 * <p>定义定时任务定义的持久化读写操作，由基础设施层提供 MyBatis-Plus 实现。
 * 应用启动时通过 {@link #findEnabled()} 恢复全部启用状态的调度任务。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface JobRepository {

    /**
     * 根据 ID 查询任务。
     *
     * @param id 任务 ID
     * @return 任务实体，未找到时返回 {@link Mono#empty()}
     */
    Mono<JobEntity> findById(Long id);

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
    Mono<List<JobEntity>> findByPage(String jobName, String jobGroup, Integer status, Integer offset, Integer pageSize);

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
    Mono<Long> countByCondition(String jobName, String jobGroup, Integer status);

    /**
     * 查询全部任务。
     *
     * @return 任务实体列表
     */
    Mono<List<JobEntity>> findAll();

    /**
     * 查询全部启用状态的任务。
     *
     * <p>用于应用启动时恢复调度，只返回状态为正常的任务。</p>
     *
     * @return 启用状态的任务列表
     */
    Mono<List<JobEntity>> findEnabled();

    /**
     * 新增任务。
     *
     * @param entity 任务实体
     * @return 影响行数
     */
    Mono<Integer> insert(JobEntity entity);

    /**
     * 更新任务。
     *
     * @param entity 任务实体
     * @return 影响行数
     */
    Mono<Integer> updateById(JobEntity entity);

    /**
     * 删除任务。
     *
     * <p>基于 {@code deleted} 字段的逻辑删除。</p>
     *
     * @param id 任务 ID
     * @return 影响行数
     */
    Mono<Integer> deleteById(Long id);
}
