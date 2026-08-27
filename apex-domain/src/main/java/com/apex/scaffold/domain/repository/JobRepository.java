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
