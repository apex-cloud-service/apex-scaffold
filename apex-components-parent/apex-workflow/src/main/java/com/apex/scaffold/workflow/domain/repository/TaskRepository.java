package com.apex.scaffold.workflow.domain.repository;

import com.apex.scaffold.workflow.domain.model.Task;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 流程任务仓储接口。
 *
 * <p>定义流程任务（{@link Task}）的持久化访问契约，包括按主键、流程实例、待办人
 * 查询，待办任务流式查询，以及新增、更新、删除操作。该接口由基础设施层的
 * {@code TaskRepositoryImpl} 提供 MyBatis-Plus 实现。</p>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 * @see com.apex.scaffold.workflow.domain.model.Task
 */
public interface TaskRepository {

    /**
     * 按主键 ID 查询任务。
     *
     * @param id 任务主键 ID
     * @return 返回包含任务的 {@link Mono}；不存在时为空
     */
    Mono<Task> findById(Long id);

    /**
     * 按流程实例查询任务列表。
     *
     * @param processInstanceId 流程实例 ID
     * @return 返回包含任务列表的 {@link Mono}；无数据时为空列表
     */
    Mono<List<Task>> findByProcessInstanceId(Long processInstanceId);

    /**
     * 按待办人查询任务列表。
     *
     * @param assigneeId 待办人用户 ID
     * @return 返回包含任务列表的 {@link Mono}；无数据时为空列表
     */
    Mono<List<Task>> findByAssignee(Long assigneeId);

    /**
     * 查询当前可信 Reactor 租户内配置了超时时间的待处理任务；后台跨租户扫描使用独立的 TimeoutTaskScan。
     *
     * <p>仅查询状态为 {@code PENDING} 且 {@code timeoutAt} 非空的任务，按超时时间升序
     * 以 {@link Flux} 输出；不在查询中筛选是否已经超时。</p>
     *
     * @return 配置了超时时间的待处理任务流 {@link Flux}
     */
    Flux<Task> findPendingTasks();

    /**
     * 新增一条任务。
     *
     * <p>当前实现不会将持久化对象的主键回填到传入的领域对象。</p>
     *
     * @param task 待新增的任务
     * @return 返回带影响行数的 {@link Mono}
     */
    Mono<Integer> insert(Task task);

    /**
     * 在可信租户内，按主键、PENDING 状态与读取版本条件更新任务并递增版本。
     * 缺失版本或条件不匹配时返回零，调用方必须拒绝继续办理。
     *
     * @param task 待更新的任务，主键与读取版本不能为空
     * @return 返回带影响行数的 {@link Mono}
     */
    Mono<Integer> updateById(Task task);

    /**
     * 按主键删除任务。
     *
     * <p>采用逻辑删除。</p>
     *
     * @param id 任务主键 ID
     * @return 返回带影响行数的 {@link Mono}
     */
    Mono<Integer> deleteById(Long id);
}
