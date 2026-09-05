package com.apex.scaffold.domain.workflow.repository;

import com.apex.scaffold.domain.workflow.TaskEntity;
import com.apex.scaffold.domain.workflow.TaskStatus;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 任务仓储接口。
 *
 * <p>定义工作流任务的持久化操作契约，支持按流程实例、审批人、状态等维度查询任务。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface TaskRepository {

    Mono<TaskEntity> findById(Long id);

    Mono<List<TaskEntity>> findByProcessInstanceId(Long processInstanceId);

    Mono<List<TaskEntity>> findByAssigneeId(Long assigneeId);

    Mono<List<TaskEntity>> findPendingByAssigneeId(Long assigneeId);

    Mono<List<TaskEntity>> findByAssigneeIdAndStatus(Long assigneeId, TaskStatus status);

    Mono<Integer> insert(TaskEntity entity);

    Mono<Integer> updateById(TaskEntity entity);

    Mono<Integer> deleteById(Long id);
}
