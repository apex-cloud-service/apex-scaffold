package com.apex.scaffold.workflow.domain.repository;

import com.apex.scaffold.workflow.domain.model.ApprovalRecord;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * 审批记录仓储接口。
 *
 * <p>定义审批记录（{@link ApprovalRecord}）的持久化访问契约，包括按主键、流程实例、
 * 任务查询，以及新增、更新、删除操作。该接口由基础设施层的
 * {@code ApprovalRecordRepositoryImpl} 提供 MyBatis-Plus 实现。</p>
 *
 * @author Apex Team
 * @version 1.0.0
 * @since 1.0.0
 * @see com.apex.scaffold.workflow.domain.model.ApprovalRecord
 */
public interface ApprovalRecordRepository {

    /**
     * 按主键 ID 查询审批记录。
     *
     * @param id 审批记录主键 ID
     * @return 返回包含审批记录的 {@link Mono}；不存在时为空
     */
    Mono<ApprovalRecord> findById(Long id);

    /**
     * 按流程实例查询审批记录列表。
     *
     * @param processInstanceId 流程实例 ID
     * @return 返回包含审批记录列表的 {@link Mono}；无数据时为空列表
     */
    Mono<List<ApprovalRecord>> findByProcessInstanceId(Long processInstanceId);

    /**
     * 按任务查询审批记录列表。
     *
     * @param taskId 任务 ID
     * @return 返回包含审批记录列表的 {@link Mono}；无数据时为空列表
     */
    Mono<List<ApprovalRecord>> findByTaskId(Long taskId);

    /**
     * 新增一条审批记录。
     *
     * <p>当前实现不会将持久化对象的主键回填到传入的领域对象。</p>
     *
     * @param record 待新增的审批记录
     * @return 返回带影响行数的 {@link Mono}
     */
    Mono<Integer> insert(ApprovalRecord record);

    /**
     * 按主键更新审批记录。
     *
     * @param record 待更新的审批记录，主键不能为空
     * @return 返回带影响行数的 {@link Mono}
     */
    Mono<Integer> updateById(ApprovalRecord record);

    /**
     * 按主键删除审批记录。
     *
     * <p>采用逻辑删除。</p>
     *
     * @param id 审批记录主键 ID
     * @return 返回带影响行数的 {@link Mono}
     */
    Mono<Integer> deleteById(Long id);
}
