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

    /**
     * 根据主键ID查询任务。
     *
     * @param id 任务ID
     * @return 任务实体
     */
    Mono<TaskEntity> findById(Long id);

    /**
     * 根据流程实例ID查询任务列表。
     *
     * @param processInstanceId 流程实例ID
     * @return 任务列表
     */
    Mono<List<TaskEntity>> findByProcessInstanceId(Long processInstanceId);

    /**
     * 根据审批人ID查询任务列表。
     *
     * @param assigneeId 审批人ID
     * @return 任务列表
     */
    Mono<List<TaskEntity>> findByAssigneeId(Long assigneeId);

    /**
     * 根据审批人ID查询待处理任务列表。
     *
     * @param assigneeId 审批人ID
     * @return 待处理任务列表
     */
    Mono<List<TaskEntity>> findPendingByAssigneeId(Long assigneeId);

    /**
     * 根据审批人ID和任务状态查询任务列表。
     *
     * @param assigneeId 审批人ID
     * @param status 任务状态
     * @return 任务列表
     */
    Mono<List<TaskEntity>> findByAssigneeIdAndStatus(Long assigneeId, TaskStatus status);

    /**
     * 插入任务。
     *
     * @param entity 任务实体
     * @return 影响的行数
     */
    Mono<Integer> insert(TaskEntity entity);

    /**
     * 根据ID更新任务。
     *
     * @param entity 任务实体
     * @return 影响的行数
     */
    Mono<Integer> updateById(TaskEntity entity);

    /**
     * 根据ID删除任务。
     *
     * @param id 任务ID
     * @return 影响的行数
     */
    Mono<Integer> deleteById(Long id);
}
