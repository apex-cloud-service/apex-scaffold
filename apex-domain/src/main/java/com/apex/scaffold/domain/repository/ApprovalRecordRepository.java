package com.apex.scaffold.domain.repository;

import com.apex.scaffold.domain.model.workflow.ApprovalRecord;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 审批记录仓储接口。
 *
 * <p>定义审批记录的持久化操作契约，由基础设施层实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 */
public interface ApprovalRecordRepository {

    /**
     * 根据主键ID查询审批记录。
     *
     * @param id 审批记录ID
     * @return 审批记录实体
     */
    Mono<ApprovalRecord> findById(Long id);

    /**
     * 根据流程实例ID查询审批记录列表。
     *
     * @param processInstanceId 流程实例ID
     * @return 审批记录列表
     */
    Mono<List<ApprovalRecord>> findByProcessInstanceId(Long processInstanceId);

    /**
     * 根据任务ID查询审批记录列表。
     *
     * @param taskId 任务ID
     * @return 审批记录列表
     */
    Mono<List<ApprovalRecord>> findByTaskId(Long taskId);

    /**
     * 插入审批记录。
     *
     * @param entity 审批记录实体
     * @return 影响的行数
     */
    Mono<Integer> insert(ApprovalRecord entity);

    /**
     * 根据ID更新审批记录。
     *
     * @param entity 审批记录实体
     * @return 影响的行数
     */
    Mono<Integer> updateById(ApprovalRecord entity);

    /**
     * 根据ID删除审批记录。
     *
     * @param id 审批记录ID
     * @return 影响的行数
     */
    Mono<Integer> deleteById(Long id);
}
