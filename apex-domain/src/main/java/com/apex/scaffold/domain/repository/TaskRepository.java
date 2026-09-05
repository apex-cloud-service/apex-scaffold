package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.workflow.Task;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 任务仓储接口。
 *
 * <p>定义任务的持久化操作契约，由基础设施层实现。
 *
 * <h2>查询方法</h2>
 * <ul>
 *   <li>{@link #findById(Long)}：根据ID查询任务</li>
 *   <li>{@link #findByProcessInstanceId(Long)}：查询流程实例下的所有任务</li>
 *   <li>{@link #findByAssignee(Long)}：查询指定审批人的待办任务</li>
 *   <li>{@link #findPendingTasks()}：查询所有待办任务（用于超时扫描）</li>
 * </ul>
 *
 * @author Apex Team
 * @version 1.3.0
 * @since 1.0.0
 */
public interface TaskRepository {

    Mono<Task> findById(Long id);

    Mono<List<Task>> findByProcessInstanceId(Long processInstanceId);

    Mono<List<Task>> findByAssignee(Long assigneeId);

    /**
     * 查询所有待办任务。
     *
     * <p>返回所有 status='PENDING' 的任务，主要用于超时任务扫描。
     * 使用 {@link Flux} 返回值支持流式处理大量数据。
     *
     * @return 待办任务流
     */
    Flux<Task> findPendingTasks();

    Mono<Integer> insert(Task entity);

    Mono<Integer> updateById(Task entity);

    Mono<Integer> deleteById(Long id);
}
