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
     * 查询全部待处理任务（流式）。
     *
     * <p>返回状态为 {@code PENDING} 的任务，以 {@link Flux} 流式输出，便于大数据量
     * 场景下逐条消费。</p>
     *
     * @return 待处理任务流 {@link Flux}
     */
    Flux<Task> findPendingTasks();

    /**
     * 新增一条任务。
     *
     * @param task 待新增的任务
     * @return 返回携带自增主键的回填结果 {@link Mono}
     */
    Mono<Integer> insert(Task task);

    /**
     * 按主键更新任务。
     *
     * @param task 待更新的任务，主键不能为空
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