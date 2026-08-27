package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.entity.JobLogEntity;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 定时任务执行日志仓储接口。
 *
 * <p>定义任务执行日志的持久化读写操作，由基础设施层提供 MyBatis-Plus 实现。
 * 执行日志按任务 ID 关联，支持按任务分页回溯执行历史。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface JobLogRepository {

    /**
     * 新增执行日志。
     *
     * @param entity 执行日志实体
     * @return 影响行数
     */
    Mono<Integer> insert(JobLogEntity entity);

    /**
     * 查询指定任务的最近执行日志。
     *
     * @param jobId 任务 ID
     * @param limit 返回条数上限
     * @return 执行日志列表，按开始时间倒序
     */
    Mono<List<JobLogEntity>> findLatestByJobId(Long jobId, int limit);
}
